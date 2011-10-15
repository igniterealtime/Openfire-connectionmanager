/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

    @Override
	public void serverNotAvailable() {
        // Do nothing;
    }
}