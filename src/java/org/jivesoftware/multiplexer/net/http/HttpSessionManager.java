/**
 * $RCSfile:  $
 * $Revision:  $
 * $Date:  $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 * This software is the proprietary information of Jive Software. Use is subject to license terms.
 */
package org.jivesoftware.multiplexer.net.http;

import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.multiplexer.ServerSurrogate;
import org.jivesoftware.multiplexer.ConnectionManager;
import org.jivesoftware.multiplexer.Session;
import org.dom4j.Element;

import java.util.List;

/**
 *
 */
public class HttpSessionManager {

    /**
     * Milliseconds a connection has to be idle to be closed. Default is 30 minutes. Sending
     * stanzas to the client is not considered as activity. We are only considering the connection
     * active when the client sends some data or hearbeats (i.e. whitespaces) to the server.
     * The reason for this is that sending data will fail if the connection is closed. And if
     * the thread is blocked while sending data (because the socket is closed) then the clean up
     * thread will close the socket anyway.
     */
    private static long inactivityTimeout;

    /**
     * The connection manager MAY limit the number of simultaneous requests the client makes with
     * the 'requests' attribute. The RECOMMENDED value is "2". Servers that only support polling
     * behavior MUST prevent clients from making simultaneous requests by setting the 'requests'
     * attribute to a value of "1" (however, polling is NOT RECOMMENDED). In any case, clients MUST
     * NOT make more simultaneous requests than specified by the connection manager.
     */
    private static int maxRequests;

    /**
     * The connection manager SHOULD include two additional attributes in the session creation
     * response element, specifying the shortest allowable polling interval and the longest
     * allowable inactivity period (both in seconds). Communication of these parameters enables
     * the client to engage in appropriate behavior (e.g., not sending empty request elements more
     * often than desired, and ensuring that the periods with no requests pending are
     * never too long).
     */
    private static int pollingInterval;

    private String serverName;
    private ServerSurrogate serverSurrogate;

    static {
        // Set the default read idle timeout. If none was set then assume 30 minutes
        inactivityTimeout = JiveGlobals.getIntProperty("xmpp.httpbind.client.idle", 30);
        maxRequests = JiveGlobals.getIntProperty("xmpp.httpbind.client.requests.max", 2);
        pollingInterval = JiveGlobals.getIntProperty("xmpp.httpbind.client.requests.polling", 5);
    }

    public HttpSessionManager(String serverName) {
        this.serverName = serverName;
        this.serverSurrogate = ConnectionManager.getInstance().getServerSurrogate();
    }

    public HttpSession getSession(String streamID) {
        Session session = Session.getSession(streamID);
        if(session instanceof HttpSession) {
            return (HttpSession) session;
        }
        return null;
    }

    public HttpSession createSession(Element rootNode, HttpConnection connection) {
        // TODO Check if IP address is allowed to connect to the server

        // Default language is English ("en").
        String language = rootNode.attributeValue("xml:lang");
        if(language == null || "".equals(language)) {
            language = "en";
        }

        int wait = getIntAttribute(rootNode.attributeValue("wait"), 60);
        int hold = getIntAttribute(rootNode.attributeValue("hold"), 1);

        // Indicate the compression policy to use for this connection
        connection.setCompressionPolicy(serverSurrogate.getCompressionPolicy());

        HttpSession session = createSession(serverName);
        session.setWait(wait);
        session.setHold(hold);
        // Store language and version information in the connection.
        session.setLanaguage(language);
        connection.deliverBody(createSessionCreationResponse(session));

        return session;
    }

    private HttpSession createSession(String serverName) {
        ServerSurrogate serverSurrogate = ConnectionManager.getInstance().getServerSurrogate();
        // Create a ClientSession for this user.
        String streamID = Session.idFactory.createStreamID();
        HttpSession session = new HttpSession(serverName, streamID);
        // Register that the new session is associated with the specified stream ID
        HttpSession.addSession(streamID, session);
        // Send to the server that a new client session has been created
        serverSurrogate.clientSessionCreated(streamID);
        return session;
    }

    private static int getIntAttribute(String value, int defaultValue) {
        if(value == null || "".equals(value)) {
            return defaultValue;
        }
        try {
            return Integer.valueOf(value);
        }
        catch (Exception ex) {
            return defaultValue;
        }
    }

    private  String createSessionCreationResponse(HttpSession session) {
        StringBuilder builder = new StringBuilder();
        builder.append("<body")
                .append(" xmlns='http://jabber.org/protocol/httpbind'").append(" authID='")
                .append(session.getStreamID()).append("'")
                .append(" sid='").append(session.getStreamID()).append("'")
                .append(" secure='true" + "'").append(" requests='")
                .append(String.valueOf(maxRequests)).append("'")
                .append(" inactivity='").append(String.valueOf(inactivityTimeout)).append("'")
                .append(" polling='").append(String.valueOf(pollingInterval)).append("'")
                .append(" wait='").append(String.valueOf(session.getWait())).append("'")
                .append(">");
        builder.append("<stream:features>");
        builder.append(serverSurrogate.getSASLMechanismsElement(session).asXML());
        builder.append("<bind xmlns=\"urn:ietf:params:xml:ns:xmpp-bind\"/>");
        builder.append("<session xmlns=\"urn:ietf:params:xml:ns:xmpp-session\"/>");
        builder.append("</stream:features>");
        builder.append("</body>");

        return builder.toString();
    }

    public HttpConnection forwardRequest(long rid, HttpSession session, Element rootNode) {
        HttpConnection connection = new HttpConnection(rid);
        session.addConnection(connection);

        //noinspection unchecked
        List<Element> elements = rootNode.elements();
        for(Element packet : elements) {
            serverSurrogate.send(packet, session.getStreamID());
        }

        return connection;
    }
}
