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

import org.dom4j.Element;
import org.jivesoftware.multiplexer.net.SocketConnection;
import org.jivesoftware.util.Log;

/**
 * A ServerPacketHandler is responsible for handling stanzas sent from the server. For each
 * server connection there is going to be an instance of this class.<p>
 *
 * Route stanzas are forwarded to clients. IQ stanzas are used when the server wants to
 * close a client connection or wants to update the clients connections configurations.
 * Stream errors with condition <tt>system-shutdown</tt> indicate that the server is
 * shutting down. The connection manager will close existing client connections but
 * will keep running.
 *
 * @author Gaston Dombiak
 */
class ServerPacketHandler {

    private ConnectionManager connectionManager = ConnectionManager.getInstance();

    /**
     * Connection to the server.
     */
    private SocketConnection connection;
    /**
     * JID that identifies this connection to the server. The address is composed by
     * the connection manager name and the name of the thread. e.g.: connManager1/thread1
     */
    private String jidAddress;

    public ServerPacketHandler(SocketConnection connection, String jidAddress) {
        this.connection = connection;
        this.jidAddress = jidAddress;
    }

    /**
     * Handles stanza sent from the server. Route stanzas are forwarded to clients. IQ
     * stanzas are used when the server wants to close a client connection or wants to
     * update the clients connections configurations. Stream errors with condition
     * <tt>system-shutdown</tt> indicate that the server is shutting down. The connection
     * manager will close existing client connections but will keep running.
     *
     * @param stanza stanza sent from the server.
     */
    public void handle(Element stanza) {
        String tag = stanza.getName();
        if ("route".equals(tag)) {
            // Process wrapped packets
            processRoute(stanza);
        }
        else if ("iq".equals(tag)) {
            String type = stanza.attributeValue("type");
            if ("set".equals(type)) {
                Element wrapper = stanza.element("session");
                if (wrapper != null) {
                    String streamID = wrapper.attributeValue("id");
                    // Check if the server is informing us that we need to close a session
                    if (wrapper.element("close") != null) {
                        // Get the session that matches the requested stream ID
                        Session session = Session.getSession(streamID);
                        if (session != null) {
                            session.close();
                        }
                    } else {
                        Log.warn("Invalid IQ stanza of type SET was received: " + stanza.asXML());
                    }
                } else {
                    Element configuration = stanza.element("configuration");
                    if (configuration != null) {
                        obtainClientOptions(stanza, configuration);
                    } else {
                        Log.warn("Invalid IQ stanza of type SET was received: " + stanza.asXML());
                    }
                }
            } else if ("result".equals(type)) {
                if (Log.isDebugEnabled()) {
                    Log.debug("IQ stanza of type RESULT was discarded: " + stanza.asXML());
                }
            } else if ("error".equals(type)) {
                // Close session if child element is CREATE
                Element wrapper = stanza.element("session");
                if (wrapper != null) {
                    String streamID = wrapper.attributeValue("id");
                    // Check if the server is informing us that we need to close a session
                    if (wrapper.element("create") != null) {
                        // Get the session that matches the requested stream ID
                        Session session = Session.getSession(streamID);
                        if (session != null) {
                            session.close();
                        }
                    } else {
                        if (Log.isDebugEnabled()) {
                            Log.debug("IQ stanza of type ERRROR was discarded: " + stanza.asXML());
                        }
                    }
                } else {
                    if (Log.isDebugEnabled()) {
                        Log.debug("IQ stanza of type ERRROR was discarded: " + stanza.asXML());
                    }
                }
            } else {
                if (Log.isDebugEnabled()) {
                    Log.debug("IQ stanza with invalid type was discarded: " + stanza.asXML());
                }
            }
        } else if ("error".equals(tag) && "stream".equals(stanza.getNamespacePrefix())) {
            if (stanza.element("system-shutdown") != null) {
                // Close connections to the server and client connections. The connection
                // manager will still be running and accepting client connections. New
                // connections to the server will be created on demand.
                connectionManager.getServerSurrogate().closeAll();
            } else {
                // Some stream error was sent from the server
                Log.warn("Server sent unexpected stream error: " + stanza.asXML());
            }
        } else {
            Log.warn("Unknown stanza type sent to Connection Manager: " + stanza.asXML());
        }
    }

    /**
     * Forwards wrapped stanza contained in the <tt>route</tt> element to the specified
     * client. The target client connection is specified in the <tt>route</tt> element by
     * the <tt>streamid</tt> attribute.<p>
     *
     * Wrapped stanzas that failed to be delivered to the target client are returned to
     * the server.
     *
     * @param route the route element containing the wrapped stanza to send to the target
     *        client.
     */
    private void processRoute(Element route) {
        String streamID = route.attributeValue("streamid");
        // Get the wrapped stanza
        Element stanza = (Element) route.elementIterator().next();
        // Get the session that matches the requested stream ID
        Session session = Session.getSession(streamID);
        if (session != null && !session.isClosed()) {
            // Deliver the wrapped stanza to the client
            session.deliver(stanza);
        }
        else {
            // Inform the server that the wrapped stanza was not delivered
            String tag = stanza.getName();
            if ("message".equals(tag)) {
                connectionManager.getServerSurrogate().deliveryFailed(stanza, streamID);
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
                    connectionManager.getServerSurrogate().send(reply.asXML(), streamID);
                }
            }
        }
    }

    /**
     * Processes server configuration to use for client connections and store the
     * configuration in {@link ServerSurrogate}.
     *
     * @param stanza stanza sent from the server containing the configuration.
     * @param configuration the configuration element contained in the stanza.
     */
    private void obtainClientOptions(Element stanza, Element configuration) {
        ServerSurrogate serverSurrogate = connectionManager.getServerSurrogate();
        // Check if TLS is avaiable (and if it is required)
        Element startTLS = configuration.element("starttls");
        if (startTLS != null) {
            if (startTLS.element("required") != null) {
                serverSurrogate.setTlsPolicy(Connection.TLSPolicy.required);
            } else {
                serverSurrogate.setTlsPolicy(Connection.TLSPolicy.optional);
            }
        } else {
            serverSurrogate.setTlsPolicy(Connection.TLSPolicy.disabled);
        }
        // Check if compression is available
        Element compression = configuration.element("compression");
        if (compression != null) {
            serverSurrogate.setCompressionPolicy(Connection.CompressionPolicy.optional);
        } else {
            serverSurrogate.setCompressionPolicy(Connection.CompressionPolicy.disabled);
        }
        // Cache supported SASL mechanisms for client authentication
        Element mechanisms = configuration.element("mechanisms");
        if (mechanisms != null) {
            serverSurrogate.setSASLMechanisms(mechanisms);
        }
        // Check if anonymous login is supported
        serverSurrogate.setNonSASLAuthEnabled(configuration.element("auth") != null);
        // Check if in-band registration is supported
        serverSurrogate.setInbandRegEnabled(configuration.element("register") != null);

        // Send ACK to the server
        Element reply = stanza.createCopy();
        reply.addAttribute("type", "result");
        reply.addAttribute("to", connectionManager.getServerName());
        reply.addAttribute("from", jidAddress);
        connection.deliver(reply.asXML());
    }
}

