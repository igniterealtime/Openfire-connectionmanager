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
import org.jivesoftware.multiplexer.task.CloseSessionTask;
import org.jivesoftware.multiplexer.task.DeliveryFailedTask;
import org.jivesoftware.multiplexer.task.NewSessionTask;
import org.jivesoftware.multiplexer.task.RouteTask;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Surrogate of the main server where the Connection Manager is routing client
 * packets. This class is responsible for keeping a pool of working threads to
 * processing incoming clients traffic and forward it to the main server. Each working
 * thread uses its own connection to the server. By default 5 threads/connections
 * are established to the server. Use the system property <tt>xmpp.manager.connections</tt>
 * to modify the default value.<p>
 *
 * ServerSurrogate is also responsible for caching the server configuration such as if
 * non-sasl authentication or in-band registration are available.<p>
 *
 * Each connection to the server has its own {@link ServerPacketReader} to read incoming
 * traffic from the server. Incoming server traffic is then handled by
 * {@link ServerPacketHandler}.
 *
 * @author Gaston Dombiak
 */
public class ServerSurrogate {

    /**
     * TLS policy to use for clients.
     */
    private Connection.TLSPolicy tlsPolicy = Connection.TLSPolicy.optional;

    /**
     * Compression policy to use for clients.
     */
    private Connection.CompressionPolicy compressionPolicy = Connection.CompressionPolicy.disabled;

    /**
     * Cache the SASL mechanisms supported by the server for client authentication
     */
    private Element saslMechanisms;
    /**
     * Flag indicating if non-sasl authentication is supported by the server.
     */
    private boolean nonSASLEnabled;
    /**
     * Flag indicating if in-band registration is supported by the server.
     */
    private boolean inbandRegEnabled;

    /**
     * Pool of threads that will send stanzas to the server. The number of threads
     * in the pool will match the number of connections to the server.
     */
    private ThreadPoolExecutor threadPool;
    /**
     * Map that holds the list of connections to the server.
     * Key: thread name, Value: ConnectionWorkerThread.
     */
    Map<String, ConnectionWorkerThread> serverConnections =
            new ConcurrentHashMap<String, ConnectionWorkerThread>(0);

    ServerSurrogate() {
    }

