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

import org.dom4j.Element;
import org.dom4j.io.XMPPPacketReader;
import org.jivesoftware.multiplexer.net.SocketConnection;
import org.jivesoftware.multiplexer.net.SocketReader;
import org.jivesoftware.multiplexer.spi.ClientFailoverDeliverer;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.JiveGlobals;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session that represents a client to server connection.
 *
 * @author Gaston Dombiak
 */
public class ClientSession extends Session {

    private static final String ETHERX_NAMESPACE = "http://etherx.jabber.org/streams";
    private static final String FLASH_NAMESPACE = "http://www.jabber.com/streams/flash";

    /**
     * Milliseconds a connection has to be idle to be closed. Default is 30 minutes. Sending
     * stanzas to the client is not considered as activity. We are only considering the connection
     * active when the client sends some data or hearbeats (i.e. whitespaces) to the server.
     * The reason for this is that sending data will fail if the connection is closed. And if
     * the thread is blocked while sending data (because the socket is closed) then the clean up
     * thread will close the socket anyway.
     */
    private static long idleTimeout;

    private static StreamIDFactory idFactory = new StreamIDFactory();

    /**
     * Map of existing sessions. A session is added just after the initial stream header
     * was processed. Key: stream ID, value: the session.
     */
    private static Map<String, ClientSession> sessions =
            new ConcurrentHashMap<String, ClientSession>();
    /**
     * Socket reader that is processing incoming packets from the client.
     */
    private SocketReader socketReader;

    static {
        // Set the default read idle timeout. If none was set then assume 30 minutes
        idleTimeout = JiveGlobals.getIntProperty("xmpp.client.idle", 30 * 60 * 1000);
    }

    public static Session createSession(String serverName, SocketReader socketReader,
                                        XMPPPacketReader reader, SocketConnection connection)
            throws XmlPullParserException {
        XmlPullParser xpp = reader.getXPPParser();

        boolean isFlashClient = xpp.getPrefix().equals("flash");
        connection.setFlashClient(isFlashClient);

        // Conduct error checking, the opening tag should be 'stream'
        // in the 'etherx' namespace
        if (!xpp.getName().equals("stream") && !isFlashClient) {
            throw new XmlPullParserException(
                    LocaleUtils.getLocalizedString("admin.error.bad-stream"));
        }

        if (!xpp.getNamespace(xpp.getPrefix()).equals(ETHERX_NAMESPACE) &&
                !(isFlashClient && xpp.getNamespace(xpp.getPrefix()).equals(FLASH_NAMESPACE)))
        {
            throw new XmlPullParserException(LocaleUtils.getLocalizedString(
                    "admin.error.bad-namespace"));
        }

        // TODO Check if IP address is allowed to connect to the server

        // Default language is English ("en").
        String language = "en";
        // Default to a version of "0.0". Clients written before the XMPP 1.0 spec may
        // not report a version in which case "0.0" should be assumed (per rfc3920
        // section 4.4.1).
        int majorVersion = 0;
        int minorVersion = 0;
        for (int i = 0; i < xpp.getAttributeCount(); i++) {
            if ("lang".equals(xpp.getAttributeName(i))) {
                language = xpp.getAttributeValue(i);
            }
            if ("version".equals(xpp.getAttributeName(i))) {
                try {
                    int[] version = decodeVersion(xpp.getAttributeValue(i));
                    majorVersion = version[0];
                    minorVersion = version[1];
                }
                catch (Exception e) {
                    Log.error(e);
                }
            }
        }

        // If the client supports a greater major version than the server,
        // set the version to the highest one the server supports.
        if (majorVersion > MAJOR_VERSION) {
            majorVersion = MAJOR_VERSION;
            minorVersion = MINOR_VERSION;
        }
        else if (majorVersion == MAJOR_VERSION) {
            // If the client supports a greater minor version than the
            // server, set the version to the highest one that the server
            // supports.
            if (minorVersion > MINOR_VERSION) {
                minorVersion = MINOR_VERSION;
            }
        }

        // Store language and version information in the connection.
        connection.setLanaguage(language);
        connection.setXMPPVersion(majorVersion, minorVersion);

        ServerSurrogate serverSurrogate = ConnectionManager.getInstance().getServerSurrogate();
        // Indicate the TLS policy to use for this connection
        connection.setTlsPolicy(serverSurrogate.getTlsPolicy());

        // Indicate the compression policy to use for this connection
        connection.setCompressionPolicy(serverSurrogate.getCompressionPolicy());

        // Set the max number of milliseconds the connection may not receive data from the
        // client before closing the connection
        connection.setIdleTimeout(idleTimeout);

        // Create a ClientSession for this user.
        String streamID = idFactory.createStreamID();
        ClientSession session = new ClientSession(serverName, connection, streamID);
        connection.init(session);
        session.socketReader = socketReader;
        // Set the stream ID that identifies the client when forwarding traffic to a client fails
        ((ClientFailoverDeliverer) connection.getPacketDeliverer()).setStreamID(streamID);
        // Register that the new session is associated with the specified stream ID
        sessions.put(streamID, session);
        // Send to the server that a new client session has been created
        serverSurrogate.clientSessionCreated(streamID);

        // Build the start packet response
        StringBuilder sb = new StringBuilder(200);
        sb.append("<?xml version='1.0' encoding='");
        sb.append(CHARSET);
        sb.append("'?>");
        if (isFlashClient) {
            sb.append("<flash:stream xmlns:flash=\"http://www.jabber.com/streams/flash\" ");
        }
        else {
            sb.append("<stream:stream ");
        }
        sb.append("xmlns:stream=\"http://etherx.jabber.org/streams\" xmlns=\"jabber:client\" from=\"");
        sb.append(serverName);
        sb.append("\" id=\"");
        sb.append(session.getStreamID());
        sb.append("\" xml:lang=\"");
        sb.append(language);
        // Don't include version info if the version is 0.0.
        if (majorVersion != 0) {
            sb.append("\" version=\"");
            sb.append(majorVersion).append(".").append(minorVersion);
        }
        sb.append("\">");
        connection.deliverRawText(sb.toString());

        // If this is a "Jabber" connection, the session is now initialized and we can
        // return to allow normal packet parsing.
        if (majorVersion == 0) {
            return session;
        }
        // Otherwise, this is at least XMPP 1.0 so we need to announce stream features.

        sb = new StringBuilder(490);
        sb.append("<stream:features>");
        if (connection.getTlsPolicy() != Connection.TLSPolicy.disabled) {
            sb.append("<starttls xmlns=\"urn:ietf:params:xml:ns:xmpp-tls\">");
            if (connection.getTlsPolicy() == Connection.TLSPolicy.required) {
                sb.append("<required/>");
            }
            sb.append("</starttls>");
        }
        // Include available SASL Mechanisms
        sb.append(serverSurrogate.getSASLMechanisms(session));
        // Include Stream features
        String specificFeatures = session.getAvailableStreamFeatures();
        if (specificFeatures != null) {
            sb.append(specificFeatures);
        }
        sb.append("</stream:features>");

        connection.deliverRawText(sb.toString());
        return session;
    }

