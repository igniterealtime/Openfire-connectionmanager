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

package org.jivesoftware.multiplexer;

import org.dom4j.Element;
import org.dom4j.io.XMPPPacketReader;
import org.jivesoftware.multiplexer.net.SocketConnection;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Reads and processes stanzas sent from the server. Each connection to the server will
 * have an instance of this class. Read packets will be processed using a thread pool.
 * By default, the thread pool will have 5 processing threads. Configure the property
 * <tt>xmpp.manager.incoming.threads</tt> to change the number of processing threads
 * per connection to the server. 
 *
 * @author Gaston Dombiak
 */
class ServerPacketReader implements SocketStatistic {

    private boolean open = true;
    private XMPPPacketReader reader = null;

    /**
     * Pool of threads that will process incoming stanzas from the server.
     */
    private ThreadPoolExecutor threadPool;
    /**
     * Actual object responsible for handling incoming traffic.
     */
    private ServerPacketHandler packetsHandler;

    public ServerPacketReader(XMPPPacketReader reader, SocketConnection connection,
                              String address) {
        this.reader = reader;
        packetsHandler = new ServerPacketHandler(connection, address);
        init();
    }

    private void init() {
        // Create a pool of threads that will process incoming packets.
        int maxThreads = JiveGlobals.getIntProperty("xmpp.manager.incoming.threads", 5);
        if (maxThreads < 1) {
            // Ensure that the max number of threads in the pool is at least 1
            maxThreads = 1;
        }
        threadPool =
                new ThreadPoolExecutor(maxThreads, maxThreads, 60, TimeUnit.SECONDS,
                        new LinkedBlockingQueue<Runnable>(),
                        new ThreadPoolExecutor.CallerRunsPolicy());

        // Create a thread that will read and store DOM Elements.
        Thread thread = new Thread("Server Packet Reader") {
            @Override
			public void run() {
                while (open) {
                    Element doc;
                    try {
                        doc = reader.parseDocument().getRootElement();

                        if (doc == null) {
                            // Stop reading the stream since the remote server has sent an end of
                            // stream element and probably closed the connection.
                            shutdown();
                        }
                        else {
                            // If this element belongs to a session, queue it so that it can
                            // be processed in the correct order.
                            Session session = getSession(doc);
                            if( session != null ) {
                                Queue<Element> sessionQueue = session.getStanzaQueue();
                                sessionQueue.add(doc);
                                threadPool.execute(new ProcessSessionQueueTask(packetsHandler,session));
                            } else {
                                // Queue task that process incoming stanzas not related to a specific streamID
                                threadPool.execute(new ProcessStanzaTask(packetsHandler, doc));
                            }
                        }
                    }
                    catch (IOException e) {
                        Log.debug("Finishing Incoming Server Stanzas Reader.", e);
                        shutdown();
                    }
                    catch (Exception e) {
                        Log.error("Finishing Incoming Server Stanzas Reader.", e);
                        shutdown();
                    }
                }
            }
        };
        thread.setDaemon(true);
        thread.start();
    }

    public long getLastActive() {
        return reader.getLastActive();
    }

    public void shutdown() {
        open = false;
        threadPool.shutdown();
    }

    /**
     * @param stanza The stanza to find the session for using the streamid or id attribute
     * @return the session associated with the given stanza, if any
     */
    private Session getSession(Element stanza) {
         if( "route".equals(stanza.getName())){
             String streamID = stanza.attributeValue("streamid");
             return(Session.getSession(streamID));
         } 
         else {
             Element wrapper = stanza.element("session");
             if( wrapper != null ) {
                 String streamID = wrapper.attributeValue("id");
                 return(Session.getSession(streamID));
             } else {
                 return(null);
             }
         }
    }

    /**
     * Task that processes incoming stanzas from the server.
     */
    private class ProcessStanzaTask implements Runnable {
        /**
         * Incoming stanza to process.
         */
        private Element stanza;
        /**
         * Actual object responsible for handling incoming traffic.
         */
        private ServerPacketHandler handler;

        public ProcessStanzaTask(ServerPacketHandler handler, Element stanza) {
            this.handler = handler;
            this.stanza = stanza;
        }

        public void run() {
            handler.handle(stanza);
        }
    }
    
    /**
     * Task that processes a Session's stanza queue. This guarantees
     * that stanzas are processed in the same order that they are received
     */
    private class ProcessSessionQueueTask implements Runnable {
        /**
         * The session
         */
        private final Session session;
        
        /**
         * Actual object responsible for handling incoming traffic.
         */
        private final ServerPacketHandler handler;
        
        public ProcessSessionQueueTask(ServerPacketHandler handler, Session session) {
            this.session = session;
            this.handler = handler;
        }
        
        /**
         * Process all the stanzas currently in the queue for this session
         */
        public void run() {
            // Synchronize on the session here to ensure that all stanzas
            // for a given session get processed in order. This can be sub-optimal
            // since we might block if multiple threads are processing stanzas
            // for the same client, but the handler.handle() call should be quick,
            // and correctness seems more important here.
            synchronized(session) {
                Element stanza = session.getStanzaQueue().poll();
                while( stanza != null ){
                    handler.handle(stanza);
                    stanza = session.getStanzaQueue().poll();
                }
            }
        }
    }
}
