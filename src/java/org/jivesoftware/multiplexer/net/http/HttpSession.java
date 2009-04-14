/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.multiplexer.net.http;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.QName;
import org.dom4j.io.XMPPPacketReader;
import org.jivesoftware.multiplexer.ClientSession;
import org.jivesoftware.multiplexer.ConnectionManager;
import org.jivesoftware.multiplexer.Session;
import org.jivesoftware.multiplexer.net.MXParser;
import org.jivesoftware.util.JiveConstants;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.StringReader;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * A session represents a serious of interactions with an XMPP client sending packets using the HTTP
 * Binding protocol specified in <a href="http://www.xmpp.org/extensions/xep-0124.html">XEP-0124</a>.
 * A session can have several client connections open simultaneously while awaiting packets bound
 * for the client from the server.
 *
 * @author Alexander Wenckus
 */
public class HttpSession extends ClientSession {
    private static XmlPullParserFactory factory = null;
    private static ThreadLocal<XMPPPacketReader> localParser = null;
    static {
        try {
            factory = XmlPullParserFactory.newInstance(MXParser.class.getName(), null);
            factory.setNamespaceAware(true);
        }
        catch (XmlPullParserException e) {
            Log.error("Error creating a parser factory", e);
        }
        // Create xmpp parser to keep in each thread
        localParser = new ThreadLocal<XMPPPacketReader>() {
            protected XMPPPacketReader initialValue() {
                XMPPPacketReader parser = new XMPPPacketReader();
                factory.setNamespaceAware(true);
                parser.setXPPFactory(factory);
                return parser;
            }
        };
    }

    private int wait;
    private int hold = 0;
    private String language;
    private final List<HttpConnection> connectionQueue = new LinkedList<HttpConnection>();
    private final List<Deliverable> pendingElements = new ArrayList<Deliverable>();
    private final List<Delivered> sentElements = new ArrayList<Delivered>();
    private boolean isSecure;
    private int maxPollingInterval;
    private long lastPoll = -1;
    private Set<SessionListener> listeners = new CopyOnWriteArraySet<SessionListener>();
    private volatile boolean isClosed;
    private int inactivityTimeout;
    private int defaultInactivityTimeout;
    private long lastActivity;
    private long lastRequestID;
    private boolean lastResponseEmpty;
    private int maxRequests;
    private int maxPause;
    private int majorVersion = -1;
    private int minorVersion = -1;

    // Semaphore which protects the packets to send, so, there can only be one consumer at a time.

    private static final Comparator<HttpConnection> connectionComparator
            = new Comparator<HttpConnection>() {
        public int compare(HttpConnection o1, HttpConnection o2) {
            return (int) (o1.getRequestId() - o2.getRequestId());
        }
    };
    private ConnectionManager connectionManager;

    public HttpSession(String serverName, String streamID, long rid) {
        super(serverName, null, streamID);
        this.lastActivity = System.currentTimeMillis();
        this.lastRequestID = rid;
        connectionManager = ConnectionManager.getInstance();
    }

    /**
     * Returns the stream features which are available for this session.
     *
     * @return the stream features which are available for this session.
     */
    public Collection<Element> getAvailableStreamFeaturesElements() {
        List<Element> elements = new ArrayList<Element>();

        if (getStatus() != Session.STATUS_AUTHENTICATED) {
            Element sasl = connectionManager.getServerSurrogate().getSASLMechanismsElement(this);
        	if (true || sasl != null) {
        		elements.add(sasl);
        	}
        }

        Element bind = DocumentHelper.createElement(new QName("bind",
                new Namespace("", "urn:ietf:params:xml:ns:xmpp-bind")));
        elements.add(bind);

        Element session = DocumentHelper.createElement(new QName("session",
                new Namespace("", "urn:ietf:params:xml:ns:xmpp-session")));
        elements.add(session);
        return elements;
    }

    public String getAvailableStreamFeatures() {
        StringBuilder sb = new StringBuilder(200);
        for (Element element : getAvailableStreamFeaturesElements()) {
            sb.append(element.asXML());
        }
        return sb.toString();
    }

    public void close() {
        closeConnection();
    }

