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
import org.jivesoftware.multiplexer.PacketRouter;
import org.jivesoftware.util.JiveGlobals;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.net.Socket;

/**
 * A SocketReader specialized for client connections. This reader will be used when the open
 * stream contains a jabber:client namespace. Received packet will have their FROM attribute
 * overriden to avoid spoofing.<p>
 *
 * By default the hostname specified in the stream header sent by clients will not be validated.
 * When validated the TO attribute of the stream header has to match the server name or a valid
 * subdomain. If the value of the 'to' attribute is not valid then a host-unknown error
 * will be returned. To enable the validation set the system property
 * <b>xmpp.client.validate.host</b> to true.<p>
 *
 * Stanzas that do not have a FROM attribute will be wrapped before forwarding them to the
 * server. The wrapping element will include the stream ID that uniquely identifies the client
 * in the server. The server will then be able to use the proper client session for processing
 * the stanza.
 *
 * @author Gaston Dombiak
 */
public class ClientSocketReader extends SocketReader {

    public ClientSocketReader(PacketRouter router, String serverName,
            Socket socket, SocketConnection connection, boolean useBlockingMode) {
        super(router, serverName, socket, connection, useBlockingMode);
    }

    boolean createSession(String namespace) throws XmlPullParserException,
            IOException {
        if ("jabber:client".equals(namespace)) {
            // The connected client is a regular client so create a ClientSession
            session = ClientSession.createSession(serverName, this, reader, connection);
            return true;
        }
        return false;
    }

    String getNamespace() {
        return "jabber:client";
    }

    String getName() {
        return "Client SR - " + hashCode();
    }

    boolean validateHost() {
        return JiveGlobals.getBooleanProperty("xmpp.client.validate.host",false);
    }
}
