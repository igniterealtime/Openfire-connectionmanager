/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.multiplexer;

import com.jcraft.jzlib.JZlib;
import com.jcraft.jzlib.ZInputStream;
import org.dom4j.Element;
import org.dom4j.io.XMPPPacketReader;
import org.jivesoftware.multiplexer.net.DNSUtil;
import org.jivesoftware.multiplexer.net.MXParser;
import org.jivesoftware.multiplexer.net.SocketConnection;
import org.jivesoftware.multiplexer.spi.ServerFailoverDeliverer;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.StringUtils;
import org.xmlpull.v1.XmlPullParser;

import javax.net.ssl.SSLHandshakeException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.Random;

/**
 * Thread that creates and keeps a connection to the server. This thread is responsable
 * for actually forwarding clients traffic to the server. If the connection is no longer
 * active then the thread is going to be discarded and a new one is created and added to
 * the thread pool that is kept in {@link ServerSurrogate}.
 *
 * @author Gaston Dombiak
 */
public class ConnectionWorkerThread extends Thread {

    /**
     * The utf-8 charset for decoding and encoding Jabber packet streams.
     */
    private static String CHARSET = "UTF-8";
    /**
     * The default XMPP port for connection multiplex.
     */
    public static final int DEFAULT_MULTIPLEX_PORT = 5262;

    // Sequence and random number generator used for creating unique IQ ID's.
    private static int sequence = 0;
    private static Random random = new Random();
    private static ConnectionCloseListener connectionListener;

    private String serverName;
    private String managerName;

    /**
     * JID that identifies this connection to the server. The address is composed by
     * the connection manager name and the name of the thread. e.g.: connManager1/thread1
     */
    private String jidAddress;
    /**
     * Connection to the server.
     */
    private SocketConnection connection;
    /**
     * Store the last received stream features from the server
     */
    private Element features;

    static {
        connectionListener = new ConnectionCloseListener() {
            public void onConnectionClose(Object handback) {
                ConnectionWorkerThread thread = (ConnectionWorkerThread) handback;
                thread.interrupt();
            }
        };
    }

    public ConnectionWorkerThread(ThreadGroup group, Runnable target, String name, long stackSize) {
        super(group, target, name, stackSize);
        ConnectionManager connectionManager = ConnectionManager.getInstance();
        this.serverName = connectionManager.getServerName();
        this.managerName = connectionManager.getName();
        // Create connection to the server
        createConnection();
        // Clean up features variable that is no longer needed
        features = null;
    }

    /**
     * Returns true if there is a connection to the server that is still active. Note
     * that sometimes a socket assumes to be opened when in fact the underlying TCP
     * socket connection is closed. To detect these cases we rely on heartbeats or
     * timing out when writing data hasn't finished for a while.
     *
     * @return rue if there is a connection to the server that is still active.
     */
    public boolean isValid() {
        return connection != null && !connection.isClosed();
    }

    /**
     * Returns the connection to the server.
     *
     * @return the connection to the server.
     */
    public SocketConnection getConnection() {
        return connection;
    }