    public void close(boolean isServerShuttingDown) {
        closeConnection();
    }

    /**
     * Returns true if this session has been closed and no longer activley accepting connections.
     *
     * @return true if this session has been closed and no longer activley accepting connections.
     */
    public synchronized boolean isClosed() {
        return isClosed;
    }

    /**
     * Specifies the longest time (in seconds) that the connection manager is allowed to wait before
     * responding to any request during the session. This enables the client to prevent its TCP
     * connection from expiring due to inactivity, as well as to limit the delay before it discovers
     * any network failure.
     *
     * @param wait the longest time it is permissible to wait for a response.
     */
    public void setWait(int wait) {
        this.wait = wait;
    }

    /**
     * Specifies the longest time (in seconds) that the connection manager is allowed to wait before
     * responding to any request during the session. This enables the client to prevent its TCP
     * connection from expiring due to inactivity, as well as to limit the delay before it discovers
     * any network failure.
     *
     * @return the longest time it is permissible to wait for a response.
     */
    public int getWait() {
        return wait;
    }

    /**
     * Specifies the maximum number of requests the connection manager is allowed to keep waiting at
     * any one time during the session. (For example, if a constrained client is unable to keep open
     * more than two HTTP connections to the same HTTP server simultaneously, then it SHOULD specify
     * a value of "1".)
     *
     * @param hold the maximum number of simultaneous waiting requests.
     */
    public void setHold(int hold) {
        this.hold = hold;
    }

    /**
     * Specifies the maximum number of requests the connection manager is allowed to keep waiting at
     * any one time during the session. (For example, if a constrained client is unable to keep open
     * more than two HTTP connections to the same HTTP server simultaneously, then it SHOULD specify
     * a value of "1".)
     *
     * @return the maximum number of simultaneous waiting requests
     */
    public int getHold() {
        return hold;
    }

    /**
     * Sets the language this session is using.
     *
     * @param language the language this session is using.
     */
    public void setLanguage(String language) {
        this.language = language;
    }

    /**
     * Returns the language this session is using.
     *
     * @return the language this session is using.
     */
    public String getLanguage() {
        return language;
    }

    /**
     * Sets the max interval within which a client can send polling requests. If more than one
     * request occurs in the interval the session will be terminated.
     *
     * @param maxPollingInterval time in seconds a client needs to wait before sending polls to the
     * server, a negative <i>int</i> indicates that there is no limit.
     */
    public void setMaxPollingInterval(int maxPollingInterval) {
        this.maxPollingInterval = maxPollingInterval;
    }

    /**
     * Returns the max interval within which a client can send polling requests. If more than one
     * request occurs in the interval the session will be terminated.
     *
     * @return the max interval within which a client can send polling requests. If more than one
     *         request occurs in the interval the session will be terminated.
     */
    public int getMaxPollingInterval() {
        return this.maxPollingInterval;
    }

    /**
     * The max number of requests it is permissable for this session to have open at any one time.
     *
     * @param maxRequests The max number of requests it is permissable for this session to have open
     * at any one time.
     */
    public void setMaxRequests(int maxRequests) {
        this.maxRequests = maxRequests;
    }

    /**
     * Returns the max number of requests it is permissable for this session to have open at any one
     * time.
     *
     * @return the max number of requests it is permissable for this session to have open at any one
     *         time.
     */
    public int getMaxRequests() {
        return this.maxRequests;
    }

    /**
     * Sets the maximum length of a temporary session pause (in seconds) that the client MAY
     * request.
     *
     * @param maxPause the maximum length of a temporary session pause (in seconds) that the client
     * MAY request.
     */
    public void setMaxPause(int maxPause) {
        this.maxPause = maxPause;
    }

    /**
     * Returns the maximum length of a temporary session pause (in seconds) that the client MAY
     * request.
     *
     * @return the maximum length of a temporary session pause (in seconds) that the client MAY
     *         request.
     */
    public int getMaxPause() {
        return this.maxPause;
    }

    /**
     * Returns true if all connections on this session should be secured, and false if they should
     * not.
     *
     * @return true if all connections on this session should be secured, and false if they should
     *         not.
     */
    public boolean isSecure() {
        return isSecure;
    }

