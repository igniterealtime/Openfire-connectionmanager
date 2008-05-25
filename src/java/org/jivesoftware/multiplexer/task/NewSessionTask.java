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

import org.jivesoftware.multiplexer.ClientSession;
import org.jivesoftware.multiplexer.ConnectionWorkerThread;

import java.net.InetAddress;

/**
 * Task that notifies the server that a new client session has been created. This task
 * is executed right after clients send their initial stream header.
 *
 * @author Gaston Dombiak
 */
public class NewSessionTask extends ClientTask {

    private InetAddress address;

    public NewSessionTask(String streamID, InetAddress address) {
        super(streamID);
        this.address = address;
    }

    public void run() {
        ConnectionWorkerThread workerThread = (ConnectionWorkerThread) Thread.currentThread();
        workerThread.clientSessionCreated(streamID, address);
    }

    public void serverNotAvailable() {
        // Close client session indicating that the server is not available
        ClientSession.getSession(streamID).close(true);
    }
}
