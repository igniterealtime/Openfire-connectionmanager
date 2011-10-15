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

package org.jivesoftware.multiplexer.spi;

import org.jivesoftware.multiplexer.ConnectionManager;
import org.jivesoftware.multiplexer.PacketRouter;
import org.jivesoftware.multiplexer.ServerSurrogate;

/**
 * Packet router that will route all traffic to the server.
 *
 * @author Gaston Dombiak
 */
public class ServerRouter implements PacketRouter {

    private ServerSurrogate serverSurrogate;

    public ServerRouter() {
        serverSurrogate = ConnectionManager.getInstance().getServerSurrogate();
    }

    public void route(String stanza, String streamID) {
        serverSurrogate.send(stanza, streamID);
    }
}
