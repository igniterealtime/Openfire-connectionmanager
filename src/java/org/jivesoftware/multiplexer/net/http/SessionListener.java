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

/**
 *
 */
public interface SessionListener {
    public void connectionOpened(Session session, HttpConnection connection);

    public void connectionClosed(Session session, HttpConnection connection);

    public void sessionClosed(Session session);
}
