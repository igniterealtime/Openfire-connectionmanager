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

    @Override
	String getNamespace() {
        return "jabber:client";
    }

    @Override
	boolean validateHost() {
        return JiveGlobals.getBooleanProperty("xmpp.client.validate.host",false);
    }

    @Override
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
