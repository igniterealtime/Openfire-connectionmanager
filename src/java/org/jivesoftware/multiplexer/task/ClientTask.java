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
