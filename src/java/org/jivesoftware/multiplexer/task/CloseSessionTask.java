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

import org.jivesoftware.multiplexer.ConnectionWorkerThread;

/**
 * Task that notifies the server that a client session has been closed. This task
 * is executed right after clients send their end of stream element or if clients
 * connections are lost.
 *
 * @author Gaston Dombiak
 */
public class CloseSessionTask extends ClientTask {

    public CloseSessionTask(String streamID) {
        super(streamID);
    }

    public void run() {
        ConnectionWorkerThread workerThread = (ConnectionWorkerThread) Thread.currentThread();
        workerThread.clientSessionClosed(streamID);
    }

    public void serverNotAvailable() {
        // Do nothing;
    }
}