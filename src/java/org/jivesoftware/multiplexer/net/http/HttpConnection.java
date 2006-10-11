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
import org.dom4j.Element;

/**
 *
 */
public class HttpConnection {
    private Connection.CompressionPolicy compressionPolicy;
    private long requestId;

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

    public void deliver(Element doc) {
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
}
