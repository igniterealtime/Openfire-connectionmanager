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

package org.jivesoftware.multiplexer.spi;

import org.dom4j.Element;
import org.jivesoftware.multiplexer.PacketDeliverer;
import org.jivesoftware.multiplexer.Session;

/**
 * Deliverer to use when a stanza received from a client failed to be forwarded
 * to the server. The deliverer will try to return it to the sender.
 *
 * @author Gaston Dombiak
 */
public class ServerFailoverDeliverer implements PacketDeliverer {

    public void deliver(Element stanza) {
        if ("route".equals(stanza.getName())) {
            // Inform the client that the stanza was not delivered to the server
            // Get the stream id that identifies the client that sent the stanza
            String streamID = stanza.attributeValue("streamid");
            // Get the wrapped stanza
            Element wrapped = (Element) stanza.elementIterator().next();
            String tag = wrapped.getName();
            if ("message".equals(tag) || "iq".equals(tag) || "presence".equals(tag)) {
                // Build ERROR bouncing packet
                Element reply = wrapped.createCopy();
                reply.addAttribute("type", "error");
                reply.addAttribute("from", wrapped.attributeValue("to"));
                reply.addAttribute("to", wrapped.attributeValue("from"));
                Element error = reply.addElement("error");
                error.addAttribute("type", "wait");
                error.addElement("internal-server-error")
                        .addAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-stanzas");
                // Get the session that matches the specified stream ID
                Session session = Session.getSession(streamID);
                if (session != null) {
                    // Bounce the failed packet
                    session.deliver(reply);
                }
            }
        }
    }
}
