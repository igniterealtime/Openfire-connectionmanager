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

import org.dom4j.Element;
import org.jivesoftware.multiplexer.ConnectionManager;
import org.jivesoftware.multiplexer.PacketDeliverer;
import org.jivesoftware.multiplexer.ServerSurrogate;

/**
 * Deliverer to use when a stanza received from the server failed to be forwarded
 * to a client. The deliverer will inform the server of the failed operation.
 *
 * @author Gaston Dombiak
 */
public class ClientFailoverDeliverer implements PacketDeliverer {

    private ServerSurrogate serverSurrogate = ConnectionManager.getInstance().getServerSurrogate();
    private String streamID;

    public void setStreamID(String streamID) {
        this.streamID = streamID;
    }

    public void deliver(Element stanza) {
        // Inform the server that the wrapped stanza was not delivered
        String tag = stanza.getName();
        if ("message".equals(tag)) {
            serverSurrogate.deliveryFailed(stanza, streamID);
        }
        else if ("iq".equals(tag)) {
            String type = stanza.attributeValue("type", "get");
            if ("get".equals(type) || "set".equals(type)) {
                // Build IQ of type ERROR
                Element reply = stanza.createCopy();
                reply.addAttribute("type", "error");
                reply.addAttribute("from", stanza.attributeValue("to"));
                reply.addAttribute("to", stanza.attributeValue("from"));
                Element error = reply.addElement("error");
                error.addAttribute("type", "wait");
                error.addElement("unexpected-request")
                        .addAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-stanzas");
                // Bounce the failed IQ packet
                serverSurrogate.send(reply.asXML(), streamID);
            }
        }
    }
}
