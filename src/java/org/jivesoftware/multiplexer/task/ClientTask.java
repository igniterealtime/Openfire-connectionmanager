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

package org.jivesoftware.multiplexer.task;

/**
 * Base class for tasks that were requested by clients and that involves the server.
 * Example of tasks are: forwarding stanzas to the server or indicating the server that
 * a new client has connected.
 *
 * @author Gaston Dombiak
 */
public abstract class ClientTask implements Runnable {

    protected String streamID;

    protected ClientTask(String streamID) {
        this.streamID = streamID;
    }

    /**
     * Execute the corresponding action when the server is not available.
     */
    public abstract void serverNotAvailable();
}
