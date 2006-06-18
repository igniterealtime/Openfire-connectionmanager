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

import org.jivesoftware.multiplexer.ConnectionManager;
import org.jivesoftware.multiplexer.PacketRouter;
import org.jivesoftware.multiplexer.ServerPort;
import org.jivesoftware.multiplexer.spi.ClientFailoverDeliverer;
import org.jivesoftware.multiplexer.spi.ServerRouter;
import org.jivesoftware.util.Log;

import java.io.IOException;
import java.net.Socket;

/**
 * Factory of {@link SocketReader}. Currently only socket readers for clients are
 * supported.
 *
 * @author Gaston Dombiak
 */
class SocketReaderFactory {

    private static PacketRouter router = new ServerRouter();
    private static String serverName = ConnectionManager.getInstance().getServerName();

    static SocketReader createSocketReader(Socket sock, boolean isSecure, ServerPort serverPort,
                                           boolean useBlockingMode) throws IOException {
        if (serverPort.isClientPort()) {
            SocketConnection conn =
                    new SocketConnection(new ClientFailoverDeliverer(), sock, isSecure);
            return new ClientSocketReader(router, serverName, sock, conn, useBlockingMode);
        } else {
            Log.warn("Invalid socket reader was requested. Only clients are allowed to connect,");
            return null;
        }
    }


}
