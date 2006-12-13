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

package org.jivesoftware.multiplexer.net;

import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.jivesoftware.multiplexer.*;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.StringUtils;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.StringReader;

/**
 * A StanzaHandler is the main responsible for handling incoming stanzas. Some stanzas like startTLS
 * are totally managed by this class. Other stanzas are just forwarded to the server.
 *
 * @author Gaston Dombiak
 */
abstract class StanzaHandler {
    /**
     * The utf-8 charset for decoding and encoding Jabber packet streams.
     */
    protected static String CHARSET = "UTF-8";
    private static final String STREAM_START = "<stream:stream";
    /**
     * Reuse the same factory for all the connections.
     */
    private static XmlPullParserFactory factory = null;

    private Connection connection;

    // DANIELE: Indicate if a session is already created
    private boolean sessionCreated = false;

    // Flag that indicates that the client requested to be authenticated. Once the
    // authentication process is over the value will return to false.
    private boolean startedSASL = false;

    // DANIELE: Indicate if a stream:stream is arrived to complete compression
    private boolean waitingCompressionACK = false;

    /**
     * Session associated with the socket reader.
     */
    protected Session session;
    /**
     * Server name for which we are attending clients.
     */
    private String serverName;

    /**
     * Router used to route incoming packets to the correct channels.
     */
    private PacketRouter router;

    private SAXReader reader;

    static {
        try {
            factory = XmlPullParserFactory.newInstance(MXParser.class.getName(), null);
            factory.setNamespaceAware(true);
        }
        catch (XmlPullParserException e) {
            Log.error("Error creating a parser factory", e);
        }
    }

    /**
     * Creates a dedicated reader for a socket.
     *
     * @param router the router for sending packets that were read.
     * @param serverName the name of the server this socket is working for.
     * @param connection the connection being read.
     */
    public StanzaHandler(PacketRouter router, String serverName, Connection connection) {
        this.serverName = serverName;
        this.router = router;
        this.connection = connection;
        // Reader is associated with a new XMPPPacketReader
        reader = new SAXReader();
        reader.setEncoding(CHARSET);
    }

    public void process(String stanza) throws Exception {

        boolean initialStream = stanza.startsWith(STREAM_START);
        if (!sessionCreated || initialStream) {
            if (!initialStream) {
                // Ignore <?xml version="1.0"?>
                return;
            }
            // Found an stream:stream tag...
            if (!sessionCreated) {
                sessionCreated = true;
                MXParser parser = (MXParser) factory.newPullParser();
                parser.setInput(new StringReader(stanza));
                createSession(parser);
            } else if (startedSASL && session.getStatus() == Session.STATUS_AUTHENTICATED) {
                startedSASL = false;
                saslSuccessful();
            } else if (waitingCompressionACK) {
                waitingCompressionACK = false;
                compressionSuccessful();
            }
            return;
        }

        // Create DOM object from received stanza
        Element doc;
        try {
            doc = reader.read(new StringReader(stanza)).getRootElement();
        } catch (DocumentException e) {
            if (stanza.equals("</stream:stream>")) {
                session.close();
                return;
            }
            // Throw the exception. This will close the connection
            throw e;
        }
        if (doc == null) {
            // No document found.
            return;
        }
        String tag = doc.getName();
        if ("starttls".equals(tag)) {
            // Negotiate TLS
            if (negotiateTLS()) {
                tlsNegotiated();
            } else {
                connection.close();
                session = null;
            }
        } else if ("auth".equals(tag)) {
            // User is trying to authenticate using SASL
            startedSASL = true;
            // Forward packet to the server
            process(doc);
        } else if ("compress".equals(tag)) {
            // Client is trying to initiate compression
            if (compressClient(doc)) {
                // Compression was successful so open a new stream and offer
                // resource binding and session establishment (to client sessions only)
                waitingCompressionACK = true;
            }
        } else {
            process(doc);
        }
    }

