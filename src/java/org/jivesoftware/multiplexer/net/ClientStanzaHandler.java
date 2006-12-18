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

import org.jivesoftware.multiplexer.ClientSession;
import org.jivesoftware.multiplexer.Connection;
import org.jivesoftware.multiplexer.PacketRouter;
import org.jivesoftware.util.JiveGlobals;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * Handler of XML stanzas sent by clients.
 *
 * @author Gaston Dombiak
 */
class ClientStanzaHandler extends StanzaHandler {

    public ClientStanzaHandler(PacketRouter router, String serverName, Connection connection)
            throws XmlPullParserException {
        super(router, serverName, connection);
    }

    String getNamespace() {
        return "jabber:client";
    }

    boolean validateHost() {
        return JiveGlobals.getBooleanProperty("xmpp.client.validate.host",false);
    }

    boolean createSession(String namespace, String serverName, XmlPullParser xpp, Connection connection)
            throws XmlPullParserException {
        if ("jabber:client".equals(namespace)) {
            // The connected client is a regular client so create a ClientSession
            session = ClientSession.createSession(serverName, xpp, connection);
            return true;
        }
        return false;
    }
}