    /**
     * Returns true if this session is a polling session. Some clients may be restricted to open
     * only one connection to the server. In this case the client SHOULD inform the server by
     * setting the values of the 'wait' and/or 'hold' attributes in its session creation request
     * to "0", and then "poll" the server at regular intervals throughout the session for stanzas
     * it may have received from the server.
     *
     * @return true if this session is a polling session.
     */
    public boolean isPollingSession() {
        return (this.wait == 0 || this.hold == 0);
    }

    /**
     * Adds a {@link SessionListener} to this session. The listener
     * will be notified of changes to the session.
     *
     * @param listener the listener which is being added to the session.
     */
    public void addSessionCloseListener(SessionListener listener) {
        listeners.add(listener);
    }

    /**
     * Removes a {@link SessionListener} from this session. The
     * listener will no longer be updated when an event occurs on the session.
     *
     * @param listener the session listener that is to be removed.
     */
    public void removeSessionCloseListener(SessionListener listener) {
        listeners.remove(listener);
    }

    /**
     * Sets the default inactivity timeout of this session. A session's inactivity timeout can
     * be temporarily changed using session pause requests.
     *
     * @see #pause(int)
     *
     * @param defaultInactivityTimeout the default inactivity timeout of this session.
     */
    public void setDefaultInactivityTimeout(int defaultInactivityTimeout) {
        this.defaultInactivityTimeout = defaultInactivityTimeout;
    }

    /**
     * Sets the time, in seconds, after which this session will be considered inactive and be be
     * terminated.
     *
     * @param inactivityTimeout the time, in seconds, after which this session will be considered
     * inactive and be terminated.
     */
    public void setInactivityTimeout(int inactivityTimeout) {
        this.inactivityTimeout = inactivityTimeout;
    }

    /**
     * Resets the inactivity timeout of this session to default. A session's inactivity timeout can
     * be temporarily changed using session pause requests.
     *
     * @see #pause(int)
     */
    public void resetInactivityTimeout() {
        this.inactivityTimeout = this.defaultInactivityTimeout;
    }

    /**
     * Returns the time, in seconds, after which this session will be considered inactive and
     * terminated.
     *
     * @return the time, in seconds, after which this session will be considered inactive and
     *         terminated.
     */
    public int getInactivityTimeout() {
        return inactivityTimeout;
    }

    /**
     * Pauses the session for the given amount of time. If a client encounters an exceptional
     * temporary situation during which it will be unable to send requests to the connection
     * manager for a period of time greater than the maximum inactivity period, then the client MAY
     * request a temporary increase to the maximum inactivity period by including a 'pause'
     * attribute in a request.
     *
     * @param duration the time, in seconds, after which this session will be considered inactive
     *        and terminated.
     */
    public void pause(int duration) {
    	// Respond immediately to all pending requests
        for (HttpConnection toClose : connectionQueue) {
            if (!toClose.isClosed()) {
                toClose.close();
                lastRequestID = toClose.getRequestId();
            }
        }
    	setInactivityTimeout(duration);
    }

    /**
     * Returns the time in milliseconds since the epoch that this session was last active. Activity
     * is a request was either made or responded to. If the session is currently active, meaning
     * there are connections awaiting a response, the current time is returned.
     *
     * @return the time in milliseconds since the epoch that this session was last active.
     */
    public synchronized long getLastActivity() {
        if (connectionQueue.isEmpty()) {
            return lastActivity;
        }
        else {
            for (HttpConnection connection : connectionQueue) {
                // The session is currently active, return the current time.
                if (!connection.isClosed()) {
                    return System.currentTimeMillis();
                }
            }
            // We have no currently open connections therefore we can assume that lastActivity is
            // the last time the client did anything.
            return lastActivity;
        }
    }