    protected void process(Element doc) {
        if (doc == null) {
            return;
        }

        // Ensure that connection was secured if TLS was required
        if (connection.getTlsPolicy() == Connection.TLSPolicy.required &&
                !connection.isSecure()) {
            closeNeverSecuredConnection();
            return;
        }
        router.route(doc, session.getStreamID());
    }

    /**
     * Tries to secure the connection using TLS. If the connection is secured then reset
     * the parser to use the new secured reader. But if the connection failed to be secured
     * then send a <failure> stanza and close the connection.
     *
     * @return true if the connection was secured.
     */
    private boolean negotiateTLS() {
        if (connection.getTlsPolicy() == Connection.TLSPolicy.disabled) {
            // Set the not_authorized error
            StreamError error = new StreamError(StreamError.Condition.not_authorized);
            // Deliver stanza
            connection.deliverRawText(error.toXML());
            // Close the underlying connection
            connection.close();
            // Log a warning so that admins can track this case from the server side
            Log.warn("TLS requested by initiator when TLS was never offered by server. " +
                    "Closing connection : " + connection);
            return false;
        }
        // Client requested to secure the connection using TLS. Negotiate TLS.
        try {
            connection.startTLS(false, null);
        }
        catch (Exception e) {
            Log.error("Error while negotiating TLS", e);
            connection.deliverRawText("<failure xmlns=\"urn:ietf:params:xml:ns:xmpp-tls\">");
            connection.close();
            return false;
        }
        return true;
    }

    /**
     * TLS negotiation was successful so open a new stream and offer the new stream features.
     * The new stream features will include available SASL mechanisms and specific features
     * depending on the session type such as auth for Non-SASL authentication and register
     * for in-band registration.
     */
    private void tlsNegotiated() {
        // Offer stream features including SASL Mechanisms
        StringBuilder sb = new StringBuilder(620);
        sb.append(geStreamHeader());
        sb.append("<stream:features>");
        // Include available SASL Mechanisms
        sb.append(ConnectionManager.getInstance().getServerSurrogate().getSASLMechanisms(session));
        // Include specific features such as auth and register for client sessions
        String specificFeatures = session.getAvailableStreamFeatures();
        if (specificFeatures != null) {
            sb.append(specificFeatures);
        }
        sb.append("</stream:features>");
        connection.deliverRawText(sb.toString());
    }

    /**
     * After SASL authentication was successful we should open a new stream and offer
     * new stream features such as resource binding and session establishment. Notice that
     * resource binding and session establishment should only be offered to clients (i.e. not
     * to servers or external components)
     */
    private void saslSuccessful() {
        StringBuilder sb = new StringBuilder(420);
        sb.append(geStreamHeader());
        sb.append("<stream:features>");

        // Include specific features such as resource binding and session establishment
        // for client sessions
        String specificFeatures = session.getAvailableStreamFeatures();
        if (specificFeatures != null) {
            sb.append(specificFeatures);
        }
        sb.append("</stream:features>");
        connection.deliverRawText(sb.toString());
    }

    /**
     * Start using compression but first check if the connection can and should use compression.
     * The connection will be closed if the requested method is not supported, if the connection
     * is already using compression or if client requested to use compression but this feature
     * is disabled.
     *
     * @param doc the element sent by the client requesting compression. Compression method is
     *            included.
     * @return true if it was possible to use compression.
     * @throws IOException if an error occurs while starting using compression.
     */
    private boolean compressClient(Element doc) {
        String error = null;
        if (connection.getCompressionPolicy() == Connection.CompressionPolicy.disabled) {
            // Client requested compression but this feature is disabled
            error = "<failure xmlns='http://jabber.org/protocol/compress'><setup-failed/></failure>";
            // Log a warning so that admins can track this case from the server side
            Log.warn("Client requested compression while compression is disabled. Closing " +
                    "connection : " + connection);
        } else if (connection.isCompressed()) {
            // Client requested compression but connection is already compressed
            error = "<failure xmlns='http://jabber.org/protocol/compress'><setup-failed/></failure>";
            // Log a warning so that admins can track this case from the server side
            Log.warn("Client requested compression and connection is already compressed. Closing " +
                    "connection : " + connection);
        } else {
            // Check that the requested method is supported
            String method = doc.elementText("method");
            if (!"zlib".equals(method)) {
                error = "<failure xmlns='http://jabber.org/protocol/compress'><unsupported-method/></failure>";
                // Log a warning so that admins can track this case from the server side
                Log.warn("Requested compression method is not supported: " + method +
                        ". Closing connection : " + connection);
            }
        }

        if (error != null) {
            // Deliver stanza
            connection.deliverRawText(error);
            return false;
        } else {
            // Indicate client that he can proceed and compress the socket
            connection.deliverRawText("<compressed xmlns='http://jabber.org/protocol/compress'/>");

            // Start using compression
            connection.startCompression();
            return true;
        }
    }