    /**
     * Creates a new connection to the server
     */
    private boolean createConnection() {
        String realHostname = null;
        int port =
                JiveGlobals.getIntProperty("xmpp.port", DEFAULT_MULTIPLEX_PORT);
        Socket socket = new Socket();
        try {
            // Get the real hostname to connect to using DNS lookup of the specified hostname
            DNSUtil.HostAddress address = DNSUtil.resolveXMPPServerDomain(serverName, port);
            realHostname = address.getHost();
            Log.debug("CM - Trying to connect to " + serverName + ":" + port +
                    "(DNS lookup: " + realHostname + ":" + port + ")");
            // Establish a TCP connection to the Receiving Server
            socket.connect(new InetSocketAddress(realHostname, port), 20000);
            Log.debug("CM - Plain connection to " + serverName + ":" + port + " successful");
        }
        catch (Exception e) {
            Log.error("Error trying to connect to server: " + serverName +
                    "(DNS lookup: " + realHostname + ":" + port + ")", e);
            return false;
        }

        try {
            connection = new SocketConnection(new ServerFailoverDeliverer(), socket, false);

            jidAddress = managerName + "/" + getName();

            // Send the stream header
            StringBuilder openingStream = new StringBuilder();
            openingStream.append("<stream:stream");
            openingStream.append(" xmlns:stream=\"http://etherx.jabber.org/streams\"");
            openingStream.append(" xmlns=\"jabber:connectionmanager\"");
            openingStream.append(" to=\"").append(jidAddress).append("\"");
            openingStream.append(" version=\"1.0\">");
            connection.deliverRawText(openingStream.toString());

            // Set a read timeout (of 5 seconds) so we don't keep waiting forever
            int soTimeout = socket.getSoTimeout();
            socket.setSoTimeout(7000);

            XMPPPacketReader reader = new XMPPPacketReader();
            reader.getXPPParser().setInput(new InputStreamReader(socket.getInputStream(),
                    CHARSET));
            // Get the answer from the Receiving Server
            XmlPullParser xpp = reader.getXPPParser();
            for (int eventType = xpp.getEventType(); eventType != XmlPullParser.START_TAG;) {
                eventType = xpp.next();
            }

            String id = xpp.getAttributeValue("", "id");
            String serverVersion = xpp.getAttributeValue("", "version");

            // Check if the remote server is XMPP 1.0 compliant
            if (serverVersion != null && decodeVersion(serverVersion)[0] >= 1) {
                // Get the stream features
                features = reader.parseDocument().getRootElement();
                // Check if there was an error
                if (features != null && "error".equals(features.getName())) {
                    Log.debug("CM - Error while opening stream: " + features.asXML());
                    // Failed to secure the connection
                    connection = null;
                    return false;
                }
                // Check if TLS is enabled
                if (features != null && features.element("starttls") != null) {
                    // Try to secure the connection since the server supports TLS
                    if (!secureConnection(reader, openingStream)) {
                        // Failed to secure the connection
                        connection = null;
                        return false;
                    }
                }
                if (features != null && features.element("compression") != null) {
                    // Try to use stream compression since the server supports it
                    if (!compressConnection(reader, openingStream)) {
                        // Failed to use stream compression (when enabled locally)
                        connection = null;
                        return false;
                    }
                }
                if (!doHandshake(id, reader)) {
                    // Failed to authenticate with the server
                    connection = null;
                    return false;
                }
                // Add connection listener
                connection.registerCloseListener(connectionListener, this);
                // Set idle time out (server needs to send heartbeats or traffic). Default 5 minutes
                connection.setIdleTimeout(5 * 60 * 1000);
                // Create reader that will process packets sent from the server.
                createSocketReader(reader);
                // Restore default timeout
                socket.setSoTimeout(soTimeout);
                return true;
            }
            Log.debug("CM - Server does not support XMPP version 1.0 or later");
        }
        catch (SSLHandshakeException e) {
            Log.warn("Handshake error while connecting to server: " + serverName +
                    "(DNS lookup: " + realHostname + ":" + port + ")", e);
        }
        catch (Exception e) {
            Log.error("Error while connecting to server: " + serverName + "(DNS lookup: " +
                    realHostname + ":" + port + ")", e);
        }
        // Close the connection
        if (connection != null) {
            connection.close();
            connection = null;
        }
        return false;
    }

    private boolean secureConnection(XMPPPacketReader reader, StringBuilder openingStream)
            throws Exception {
        Log.debug("CM - Indicating we want TLS to " + serverName);
        connection.deliverRawText("<starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>");

        MXParser xpp = reader.getXPPParser();
        // Wait for the <proceed> response
        Element proceed = reader.parseDocument().getRootElement();
        if (proceed != null && proceed.getName().equals("proceed")) {
            Log.debug("CM - Negotiating TLS with " + serverName);
            connection.startTLS(true, serverName);
            Log.debug("CM - TLS negotiation with " + serverName + " was successful");

            // TLS negotiation was successful so initiate a new stream
            connection.deliverRawText(openingStream.toString());

            // Reset the parser to use the new secured reader
            xpp.setInput(
                    new InputStreamReader(connection.getTLSStreamHandler().getInputStream(),
                            CHARSET));
            // Skip new stream element
            for (int eventType = xpp.getEventType(); eventType != XmlPullParser.START_TAG;) {
                eventType = xpp.next();
            }
            // Get new stream features
            features = reader.parseDocument().getRootElement();
            return true;
        } else {
            Log.debug("CM - Error, <proceed> was not received");
        }
        return false;
    }