    /**
     * Returns the highest 'rid' attribute the server has received where it has also received
     * all requests with lower 'rid' values. When responding to a request that it has been
     * holding, if the server finds it has already received another request with a higher 'rid'
     * attribute (typically while it was holding the first request), then it MAY acknowledge the
     * reception to the client.
     *
     * @return the highest 'rid' attribute the server has received where it has also received
     * all requests with lower 'rid' values.
     */
    public long getLastAcknowledged() {
    	long ack = lastRequestID;
    	Collections.sort(connectionQueue, connectionComparator);
        for (HttpConnection connection : connectionQueue) {
            if (connection.getRequestId() == ack + 1) {
            	ack++;
            }
        }
        return ack;
    }

    /**
     * Sets the major version of BOSH which the client implements. Currently, the only versions
     * supported by Openfire are 1.5 and 1.6.
     *
     * @param majorVersion the major version of BOSH which the client implements.
     */
    public void setMajorVersion(int majorVersion) {
        if(majorVersion != 1) {
            return;
        }
        this.majorVersion = majorVersion;
    }

    /**
     * Returns the major version of BOSH which this session utilizes. The version refers to the
     * version of the XEP which the connecting client implements. If the client did not specify
     * a version 1 is returned as 1.5 is the last version of the <a
     * href="http://www.xmpp.org/extensions/xep-0124.html">XEP</a> that the client was not
     * required to pass along its version information when creating a session.
     *
     * @return the major version of the BOSH XEP which the client is utilizing.
     */
    public int getMajorVersion() {
        if (this.majorVersion != -1) {
            return this.majorVersion;
        }
        else {
            return 1;
        }
    }

    /**
     * Sets the minor version of BOSH which the client implements. Currently, the only versions
     * supported by Openfire are 1.5 and 1.6. Any versions less than or equal to 5 will be
     * interpreted as 5 and any values greater than or equal to 6 will be interpreted as 6.
     *
     * @param minorVersion the minor version of BOSH which the client implements.
     */
    public void setMinorVersion(int minorVersion) {
    	if(minorVersion <= 5) {
        	this.minorVersion = 5;
        }
    	else if(minorVersion >= 6) {
        	this.minorVersion = 6;
        }
    }

    /**
     * Returns the major version of BOSH which this session utilizes. The version refers to the
     * version of the XEP which the connecting client implements. If the client did not specify
     * a version 5 is returned as 1.5 is the last version of the <a
     * href="http://www.xmpp.org/extensions/xep-0124.html">XEP</a> that the client was not
     * required to pass along its version information when creating a session.
     *
     * @return the minor version of the BOSH XEP which the client is utilizing.
     */
    public int getMinorVersion() {
        if (this.minorVersion != -1) {
            return this.minorVersion;
        }
        else {
            return 5;
        }
    }

    /**
     * lastResponseEmpty true if last response of this session is an empty body element. This
     * is used in overactivity checking.
     *
     * @param lastResponseEmpty true if last response of this session is an empty body element.
     */
	public void setLastResponseEmpty(boolean lastResponseEmpty) {
		this.lastResponseEmpty = lastResponseEmpty;
	}

    public String getResponse(long requestID) throws HttpBindException {
        for (HttpConnection connection : connectionQueue) {
            if (connection.getRequestId() == requestID) {
                String response = getResponse(connection);

                // connection needs to be removed after response is returned to maintain idempotence
                // otherwise if this method is called again, after 'waiting', the InternalError
                // will be thrown because the connection is no longer in the queue.
                connectionQueue.remove(connection);
                fireConnectionClosed(connection);
                return response;
            }
        }
        throw new InternalError("Could not locate connection: " + requestID);
    }

    private String getResponse(HttpConnection connection) throws HttpBindException {
        String response = null;
        try {
            response = connection.getResponse();
        }
        catch (HttpBindTimeoutException e) {
            // This connection timed out we need to increment the request count
            if (connection.getRequestId() != lastRequestID + 1) {
                throw new HttpBindException("Unexpected RID error.",
                        BoshBindingError.itemNotFound);
            }
            lastRequestID = connection.getRequestId();
        }
        if (response == null) {
            response = createEmptyBody();
            setLastResponseEmpty(true);
        }
        return response;
    }

    /**
     * Sets whether the initial request on the session was secure.
     *
     * @param isSecure true if the initial request was secure and false if it wasn't.
     */
    protected void setSecure(boolean isSecure) {
        this.isSecure = isSecure;
    }