    void start() {
        // Create empty thread pool
        createThreadPool();
        // Populate thread pool with threads that will include connections to the server
        threadPool.prestartAllCoreThreads();
        // Start thread that will send heartbeats to the server every 30 seconds
        // to keep connections to the server open.
        Thread hearbeatThread = new Thread() {
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(30000);
                        for (ConnectionWorkerThread thread : serverConnections.values()) {
                            thread.getConnection().deliverRawText(" ");
                        }
                    }
                    catch (InterruptedException e) {
                        // Do nothing
                    }
                    catch(Exception e) {
                        Log.error(e);
                    }
                }
            }
        };
        hearbeatThread.setDaemon(true);
        hearbeatThread.setPriority(Thread.NORM_PRIORITY);
        hearbeatThread.start();
    }

    /**
     * Closes existing connections to the server. A new thread pool will be created
     * but no connections will be created.  New connections will be created on demand.
     */
    void closeAll() {
        shutdown(true);
        // Create new thread pool but this time do not populate it
        createThreadPool();
    }

    /**
     * Closes connections of connected clients and stops forwarding clients traffic to
     * the server. If the server is the one that requested to stop forwarding traffic
     * then stop doing it now. This means that queued packets will be discarded, otherwise
     * stop queuing packet but continue processing queued packets.
     *
     * @param now true if forwarding packets should be done now.
     */
    void shutdown(boolean now) {
        // Disconnect connected clients
        ClientSession.closeAll();
        // Shutdown the threads that send stanzas to the server
        if (now) {
            threadPool.shutdownNow();
        }
        else {
            threadPool.shutdown();
        }
    }

    /**
     * Notification message indication that a new client session has been created. Send
     * a notification to the main server.
     *
     * @param streamID the stream ID assigned by the connection manager to the new session.
     */
    public void clientSessionCreated(final String streamID) {
        threadPool.execute(new NewSessionTask(streamID));
    }

    /**
     * Notification message indication that a client session has been closed. Send
     * a notification to the main server.
     *
     * @param streamID the stream ID assigned by the connection manager to the session.
     */
    public void clientSessionClosed(final String streamID) {
        threadPool.execute(new CloseSessionTask(streamID));
    }

    /**
     * Notification message indicating that delivery of a stanza to a client has
     * failed.
     *
     * @param stanza the stanza that was not sent to the client.
     * @param streamID the stream ID assigned by the connection manager to the no
     *        longer available session.
     */
    public void deliveryFailed(Element stanza, String streamID) {
        threadPool.execute(new DeliveryFailedTask(streamID, stanza));
    }

    /**
     * Forwards the specified stanza to the server. The client that is sending the
     * stanza is specified by the streamID parameter.
     *
     * @param stanza the stanza to send to the server.
     * @param streamID the stream ID assigned by the connection manager to the session.
     */
    public void send(Element stanza, String streamID) {
        threadPool.execute(new RouteTask(streamID, stanza));
    }

    /**
     * Returns the SASL mechanisms supported by the server for client authentication.
     *
     * @param session the session connecting to the connection manager.
     * @return the SASL mechanisms supported by the server for client authentication.
     */
    public String getSASLMechanisms(Session session) {
        return saslMechanisms.asXML();
    }

    public Element getSASLMechanismsElement(Session session) {
        return saslMechanisms;
    }

    /**
     * Returns the SASL mechanisms supported by the server for client authentication.
     *
     * @param mechanisms the SASL mechanisms supported by the server for client authentication.
     */
    public void setSASLMechanisms(Element mechanisms) {
        saslMechanisms = mechanisms.createCopy();
    }

    /**
     * Returns whether TLS is mandatory, optional or is disabled. When TLS is mandatory clients
     * are required to secure their connections or otherwise their connections will be closed.
     * On the other hand, when TLS is disabled clients are not allowed to secure their connections
     * using TLS. Their connections will be closed if they try to secure the connection. in this
     * last case.
     *
     * @return whether TLS is mandatory, optional or is disabled.
     */
    public Connection.TLSPolicy getTlsPolicy() {
        return tlsPolicy;
    }

    /**
     * Sets whether TLS is mandatory, optional or is disabled. When TLS is mandatory clients
     * are required to secure their connections or otherwise their connections will be closed.
     * On the other hand, when TLS is disabled clients are not allowed to secure their connections
     * using TLS. Their connections will be closed if they try to secure the connection. in this
     * last case.
     *
     * @param tlsPolicy whether TLS is mandatory, optional or is disabled.
     */
    public void setTlsPolicy(Connection.TLSPolicy tlsPolicy) {
        this.tlsPolicy = tlsPolicy;
    }

    /**
     * Returns whether compression is optional or is disabled.
     *
     * @return whether compression is optional or is disabled.
     */
    public Connection.CompressionPolicy getCompressionPolicy() {
        return compressionPolicy;
    }

    /**
     * Sets whether compression is enabled or is disabled.<p>
     *
     * Note: Connection managers share the same code from Wildfire so the same compression
     * algorithms will be offered.
     * // TODO When used with other server we need to store the available algorithms.
     *
     * @param compressionPolicy whether Compression is enabled or is disabled.
     */
    public void setCompressionPolicy(Connection.CompressionPolicy compressionPolicy) {
        this.compressionPolicy = compressionPolicy;
    }

    /**
     * Returns true if non-sasl authentication is supported by the server.
     *
     * @return true if non-sasl authentication is supported by the server.
     */
    public boolean isNonSASLAuthEnabled() {
        return nonSASLEnabled;
    }

    /**
     * Sets if non-sasl authentication is supported by the server.
     *
     * @param nonSASLEnabled if non-sasl authentication is supported by the server.
     */
    public void setNonSASLAuthEnabled(boolean nonSASLEnabled) {
        this.nonSASLEnabled = nonSASLEnabled;
    }

    /**
     * Returns true if in-band registration is supported by the server.
     *
     * @return true if in-band registration is supported by the server.
     */
    public boolean isInbandRegEnabled() {
        return inbandRegEnabled;
    }

    /**
     * Sets if in-band registration is supported by the server.
     *
     * @param inbandRegEnabled if in-band registration is supported by the server.
     */
    public void setInbandRegEnabled(boolean inbandRegEnabled) {
        this.inbandRegEnabled = inbandRegEnabled;
    }

    /**
     * Creates a new thread pool that will not contain any thread. So new connections
     * won't be created to the server at this point.
     */
    private void createThreadPool() {
        int maxConnections = JiveGlobals.getIntProperty("xmpp.manager.connections", 5);
        // Create a pool of threads that will process queued packets.
        threadPool = new ConnectionWorkerThreadPool(maxConnections, maxConnections, 60,
                TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(),
                new ConnectionsWorkerFactory(), new ThreadPoolExecutor.CallerRunsPolicy());
    }

    /**
     * ThreadPoolExecutor that verifies connection status before executing a task. If
     * the connection is invalid then the worker thread will be dismissed and the task
     * will be injected into the pool again.
     */
    private class ConnectionWorkerThreadPool extends ThreadPoolExecutor {
        public ConnectionWorkerThreadPool(int corePoolSize, int maximumPoolSize, long keepAliveTime,
                                          TimeUnit unit,
                                          BlockingQueue<Runnable> workQueue,
                                          ThreadFactory threadFactory,
                                          RejectedExecutionHandler handler) {
            super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory,
                    handler);
        }

        protected void beforeExecute(Thread thread, Runnable task) {
            super.beforeExecute(thread, task);
            ConnectionWorkerThread workerThread = (ConnectionWorkerThread) thread;
            // Check that the worker thread is valid. This means that it has a valid connection
            // to the server
            if (!workerThread.isValid()) {
                // Request other thread to process the task. In fact, a new thread
                // will be created by the
                execute(task);
                // Throw an exception so that this worker is dismissed
                throw new IllegalStateException(
                        "There is no connection to the server or connection is lost.");
            }
        }

        public void shutdown() {
            // Notify the server that the connection manager is being shut down
            execute(new Runnable() {
                public void run() {
                    ConnectionWorkerThread thread = (ConnectionWorkerThread) Thread.currentThread();
                    thread.notifySystemShutdown();
                }
            });
            // Stop the workers and shutdown
            super.shutdown();
        }
    }

    /**
     * Factory of threads where is thread will create and keep its own connection
     * to the server. If creating new connections to the server failes 2 consecutive
     * times then existing client connections will be closed.
     */
    private class ConnectionsWorkerFactory implements ThreadFactory {
        final ThreadGroup group;
        final AtomicInteger threadNumber = new AtomicInteger(1);
        final AtomicInteger failedAttempts = new AtomicInteger(0);

        ConnectionsWorkerFactory() {
            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() :
                    Thread.currentThread().getThreadGroup();
        }

        public Thread newThread(Runnable r) {
            // Create new worker thread that will include a connection to the server
            ConnectionWorkerThread t = new ConnectionWorkerThread(group, r,
                    "Connection Worker - " + threadNumber.getAndIncrement(), 0);
            if (t.isDaemon())
                t.setDaemon(false);
            if (t.getPriority() != Thread.NORM_PRIORITY)
                t.setPriority(Thread.NORM_PRIORITY);
            // Return null if failed to create worker thread
            if (!t.isValid()) {
                int attempts = failedAttempts.incrementAndGet();
                if (attempts == 2 && serverConnections.size() == 0) {
                    // Server seems to be unavailable so close existing client connections
                    closeAll();
                    // Clean up the counter of failed attemps to create new connections
                    failedAttempts.set(0);
                }
                return null;
            }
            // Clean up the counter of failed attemps to create new connections
            failedAttempts.set(0);
            // Update number of available connections to the server
            serverConnections.put(t.getName(), t);
            return t;
        }
    }
}