    private boolean compressConnection(XMPPPacketReader reader, StringBuilder openingStream)
            throws Exception {
        // Check if we can use stream compression
        String policyName = JiveGlobals.getXMLProperty("xmpp.server.compression.policy",
                Connection.CompressionPolicy.disabled.toString());
        Connection.CompressionPolicy compressionPolicy =
                Connection.CompressionPolicy.valueOf(policyName);
        // Check if stream compression is enabled in the Connection Manager
        if (Connection.CompressionPolicy.optional == compressionPolicy) {
            Element compression = features.element("compression");
            boolean zlibSupported = false;
            Iterator it = compression.elementIterator("method");
            while (it.hasNext()) {
                Element method = (Element) it.next();
                if ("zlib".equals(method.getTextTrim())) {
                    zlibSupported = true;
                }
            }
            if (zlibSupported) {
                MXParser xpp = reader.getXPPParser();
                // Request Stream Compression
                connection.deliverRawText(
                        "<compress xmlns='http://jabber.org/protocol/compress'><method>zlib</method></compress>");
                // Check if we are good to start compression
                Element answer = reader.parseDocument().getRootElement();
                if ("compressed".equals(answer.getName())) {
                    // Server confirmed that we can use zlib compression
                    connection.startCompression();
                    Log.debug("CM - Stream compression was successful with " + serverName);
                    // Stream compression was successful so initiate a new stream
                    connection.deliverRawText(openingStream.toString());
                    // Reset the parser to use stream compression over TLS
                    ZInputStream in =
                            new ZInputStream(connection.getTLSStreamHandler().getInputStream());
                    in.setFlushMode(JZlib.Z_PARTIAL_FLUSH);
                    xpp.setInput(new InputStreamReader(in, CHARSET));
                    // Skip the opening stream sent by the server
                    for (int eventType = xpp.getEventType();
                         eventType != XmlPullParser.START_TAG;) {
                        eventType = xpp.next();
                    }
                    // Get new stream features
                    features = reader.parseDocument().getRootElement();
                    return true;
                } else {
                    Log.debug("CM - Stream compression was rejected by " + serverName);
                }
            } else {
                Log.debug(
                        "CM - Stream compression found but zlib method is not supported by" +
                                serverName);
            }
            return false;
        }
        return true;
    }

    private boolean doHandshake(String streamID, XMPPPacketReader reader) throws Exception {
        String password = JiveGlobals.getXMLProperty("xmpp.password");
        if (password == null) {
            // No password was configued in the connection manager
            Log.debug("CM - No password was found. Configure xmpp.password property");
            return false;
        }
        MessageDigest digest;
        // Create a message digest instance.
        try {
            digest = MessageDigest.getInstance("SHA");
        }
        catch (NoSuchAlgorithmException e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            return false;
        }

        digest.update(streamID.getBytes());
        String key = StringUtils.encodeHex(digest.digest(password.getBytes()));

        Log.debug("OS - Sent handshake to host: " + serverName + " id: " + streamID);

        // Send handshake to server
        StringBuilder sb = new StringBuilder();
        sb.append("<handshake>").append(key).append("</handshake>");
        connection.deliverRawText(sb.toString());

        // Wait for the <handshake> response
        Element proceed = reader.parseDocument().getRootElement();
        if (proceed != null && proceed.getName().equals("handshake")) {
            Log.debug("OS - Handshake was SUCCESSFUL with host: " + serverName + " id: " +
                    streamID);
            return true;
        }
        Log.debug("OS - Handshake FAILED with host: " + serverName + " id: " + streamID);
        return false;
    }

    private int[] decodeVersion(String version) {
        int[] answer = new int[]{0, 0};
        String [] versionString = version.split("\\.");
        answer[0] = Integer.parseInt(versionString[0]);
        answer[1] = Integer.parseInt(versionString[1]);
        return answer;
    }