    /**
     * Creates a new connection on this session. If a response is currently available for this
     * session the connection is responded to immediately, otherwise it is queued awaiting a
     * response.
     *
     * @param rid the request id related to the connection.
     * @param packetsToBeSent any packets that this connection should send.
     * @param isSecure true if the connection was secured using HTTPS.
     * @return the created {@link HttpConnection} which represents
     *         the connection.
     *
     * @throws HttpConnectionClosedException if the connection was closed before a response could be
     * delivered.
     * @throws HttpBindException if the connection has violated a facet of the HTTP binding
     * protocol.
     */
    synchronized HttpConnection createConnection(long rid, Collection<Element> packetsToBeSent,
                                                 boolean isSecure, boolean isPoll)
            throws HttpConnectionClosedException, HttpBindException
    {
        HttpConnection connection = new HttpConnection(rid, isSecure);
        if (rid <= lastRequestID) {
            Delivered deliverable = retrieveDeliverable(rid);
            if (deliverable == null) {
                Log.warn("Deliverable unavailable for " + rid);
                throw new HttpBindException("Unexpected RID error.",
                        BoshBindingError.itemNotFound);
            }
            connection.deliverBody(createDeliverable(deliverable.deliverables));
            return connection;
        }
        else if (rid > (lastRequestID + maxRequests)) {
        	Log.warn("Request " + rid + " > " + (lastRequestID + maxRequests) + ", ending session.");
                throw new HttpBindException("Unexpected RID error.",
                        BoshBindingError.itemNotFound);
        }

        addConnection(connection, isPoll);
        return connection;
    }

    private Delivered retrieveDeliverable(long rid) {
        for (Delivered delivered : sentElements) {
            if (delivered.getRequestID() == rid) {
                return delivered;
            }
        }
        return null;
    }

    private void addConnection(HttpConnection connection, boolean isPoll) throws HttpBindException,
            HttpConnectionClosedException {
        if (connection == null) {
            throw new IllegalArgumentException("Connection cannot be null.");
        }

        checkOveractivity(isPoll);

        if (isSecure && !connection.isSecure()) {
            throw new HttpBindException("Session was started from secure connection, all " +
                    "connections on this session must be secured.", BoshBindingError.badRequest);
        }

        connection.setSession(this);
        // We aren't supposed to hold connections open or we already have some packets waiting
        // to be sent to the client.
        if (isPollingSession() || (pendingElements.size() > 0 && connection.getRequestId() == lastRequestID + 1)) {
            deliver(connection, pendingElements);
            lastRequestID = connection.getRequestId();
            pendingElements.clear();
            connectionQueue.add(connection);
            Collections.sort(connectionQueue, connectionComparator);
        }
        else {
            // With this connection we need to check if we will have too many connections open,
            // closing any extras.

            connectionQueue.add(connection);
            Collections.sort(connectionQueue, connectionComparator);

            int connectionsToClose;
            if(connectionQueue.get(connectionQueue.size() - 1) != connection) {
            	// Current connection does not have the greatest rid. That means
            	// requests were received out of order, respond to all.
            	connectionsToClose = connectionQueue.size();
            }
            else {
                // Everything's fine, number of current connections open tells us
            	// how many that we need to close.
            	connectionsToClose = getOpenConnectionCount() - hold;
            }
            int closed = 0;
            for (int i = 0; i < connectionQueue.size() && closed < connectionsToClose; i++) {
                HttpConnection toClose = connectionQueue.get(i);
                if (!toClose.isClosed() && toClose.getRequestId() == lastRequestID + 1) {
                    if(toClose == connection) {
                    	// Current connection has no continuation yet, just deliver.
                    	deliver(new Deliverable(""));
                    }
                    else {
                        toClose.close();
                    }
                    lastRequestID = toClose.getRequestId();
                    closed++;
                }
            }
        }
        fireConnectionOpened(connection);
    }

    private int getOpenConnectionCount() {
        int count = 0;
        for (HttpConnection connection : connectionQueue) {
            if (!connection.isClosed()) {
                count++;
            }
        }
        return count;
    }