    /**
     * After compression was successful we should open a new stream and offer
     * new stream features such as resource binding and session establishment. Notice that
     * resource binding and session establishment should only be offered to clients (i.e. not
     * to servers or external components)
     */
    private void compressionSuccessful() {
        StringBuilder sb = new StringBuilder(340);
        sb.append(geStreamHeader());
        sb.append("<stream:features>");
        // Include SASL mechanisms only if client has not been authenticated
        if (session.getStatus() != Session.STATUS_AUTHENTICATED) {
            // Include available SASL Mechanisms
            sb.append(ConnectionManager.getInstance().getServerSurrogate().getSASLMechanisms(
                    session));
        }
        // Include specific features such as resource binding and session establishment
        // for client sessions
        String specificFeatures = session.getAvailableStreamFeatures();
        if (specificFeatures != null) {
            sb.append(specificFeatures);
        }
        sb.append("</stream:features>");
        connection.deliverRawText(sb.toString());
    }

    private String geStreamHeader() {
        StringBuilder sb = new StringBuilder(200);
        sb.append("<?xml version='1.0' encoding='");
        sb.append(CHARSET);
        sb.append("'?>");
        if (connection.isFlashClient()) {
            sb.append("<flash:stream xmlns:flash=\"http://www.jabber.com/streams/flash\" ");
        } else {
            sb.append("<stream:stream ");
        }
        sb.append("xmlns:stream=\"http://etherx.jabber.org/streams\" xmlns=\"");
        sb.append(getNamespace());
        sb.append("\" from=\"");
        sb.append(serverName);
        sb.append("\" id=\"");
        sb.append(session.getStreamID());
        sb.append("\" xml:lang=\"");
        sb.append(connection.getLanguage());
        sb.append("\" version=\"");
        sb.append(Session.MAJOR_VERSION).append(".").append(Session.MINOR_VERSION);
        sb.append("\">");
        return sb.toString();
    }

    /**
     * Close the connection since TLS was mandatory and the entity never negotiated TLS. Before
     * closing the connection a stream error will be sent to the entity.
     */
    void closeNeverSecuredConnection() {
        // Set the not_authorized error
        StreamError error = new StreamError(StreamError.Condition.not_authorized);
        // Deliver stanza
        connection.deliverRawText(error.toXML());
        // Close the underlying connection
        connection.close();
        // Log a warning so that admins can track this case from the server side
        Log.warn("TLS was required by the server and connection was never secured. " +
                "Closing connection : " + connection);
    }

