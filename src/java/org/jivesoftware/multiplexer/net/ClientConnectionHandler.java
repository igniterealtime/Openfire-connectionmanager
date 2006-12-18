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

package org.jivesoftware.multiplexer.net;

import org.apache.mina.common.IoSession;
import org.jivesoftware.multiplexer.spi.ClientFailoverDeliverer;
import org.jivesoftware.util.JiveGlobals;
import org.xmlpull.v1.XmlPullParserException;

/**
 * ConnectionHandler that knows which subclass of {@link StanzaHandler} should
 * be created and how to build and configure a {@link NIOConnection}.
 *
 * @author Gaston Dombiak
 */
public class ClientConnectionHandler extends ConnectionHandler {

    StanzaHandler createStanzaHandler(NIOConnection connection) throws XmlPullParserException {
        return new ClientStanzaHandler(router, serverName, connection);
    }

    NIOConnection createNIOConnection(IoSession session) {
        return new NIOConnection(session, new ClientFailoverDeliverer());
    }

    int getMaxIdleTime() {
        // Return 30 minuntes
        return JiveGlobals.getIntProperty("xmpp.client.idle", 30 * 60 * 1000) / 1000;
    }
}
