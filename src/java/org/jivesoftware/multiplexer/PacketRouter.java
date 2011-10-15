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

package org.jivesoftware.multiplexer;

/**
 * A router that handles incoming packets. Packets will be routed to their
 * corresponding handler. A router is much like a forwarded with some logic
 * to figute out who is the target for each packet.
 *
 * @author Gaston Dombiak
 */
public interface PacketRouter {

    /**
     * Routes the given packet based on its type.
     *
     * @param stanza The stanza to route.
     * @param streamID The ID of the client's stream.
     */
    void route(String stanza, String streamID);
}
