/**
 * $RCSfile:  $
 * $Revision:  $
 * $Date:  $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 * This software is the proprietary information of Jive Software. Use is subject to license terms.
 */
package org.jivesoftware.multiplexer.net.http;

import org.jivesoftware.multiplexer.Connection;


/**
 *
 */
public class HttpConnection {
    private Connection.CompressionPolicy compressionPolicy;
    private long requestId;
    private String body;
    private HttpSession session;

    public HttpConnection(long requestID) {
        this.requestId = requestID;
    }

    public boolean validate() {
        return false;
    }

    public void close() {
    }

    public void systemShutdown() {
    }

    public boolean isClosed() {
        return false;
    }

    public boolean isSecure() {
        return false;
    }

    public void deliverBody(String body) {
        this.body = body;
    }

    public String getDeliverable() {
        return body;
    }

    public boolean isCompressed() {
        return false;
    }

    public Connection.CompressionPolicy getCompressionPolicy() {
        return compressionPolicy;
    }

    public void setCompressionPolicy(Connection.CompressionPolicy compressionPolicy) {
        this.compressionPolicy = compressionPolicy;
    }

    public long getRequestId() {
        return requestId;
    }

    /**
     * Set the session that this connection belongs to.
     *
     * @param session the session that this connection belongs to.
     */
    void setSession(HttpSession session) {
        this.session = session;
    }

    /**
     * Returns the session that this connection belongs to.
     *
     * @return the session that this connection belongs to.
     */
    public HttpSession getSession() {
        return session;
    }
}