    /**
     * Closes connections of connected clients since the server or the connection
     * manager is being shut down. If the server is the one that is being shut down
     * then the connection manager will keep running and will try to establish new
     * connections to the server (on demand).
     */
    public static void closeAll() {
        for (ClientSession session : sessions.values()) {
            session.close(true);
        }
    }

    /**
     * Returns the session whose stream ID matches the specified stream ID.
     *
     * @param streamID the stream ID of the session to look for.
     * @return the session whose stream ID matches the specified stream ID.
     */
    public static ClientSession getSession(String streamID) {
        return sessions.get(streamID);
    }

    public ClientSession(String serverName, Connection connection, String streamID) {
        super(serverName, connection, streamID);
    }

    public String getAvailableStreamFeatures() {
        // Offer authenticate and registration only if TLS was not required or if required
        // then the connection is already secured
        if (conn.getTlsPolicy() == Connection.TLSPolicy.required && !conn.isSecure()) {
            return null;
        }

        StringBuilder sb = new StringBuilder(200);

        // Include Stream Compression Mechanism
        if (conn.getCompressionPolicy() != Connection.CompressionPolicy.disabled &&
                !conn.isCompressed()) {
            sb.append(
                    "<compression xmlns=\"http://jabber.org/features/compress\"><method>zlib</method></compression>");
        }

        if (getStatus() != Session.STATUS_AUTHENTICATED) {
            ServerSurrogate serverSurrogate = ConnectionManager.getInstance().getServerSurrogate();
            // Advertise that the server supports Non-SASL Authentication
            if (serverSurrogate.isNonSASLAuthEnabled()) {
                sb.append("<auth xmlns=\"http://jabber.org/features/iq-auth\"/>");
            }
            // Advertise that the server supports In-Band Registration
            if (serverSurrogate.isInbandRegEnabled()) {
                sb.append("<register xmlns=\"http://jabber.org/features/iq-register\"/>");
            }
        }
        else {
            // If the session has been authenticated then offer resource binding
            // and session establishment
            sb.append("<bind xmlns=\"urn:ietf:params:xml:ns:xmpp-bind\"/>");
            sb.append("<session xmlns=\"urn:ietf:params:xml:ns:xmpp-session\"/>");
        }
        return sb.toString();
    }

    /**
     * Delivers a stanza sent by the server to the client.
     *
     * @param stanza the stanza sent by the server.
     */
    public void deliver(Element stanza) {
        // Until session is not authenticated we need to inspect server traffic
        if (status != Session.STATUS_AUTHENTICATED) {
            String tag = stanza.getName();
            if ("success".equals(tag)) {
                // Session has been authenticated (using SASL). Update status
                setStatus(Session.STATUS_AUTHENTICATED);
                // Notify the socket reader that sasl authentication has finished
                socketReader.clientAuthenticated(true);
            }
            else if ("failure".equals(tag)) {
                // Notify the socket reader that sasl authentication has finished
                socketReader.clientAuthenticated(false);
            }
            else if ("challenge".equals(tag)) {
                // Notify the socket reader that client needs to respond to challenge
                socketReader.clientChallenged();
            }
        }
        // Deliver stanza to client
        if (conn != null && !conn.isClosed()) {
            try {
                conn.deliver(stanza);
            }
            catch (Exception e) {
                Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            }
        }
    }

    public void close() {
        close(false);
    }

    /**
     * Closes the client connection. The <tt>systemStopped</tt> parameter indicates if the
     * client connection is being closed because the server is shutting down or unavailable
     * or if it is because the connection manager is being shutdown.
     *
     * @param systemStopped true when the server is no longer available or the
     *        connection manager is being shutdown.
     */
    public void close(boolean systemStopped) {
        if (status != STATUS_CLOSED) {
            // Close the connection of the client
            if (systemStopped) {
                conn.systemShutdown();
            }
            else  {
                conn.close();
            }
            // Changhe the status to closed
            status = STATUS_CLOSED;
            // Remove session from list of sessions
            sessions.remove(getStreamID());
            // Tell the server that the client session has been closed
            ConnectionManager.getInstance().getServerSurrogate().clientSessionClosed(getStreamID());
        }
    }
}
