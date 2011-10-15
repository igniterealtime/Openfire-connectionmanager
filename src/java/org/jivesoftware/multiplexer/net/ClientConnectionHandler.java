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

    @Override
	StanzaHandler createStanzaHandler(NIOConnection connection) throws XmlPullParserException {
        return new ClientStanzaHandler(router, serverName, connection);
    }

    @Override
	NIOConnection createNIOConnection(IoSession session) {
        return new NIOConnection(session, new ClientFailoverDeliverer());
    }

    @Override
	int getMaxIdleTime() {
        // Return 30 minuntes
        return JiveGlobals.getIntProperty("xmpp.client.idle", 30 * 60 * 1000) / 1000;
    }
}