    /**
     * Uses the XPP to grab the opening stream tag and create an active session
     * object. The session to create will depend on the sent namespace. In all
     * cases, the method obtains the opening stream tag, checks for errors, and
     * either creates a session or returns an error and kills the connection.
     * If the connection remains open, the XPP will be set to be ready for the
     * first packet. A call to next() should result in an START_TAG state with
     * the first packet in the stream.
     */
    protected void createSession(XmlPullParser xpp) throws XmlPullParserException, IOException {
        for (int eventType = xpp.getEventType(); eventType != XmlPullParser.START_TAG;) {
            eventType = xpp.next();
        }

        // Check that the TO attribute of the stream header matches the server name or a valid
        // subdomain. If the value of the 'to' attribute is not valid then return a host-unknown
        // error and close the underlying connection.
        String host = xpp.getAttributeValue("", "to");
        if (validateHost() && isHostUnknown(host)) {
            StringBuilder sb = new StringBuilder(250);
            sb.append("<?xml version='1.0' encoding='");
            sb.append(CHARSET);
            sb.append("'?>");
            // Append stream header
            sb.append("<stream:stream ");
            sb.append("from=\"").append(serverName).append("\" ");
            sb.append("id=\"").append(StringUtils.randomString(5)).append("\" ");
            sb.append("xmlns=\"").append(xpp.getNamespace(null)).append("\" ");
            sb.append("xmlns:stream=\"").append(xpp.getNamespace("stream")).append("\" ");
            sb.append("version=\"1.0\">");
            // Set the host_unknown error
            StreamError error = new StreamError(StreamError.Condition.host_unknown);
            sb.append(error.toXML());
            // Deliver stanza
            connection.deliverRawText(sb.toString());
            // Close the underlying connection
            connection.close();
            // Log a warning so that admins can track this cases from the server side
            Log.warn("Closing session due to incorrect hostname in stream header. Host: " + host +
                    ". Connection: " + connection);
        }

        // Create the correct session based on the sent namespace. At this point the server
        // may offer the client to secure the connection. If the client decides to secure
        // the connection then a <starttls> stanza should be received
        else if (!createSession(xpp.getNamespace(null), serverName, xpp, connection)) {
            // No session was created because of an invalid namespace prefix so answer a stream
            // error and close the underlying connection
            StringBuilder sb = new StringBuilder(250);
            sb.append("<?xml version='1.0' encoding='");
            sb.append(CHARSET);
            sb.append("'?>");
            // Append stream header
            sb.append("<stream:stream ");
            sb.append("from=\"").append(serverName).append("\" ");
            sb.append("id=\"").append(StringUtils.randomString(5)).append("\" ");
            sb.append("xmlns=\"").append(xpp.getNamespace(null)).append("\" ");
            sb.append("xmlns:stream=\"").append(xpp.getNamespace("stream")).append("\" ");
            sb.append("version=\"1.0\">");
            // Include the bad-namespace-prefix in the response
            StreamError error = new StreamError(StreamError.Condition.bad_namespace_prefix);
            sb.append(error.toXML());
            connection.deliverRawText(sb.toString());
            // Close the underlying connection
            connection.close();
            // Log a warning so that admins can track this cases from the server side
            Log.warn("Closing session due to bad_namespace_prefix in stream header. Prefix: " +
                    xpp.getNamespace(null) + ". Connection: " + connection);
        }
    }

    private boolean isHostUnknown(String host) {
        if (host == null) {
            // Answer false since when using server dialback the stream header will not
            // have a TO attribute
            return false;
        }
        if (serverName.equals(host)) {
            // requested host matched the server name
            return false;
        }
        return true;
    }

    /**
     * Returns the stream namespace. (E.g. jabber:client, jabber:server, etc.).
     *
     * @return the stream namespace.
     */
    abstract String getNamespace();

    /**
     * Returns true if the value of the 'to' attribute in the stream header should be
     * validated. If the value of the 'to' attribute is not valid then a host-unknown error
     * will be returned and the underlying connection will be closed.
     *
     * @return true if the value of the 'to' attribute in the initial stream header should be
     *         validated.
     */
    abstract boolean validateHost();

    /**
     * Creates the appropriate {@link Session} subclass based on the specified namespace.
     *
     * @param namespace the namespace sent in the stream element. eg. jabber:client.
     * @return the created session or null.
     * @throws org.xmlpull.v1.XmlPullParserException
     */
    abstract boolean createSession(String namespace, String serverName, XmlPullParser xpp, Connection connection)
            throws XmlPullParserException;
}
