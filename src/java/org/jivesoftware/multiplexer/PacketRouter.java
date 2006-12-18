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