    /**
     * Creates a reader that will process incoming packets from the server. Incoming
     * stanzas will be handled by {@link ServerPacketHandler} through a pool of
     * threads.
     *
     * @param reader the reader to use to retrieve stanzas.
     */
    private void createSocketReader(XMPPPacketReader reader) {
        ServerPacketReader serverPacketReader =
                new ServerPacketReader(reader, connection, jidAddress);
        connection.setSocketStatistic(serverPacketReader);
    }

    /**
     * Sends a notification to the main server that a new client session has been created.
     *
     * @param streamID the stream ID assigned by the connection manager to the new session.
     */
    public void clientSessionCreated(String streamID) {
        StringBuilder sb = new StringBuilder(100);
        sb.append("<iq type='set' to='").append(serverName);
        sb.append("' from='").append(jidAddress);
        sb.append("' id='").append(String.valueOf(random.nextInt(1000) + "-" + sequence++));
        sb.append("'><session xmlns='http://jabber.org/protocol/connectionmanager' id='").append(streamID);
        sb.append("'><create/></session></iq>");
        // Forward the notification to the server
        connection.deliver(sb.toString());
    }

    /**
     * Sends a notification to the main server that a client session has been closed.
     *
     * @param streamID the stream ID assigned by the connection manager to the closed session.
     */
    public void clientSessionClosed(String streamID) {
        StringBuilder sb = new StringBuilder(100);
        sb.append("<iq type='set' to='").append(serverName);
        sb.append("' from='").append(jidAddress);
        sb.append("' id='").append(String.valueOf(random.nextInt(1000) + "-" + sequence++));
        sb.append("'><session xmlns='http://jabber.org/protocol/connectionmanager' id='").append(streamID);
        sb.append("'><close/></session></iq>");
        // Forward the notification to the server
        connection.deliver(sb.toString());
    }

    /**
     * Sends notification to the main server that delivery of a stanza to a client has
     * failed.
     *
     * @param stanza the stanza that was not sent to the client.
     * @param streamID the stream ID assigned by the connection manager to the no
     *        longer available session.
     */
    public void deliveryFailed(Element stanza, String streamID) {
        StringBuilder sb = new StringBuilder(100);
        sb.append("<iq type='set' to='").append(serverName);
        sb.append("' from='").append(jidAddress);
        sb.append("' id='").append(String.valueOf(random.nextInt(1000) + "-" + sequence++));
        sb.append("'><session xmlns='http://jabber.org/protocol/connectionmanager' id='").append(streamID);
        sb.append("'><failed>").append(stanza.asXML()).append("</failed></session></iq>");
        // Send notification to the server
        connection.deliver(sb.toString());
    }

    public void run() {
        try {
            super.run();
        }
        catch(IllegalStateException e) {
            // Do not print this exception that was thrown to stop this thread when
            // it was detected that the connection was closed before using this thread
        }
        finally {
            // Remove this thread/connection from the list of available connections
            ConnectionManager.getInstance().getServerSurrogate().serverConnections.remove(getName());
            // Close the connection
            connection.close();
        }
    }

    /**
     * Indicates the server that the connection manager is being shut down.
     */
    void notifySystemShutdown() {
        connection.systemShutdown();
    }

    /**
     * Delivers clients traffic to the server. The client session that originated
     * the traffic is specified by the streamID attribute. Clients traffic is wrapped
     * by a <tt>route</tt> element.
     *
     * @param stanza the original client stanza that is going to be wrapped.
     * @param streamID the stream ID assigned by the connection manager to the client session.
     */
    public void deliver(String stanza, String streamID) {
        // Wrap the stanza
        StringBuilder sb = new StringBuilder(80);
        sb.append("<route ");
        sb.append("to='").append(serverName);
        sb.append("' from='").append(jidAddress);
        sb.append("' streamid='").append(streamID).append("'>");
        sb.append(stanza);
        sb.append("</route>");

        // Forward the wrapped stanza to the server
        connection.deliver(sb.toString());
    }
}