    private void deliver(HttpConnection connection, Collection<Deliverable> deliverable)
            throws HttpConnectionClosedException {
        connection.deliverBody(createDeliverable(deliverable));

        Delivered delivered = new Delivered(deliverable);
        delivered.setRequestID(connection.getRequestId());
        while (sentElements.size() > hold) {
            sentElements.remove(0);
        }

        sentElements.add(delivered);
    }

    private void fireConnectionOpened(HttpConnection connection) {
        lastActivity = System.currentTimeMillis();
        for (SessionListener listener : listeners) {
            listener.connectionOpened(this, connection);
        }
    }

    /**
     * Check that the client SHOULD NOT make more simultaneous requests than specified
     * by the 'requests' attribute in the connection manager's Session Creation Response.
     * However the client MAY make one additional request if it is to pause or terminate a session.
     *
     * @see <a href="http://www.xmpp.org/extensions/xep-0124.html#overactive">overactive</a>.
     * @param isPoll true if the session is using polling.
     * @throws HttpBindException if the connection has violated a facet of the HTTP binding
     *         protocol.
     */
    private void checkOveractivity(boolean isPoll) throws HttpBindException {
    	int pendingConnections = 0;
    	boolean overactivity = false;
    	String errorMessage = "Overactivity detected";

        for (HttpConnection conn : connectionQueue) {
            if (!conn.isClosed()) {
                pendingConnections++;
            }
        }

        if(pendingConnections >= maxRequests) {
        	overactivity = true;
        	errorMessage += ", too many simultaneous requests.";
        }
        else if(isPoll) {
	    	long time = System.currentTimeMillis();
	        if (time - lastPoll < maxPollingInterval * JiveConstants.SECOND) {
	        	if(isPollingSession()) {
	        		overactivity = lastResponseEmpty;
	        	}
	        	else {
	        		overactivity = (pendingConnections >= maxRequests - 1);
	        	}
	        }
	        errorMessage += ", minimum polling interval is "
	        	+ maxPollingInterval + ", current interval " + ((time - lastPoll) / 1000);
	        lastPoll = time;
        }
        setLastResponseEmpty(false);

        if(overactivity) {
        	Log.debug(errorMessage);
            if (!JiveGlobals.getBooleanProperty("xmpp.httpbind.client.requests.ignoreOveractivity", false)) {
                throw new HttpBindException(errorMessage, BoshBindingError.policyViolation);
            }
        }
    }

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
        deliver(new Deliverable(Arrays.asList(stanza)));
    }

    private synchronized void deliver(Deliverable stanza) {
        Collection<Deliverable> deliverable = Arrays.asList(stanza);
        boolean delivered = false;
        for (HttpConnection connection : connectionQueue) {
            try {
                if (connection.getRequestId() == lastRequestID + 1) {
                    lastRequestID = connection.getRequestId();
                    deliver(connection, deliverable);
                    delivered = true;
                    break;
                }
            }
            catch (HttpConnectionClosedException e) {
                /* Connection was closed, try the next one */
            }
        }

        if (!delivered) {
            pendingElements.add(stanza);
        }
    }

    private void fireConnectionClosed(HttpConnection connection) {
        lastActivity = System.currentTimeMillis();
        for (SessionListener listener : listeners) {
            listener.connectionClosed(this, connection);
        }
    }

    private String createDeliverable(Collection<Deliverable> elements) {
        StringBuilder builder = new StringBuilder();
        builder.append("<body xmlns='" + "http://jabber.org/protocol/httpbind" + "'");

        long ack = getLastAcknowledged();
        if(ack > lastRequestID)
            builder.append(" ack='").append(ack).append("'");

        builder.append(">");

        setLastResponseEmpty(elements.size() == 0);
        for (Deliverable child : elements) {
            builder.append(child.getDeliverable());
        }
        builder.append("</body>");
        return builder.toString();
    }

    private synchronized void closeConnection() {
        if (isClosed) {
            return;
        }
        isClosed = true;

        if (pendingElements.size() > 0) {
            failDelivery();
        }

        for (SessionListener listener : listeners) {
            listener.sessionClosed(this);
        }
        this.listeners.clear();
    }

    private void failDelivery() {
        for (Deliverable deliverable : pendingElements) {
            Collection<Element> packet = deliverable.getPackets();
            if (packet != null) {
                failDelivery(packet);
            }
        }

        for (HttpConnection toClose : connectionQueue) {
            if (!toClose.isDelivered()) {
                Delivered delivered = retrieveDeliverable(toClose.getRequestId());
                if (delivered != null) {
                    failDelivery(delivered.getPackets());
                }
                else {
                    Log.warn("Packets could not be found for session " + getStreamID() + " cannot" +
                            "be delivered to client");
                }
            }
            toClose.close();
            fireConnectionClosed(toClose);
        }
        pendingElements.clear();
    }

    private void failDelivery(Collection<Element> packets) {
        if (packets == null) {
            // Do nothing if someone asked to deliver nothing :)
            return;
        }
        for (Element packet : packets) {
            // Inform the server that the wrapped stanza was not delivered
            String tag = packet.getName();
            if ("message".equals(tag)) {
                connectionManager.getServerSurrogate().deliveryFailed(packet, getStreamID());
            }
            else if ("iq".equals(tag)) {
                String type = packet.attributeValue("type", "get");
                if ("get".equals(type) || "set".equals(type)) {
                    // Build IQ of type ERROR
                    Element reply = packet.createCopy();
                    reply.addAttribute("type", "error");
                    reply.addAttribute("from", packet.attributeValue("to"));
                    reply.addAttribute("to", packet.attributeValue("from"));
                    Element error = reply.addElement("error");
                    error.addAttribute("type", "wait");
                    error.addElement("unexpected-request")
                            .addAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-stanzas");
                    // Bounce the failed IQ packet
                    connectionManager.getServerSurrogate().send(reply.asXML(), getStreamID());
                }
            }
        }
    }

    private String createEmptyBody() {
        Element body = DocumentHelper.createElement("body");
        body.addNamespace("", "http://jabber.org/protocol/httpbind");
        long ack = getLastAcknowledged();
        if(ack > lastRequestID)
        	body.addAttribute("ack", String.valueOf(ack));
        return body.asXML();
    }

    private class Deliverable implements Comparable<Deliverable> {
        private final String text;
        private final Collection<String> packets;
        private long requestID;

        public Deliverable(String text) {
            this.text = text;
            this.packets = null;
        }

        public Deliverable(Collection<Element> elements) {
            this.text = null;
            this.packets = new ArrayList<String>();
            for (Element packet : elements) {
                this.packets.add(packet.asXML());
            }
        }

        public String getDeliverable() {
            if (text == null) {
                StringBuilder builder = new StringBuilder();
                for (String packet : packets) {
                    builder.append(packet);
                }
                return builder.toString();
            }
            else {
                return text;
            }
        }

        public void setRequestID(long requestID) {
            this.requestID = requestID;
        }

        public long getRequestID() {
            return requestID;
        }

        public Collection<Element> getPackets() {
            List<Element> answer = new ArrayList<Element>();
            for (String packetXML : packets) {
                try {
                    // Parse the XML stanza
                    Element element = localParser.get().read(new StringReader(packetXML)).getRootElement();
                    answer.add(element);
                }
                catch (Exception e) {
                    Log.error("Error while parsing Privacy Property", e);
                }
            }
            return answer;
        }

        public int compareTo(Deliverable o) {
            return (int) (o.getRequestID() - requestID);
        }
    }

    private class Delivered {
        private long requestID;
        private Collection<Deliverable> deliverables;

        public Delivered(Collection<Deliverable> deliverables) {
            this.deliverables = deliverables;
        }

        public void setRequestID(long requestID) {
            this.requestID = requestID;
        }

        public long getRequestID() {
            return requestID;
        }

        public Collection<Element> getPackets() {
            List<Element> packets = new ArrayList<Element>();
            for (Deliverable deliverable : deliverables) {
                if (deliverable.packets != null) {
                    packets.addAll(deliverable.getPackets());
                }
            }
            return packets;
        }
    }
}
