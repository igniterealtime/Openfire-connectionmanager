/**
 * $RCSfile:  $
 * $Revision:  $
 * $Date:  $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 * This software is the proprietary information of Jive Software. Use is subject to license terms.
 */
package org.jivesoftware.multiplexer.net.http;

import org.jivesoftware.multiplexer.Session;
import org.jivesoftware.multiplexer.Connection;

/**
 *
 */
public class HttpSession extends Session {
    /**
     * Creates a session with an underlying connection and permission protection.
     *
     * @param connection The connection we are proxying
     */
    public HttpSession(String serverName, Connection connection, String streamID) {
        super(serverName, connection, streamID);
    }

    public String getAvailableStreamFeatures() {
        return null;
    }

    public void close() {
    }
}
