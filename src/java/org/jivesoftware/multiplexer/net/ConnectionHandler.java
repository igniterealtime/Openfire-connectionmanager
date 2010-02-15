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

import org.apache.mina.common.IdleStatus;
import org.apache.mina.common.IoHandlerAdapter;
import org.apache.mina.common.IoSession;
import org.apache.mina.filter.codec.ProtocolDecoderException;
import org.jivesoftware.multiplexer.Connection;
import org.jivesoftware.multiplexer.ConnectionManager;
import org.jivesoftware.multiplexer.PacketRouter;
import org.jivesoftware.multiplexer.spi.ServerRouter;
import org.jivesoftware.util.Log;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A ConnectionHandler is responsible for creating new sessions, destroying sessions and delivering
 * received XML stanzas to the proper StanzaHandler.
 *
 * @author Gaston Dombiak
 */
public abstract class ConnectionHandler extends IoHandlerAdapter {

    /**
     * The utf-8 charset for decoding and encoding Jabber packet streams.
     */
    static final String CHARSET = "UTF-8";
    static final String XML_PARSER = "XML-PARSER";
    private static final String HANDLER = "HANDLER";
    private static final String CONNECTION = "CONNECTION";

    protected static PacketRouter router = new ServerRouter();
    protected static String serverName = ConnectionManager.getInstance().getServerName();
    private static Map<Integer, XmlPullParser> parsers = new ConcurrentHashMap<Integer, XmlPullParser>();
    /**
     * Reuse the same factory for all the connections.
     */
    private static XmlPullParserFactory factory = null;

    static {
        try {
            factory = XmlPullParserFactory.newInstance(MXParser.class.getName(), null);
            factory.setNamespaceAware(true);
        }
        catch (XmlPullParserException e) {
            Log.error("Error creating a parser factory", e);
        }
    }

    @Override
	public void sessionOpened(IoSession session) throws Exception {
        // Create a new XML parser for the new connection. The parser will be used by the XMPPDecoder filter.
        XMLLightweightParser parser = new XMLLightweightParser(CHARSET);
        session.setAttribute(XML_PARSER, parser);
        // Create a new NIOConnection for the new session
        NIOConnection connection = createNIOConnection(session);
        session.setAttribute(CONNECTION, connection);
        session.setAttribute(HANDLER, createStanzaHandler(connection));
        // Set the max time a connection can be idle before closing it
        int idleTime = getMaxIdleTime();
        if (idleTime > 0) {
            session.setIdleTime(IdleStatus.READER_IDLE, idleTime);
        }
    }

    @Override
	public void sessionClosed(IoSession session) throws Exception {
        // Get the connection for this session
        Connection connection = (Connection) session.getAttribute(CONNECTION);
        // Inform the connection that it was closed
        connection.close();
    }

    @Override
	public void sessionIdle(IoSession session, IdleStatus status) throws Exception {
        // Get the connection for this session
        Connection connection = (Connection) session.getAttribute(CONNECTION);
        // Close idle connection
        if (Log.isDebugEnabled()) {
            Log.debug("Closing connection that has been idle: " + connection);
        }
        connection.close();
    }

    @Override
	public void exceptionCaught(IoSession session, Throwable cause) throws Exception {
        if (cause instanceof IOException) {
            // TODO Verify if there were packets pending to be sent and decide what to do with them
            Log.debug(cause);
        }
        else if (cause instanceof ProtocolDecoderException) {
            Log.warn("Closing session due to exception: " + session, cause);
            session.close();
        }
        else {
            Log.error(cause);
        }
    }

    @Override
	public void messageReceived(IoSession session, Object message) throws Exception {
        //System.out.println("RCVD: " + message);
        // Get the stanza handler for this session
        StanzaHandler handler = (StanzaHandler) session.getAttribute(HANDLER);
        // Get the parser to use to process stanza. For optimization there is going
        // to be a parser for each running thread. Each Filter will be executed
        // by the Executor placed as the first Filter. So we can have a parser associated
        // to each Thread
        int hashCode = Thread.currentThread().hashCode();
        XmlPullParser parser = parsers.get(hashCode);
        if (parser == null) {
            parser = factory.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
            parsers.put(hashCode, parser);
        }

        // Let the stanza handler process the received stanza
        try {
            handler.process( (String) message, parser);
        } catch (Exception e) {
            Log.error("Closing connection due to error while processing message: " + message, e);
            Connection connection = (Connection) session.getAttribute(CONNECTION);
            connection.close();
        }
    }

    abstract NIOConnection createNIOConnection(IoSession session);

    abstract StanzaHandler createStanzaHandler(NIOConnection connection) throws XmlPullParserException;

    /**
     * Returns the max number of seconds a connection can be idle (both ways) before
     * being closed.<p>
     *
     * @return the max number of seconds a connection can be idle.
     */
    abstract int getMaxIdleTime();
}
