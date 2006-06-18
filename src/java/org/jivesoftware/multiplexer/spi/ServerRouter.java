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

package org.jivesoftware.multiplexer.spi;

import org.jivesoftware.multiplexer.PacketRouter;
import org.jivesoftware.multiplexer.ConnectionManager;
import org.jivesoftware.multiplexer.ServerSurrogate;
import org.dom4j.Element;

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

    public void route(Element stanza, String streamID) {
        serverSurrogate.send(stanza, streamID);
    }
}
