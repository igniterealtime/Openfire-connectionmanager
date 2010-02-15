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
import org.jivesoftware.multiplexer.spi.ClientFailoverDeliverer;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Session that represents a client to server connection.
 *
 * @author Gaston Dombiak
 */
public class ClientSession extends Session {

    private static final String ETHERX_NAMESPACE = "http://etherx.jabber.org/streams";
    private static final String FLASH_NAMESPACE = "http://www.jabber.com/streams/flash";
    private static ConnectionCloseListener closeListener;

    static {
        closeListener = new ConnectionCloseListener() {
            public void onConnectionClose(Object handback) {
                ClientSession session = (ClientSession) handback;
                // Mark the session as closed
                session.close(false);
            }
        };
    }

    public static Session createSession(String serverName, XmlPullParser xpp, Connection connection)
            throws XmlPullParserException {

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

        // Create a ClientSession for this user.
        String streamID = idFactory.createStreamID();
        ClientSession session = new ClientSession(serverName, connection, streamID);
        connection.init(session);
        // Set the stream ID that identifies the client when forwarding traffic to a client fails
        ((ClientFailoverDeliverer) connection.getPacketDeliverer()).setStreamID(streamID);
        // Listen when the connection is closed
        connection.registerCloseListener(closeListener, session);
        // Register that the new session is associated with the specified stream ID
        Session.addSession(streamID, session);
        // Send to the server that a new client session has been created
        InetAddress address = null;
        try {
            address = connection.getInetAddress();
        } catch (UnknownHostException e) {
            // Do nothing
        }
        serverSurrogate.clientSessionCreated(streamID, address);

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

    public ClientSession(String serverName, Connection connection, String streamID) {
        super(serverName, connection, streamID);
    }

    @Override
	public String getAvailableStreamFeatures() {
        // Offer authenticate and registration only if TLS was not required or if required
        // then the connection is already secured
        if (conn.getTlsPolicy() == Connection.TLSPolicy.required && !conn.isSecure()) {
            return null;
        }

        StringBuilder sb = new StringBuilder(200);
        // TODO Fix compression with MINA and re-enable this code
        // Include Stream Compression Mechanism
        /*if (conn.getCompressionPolicy() != Connection.CompressionPolicy.disabled &&
                !conn.isCompressed()) {
            sb.append(
                    "<compression xmlns=\"http://jabber.org/features/compress\"><method>zlib</method></compression>");
        }*/

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
    @Override
	public void deliver(Element stanza) {
        // Until session is not authenticated we need to inspect server traffic
        if (status != Session.STATUS_AUTHENTICATED) {
            String tag = stanza.getName();
            if ("success".equals(tag)) {
                // Session has been authenticated (using SASL). Update status
                setStatus(Session.STATUS_AUTHENTICATED);
            }
            else if ("failure".equals(tag)) {
                // Sasl authentication has failed
                // Ignore for now
            }
            else if ("challenge".equals(tag)) {
                // A challenge was sent to the client. Client needs to respond
                // Ignore for now
            }
        }
        // Deliver stanza to client
        if (conn != null && !conn.isClosed()) {
            try {
                conn.deliver(stanza.asXML());
            }
            catch (Exception e) {
                Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            }
        }
    }

    @Override
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
    @Override
	public void close(boolean systemStopped) {
        if (status != STATUS_CLOSED) {
            // Change the status to closed
            status = STATUS_CLOSED;
            // Close the connection of the client
            if (systemStopped) {
                conn.systemShutdown();
            }
            else  {
                conn.close();
            }
            // Remove session from list of sessions
            removeSession(getStreamID());
            // Tell the server that the client session has been closed
            ConnectionManager.getInstance().getServerSurrogate().clientSessionClosed(getStreamID());
        }
    }

    @Override
	public boolean isClosed() {
        return status == STATUS_CLOSED;
    }
}
