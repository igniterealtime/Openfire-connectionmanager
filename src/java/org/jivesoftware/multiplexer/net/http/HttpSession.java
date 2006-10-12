/**
 * $RCSfile:  $
 * $Revision:  $
 * $Date:  $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 * This software is the proprietary information of Jive Software. Use is subject to license terms.
 */
package org.jivesoftware.multiplexer.net.http;

import org.jivesoftware.multiplexer.*;
import org.dom4j.Element;
import org.dom4j.DocumentHelper;

import java.util.*;

/**
 *
 */
public class HttpSession extends Session {
    private int wait;
    private int hold = -1000;
    private String language;
    private final Queue<HttpConnection> connectionQueue = new LinkedList<HttpConnection>();
    private final List<Element> pendingElements = new ArrayList<Element>();


    public HttpSession(String serverName, String streamID) {
        super(serverName, null, streamID);
    }

    void addConnection(HttpConnection connection, boolean isPoll) {
        if(connection == null) {
            throw new IllegalArgumentException("Connection cannot be null.");
        }

        connection.setSession(this);
        if(pendingElements.size() > 0) {
            createDeliverable(pendingElements);
            pendingElements.clear();
            return;
        }
        // With this connection we need to check if we will have too many connections open, closing
        // any extras.
        while(hold > 0 && connectionQueue.size() >= hold) {
            HttpConnection toClose = connectionQueue.remove();
            toClose.close();
        }
        connectionQueue.offer(connection);
    }

    public String getAvailableStreamFeatures() {
        return null;
    }

    public void close() {
    }

    public void close(boolean isServerShuttingDown) {
    }

    public synchronized void deliver(Element stanza) {
        String deliverable = createDeliverable(Arrays.asList(stanza));
        boolean delivered = false;
        while(!delivered && connectionQueue.size() > 0) {
            HttpConnection connection = connectionQueue.remove();
            try {
                connection.deliverBody(deliverable);
                delivered = true;
            }
            catch (HttpConnectionClosedException e) {
                /* Connection was closed, try the next one */
            }
        }

        if(!delivered) {
            pendingElements.add(stanza);
        }
    }

    private String createDeliverable(Collection<Element> elements) {
        Element body = DocumentHelper.createElement("body");
        body.addAttribute("xmlns", "http://jabber.org/protocol/httpbind");
        for(Element child : elements) {
            body.add(child);
        }
        return body.asXML();
    }

    /**
     * This attribute specifies the longest time (in seconds) that the connection manager is allowed
     * to wait before responding to any request during the session. This enables the client to
     * prevent its TCP connection from expiring due to inactivity, as well as to limit the delay
     * before it discovers any network failure.
     *
     * @param wait the longest time it is permissible to wait for a response.
     */
    public void setWait(int wait) {
        this.wait = wait;
    }

    /**
     * This attribute specifies the longest time (in seconds) that the connection manager is allowed
     * to wait before responding to any request during the session. This enables the client to
     * prevent its TCP connection from expiring due to inactivity, as well as to limit the delay
     * before it discovers any network failure.
     *
     * @return the longest time it is permissible to wait for a response.
     */
    public int getWait() {
        return wait;
    }

    /**
     * This attribute specifies the maximum number of requests the connection manager is allowed
     * to keep waiting at any one time during the session. (For example, if a constrained client
     * is unable to keep open more than two HTTP connections to the same HTTP server simultaneously,
     * then it SHOULD specify a value of "1".)
     *
     * @param hold the maximum number of simultaneous waiting requests.
     *
     */
    public void setHold(int hold) {
        this.hold = hold;
    }

    /**
     * This attribute specifies the maximum number of requests the connection manager is allowed
     * to keep waiting at any one time during the session. (For example, if a constrained client
     * is unable to keep open more than two HTTP connections to the same HTTP server simultaneously,
     * then it SHOULD specify a value of "1".)
     *
     * @return the maximum number of simultaneous waiting requests
     */
    public int getHold() {
        return hold;
    }

    public void setLanaguage(String language) {
        this.language = language;
    }

    public String getLanguage() {
        return language;
    }

    /**
     * Sets the max interval within which a client can send polling requests. If more than one
     * @param pollingInterval
     */
    public void setMaxPollingInterval(int pollingInterval) {
    }
}
