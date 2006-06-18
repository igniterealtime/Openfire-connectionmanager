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
import org.jivesoftware.multiplexer.ClientSession;

/**
 * Task that notifies the server that a new client session has been created. This task
 * is executed right after clients send their initial stream header.
 *
 * @author Gaston Dombiak
 */
public class NewSessionTask extends ClientTask {

    public NewSessionTask(String streamID) {
        super(streamID);
    }

    public void run() {
        ConnectionWorkerThread workerThread = (ConnectionWorkerThread) Thread.currentThread();
        workerThread.clientSessionCreated(streamID);
    }

    public void serverNotAvailable() {
        // Close client session indicating that the server is not available
        ClientSession.getSession(streamID).close(true);
    }
}
