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

import org.dom4j.Document;
import org.dom4j.io.SAXReader;
import org.jivesoftware.util.*;
import org.jivesoftware.multiplexer.net.SocketAcceptThread;
import org.jivesoftware.multiplexer.net.SSLSocketAcceptThread;
import org.jivesoftware.multiplexer.net.SocketSendingTracker;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Connection managers handle connections of clients that want to connect to a server. Each
 * connection manager may have one or more connections to the target server. These connections
 * are shared amongst connected clients (i.e. multiplexed) thus reducing the load on the
 * server.<p>
 *
 * The only properties that needs to be configured during Connection Managers' setup are
 * <tt>xmpp.domain</tt> and <tt>xmpp.sharedSecret</tt>. The <tt>xmpp.domain</tt> property
 * defines the name of the target server that clients want to connect to. Clients are
 * redirected to a connection manager when trying to open a socket connection to the server.
 * This is typically done by configuring some local DNS server with a SRV record for the server
 * name that points to the connection manager address. More elaborated solutions may include a
 * load balancer in front of several connection managers. Since XMPP connections are state-full
 * and long-lived then the load balancer does not have to be configured with "sticky sessions".<p>
 *
 * The server and connection managers have to share a common secret so that the server can
 * let connection managers connect to the server and forward packets. Configure the
 * <tt>xmpp.sharedSecret</tt> property with the same shared secret that the server is using.<p>
 *
 * Each connection manager has to have a unique name that uniquely identifies it from other
 * connection managers. Use the property <tt>xmpp.manager.name</tt> to manually set a name. If this
 * property is not present then a random name will be created for the manager each time it is
 * started. Properties are stored in conf/manager.xml. There are several ways for locating this
 * file.
 * <ol>
 *  <li>Set the system property <b>managerHome</b> when starting up the server.
 *  <li>When running in standalone mode attempt to find it in [home]/conf/manager.xml.
 *  <li>Load the path from <b>manager_init.xml</b> which must be in the classpath.
 * </ol>
 *
 * By default connection managers will open five connections to the server. Configure the
 * <tt>xmpp.manager.connections</tt> property if you want to change the number of connections.
 *
 * @author Gaston Dombiak
 */
public class ConnectionManager {

    private static ConnectionManager instance;

    /**
     * Name of the connection manager. Each manager MUST have a unique name. The name will
     * be used when connecting to the server.
     */
    protected String name;
    /**
     * Name of the server to connect. This is the server where users actually want to
     * connect.
     */
    protected String serverName;
    protected Version version;
    protected Date startDate;
    protected Date stopDate;

    /**
     * Location of the home directory. All configuration files should be
     * located here.
     */
    private File managerHome;
    protected ClassLoader loader;

    /**
     * True if in setup mode
     */
    private boolean setupMode = true;

    private static final String STARTER_CLASSNAME =
            "org.jivesoftware.multiplexer.starter.ServerStarter";
    private static final String WRAPPER_CLASSNAME =
            "org.tanukisoftware.wrapper.WrapperManager";

    private ServerSurrogate serverSurrogate;
    private SocketAcceptThread socketThread;
    private SSLSocketAcceptThread sslSocketThread;

    /**
     * Returns a singleton instance of ConnectionManager.
     *
     * @return an instance.
     */
    public static ConnectionManager getInstance() {
        return instance;
    }

    /**
     * Creates a server and starts it.
     */
    public ConnectionManager() {
        // We may only have one instance of the server running on the JVM
        if (instance != null) {
            throw new IllegalStateException("A server is already running");
        }
        instance = this;
        start();
    }

    protected void initialize() throws FileNotFoundException {
        locateHome();
        name = JiveGlobals.getXMLProperty("xmpp.manager.name", StringUtils.randomString(5)).toLowerCase();
        serverName = JiveGlobals.getXMLProperty("xmpp.domain");

        version = new Version(3, 0, 0, Version.ReleaseStatus.Beta, -1);
        if (serverName != null) {
            setupMode = false;
        }
        else  {
            Log.warn(LocaleUtils.getLocalizedString("setup.no_server_name"));
            System.err.println(LocaleUtils.getLocalizedString("setup.no_server_name"));
        }

        if (isStandAlone()) {
            Runtime.getRuntime().addShutdownHook(new ShutdownHookThread());
        }

        loader = Thread.currentThread().getContextClassLoader();
    }

    /**
     * Finish the setup process. Because this method is meant to be called from inside
     * the Admin console plugin, it spawns its own thread to do the work so that the
     * class loader is correct.
     */
    public void finishSetup() {
        if (!setupMode) {
            return;
        }
        // Make sure that setup finished correctly.
        if ("true".equals(JiveGlobals.getXMLProperty("setup"))) {
            // Set the new server domain assigned during the setup process
            name = JiveGlobals.getXMLProperty("xmpp.manager.name", StringUtils.randomString(5))
                    .toLowerCase();
            serverName = JiveGlobals.getXMLProperty("xmpp.domain").toLowerCase();

            Thread finishSetup = new Thread() {
                public void run() {
                    try {
                        // Start modules
                        startModules();
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                        Log.error(e);
                        shutdownServer();
                    }
                }
            };
            // Use the correct class loader.
            finishSetup.setContextClassLoader(loader);
            finishSetup.start();
            // We can now safely indicate that setup has finished
            setupMode = false;
        }
    }

    public void start() {
        try {
            initialize();

            // If the server has already been setup then we can start all the server's modules
            if (!setupMode) {
                // Start modules
                startModules();
            }
            // Log that the server has been started
            List<String> params = new ArrayList<String>();
            params.add(version.getVersionString());
            params.add(JiveGlobals.formatDateTime(new Date()));
            String startupBanner = LocaleUtils.getLocalizedString("startup.name", params);
            Log.info(startupBanner);
            System.out.println(startupBanner);

            startDate = new Date();
            stopDate = null;
        }
        catch (Exception e) {
            e.printStackTrace();
            Log.error(e);
            System.out.println(LocaleUtils.getLocalizedString("startup.error"));
            shutdownServer();
        }
    }

    private void startModules() {
        serverSurrogate = new ServerSurrogate();
        serverSurrogate.start();
        String localIPAddress;
        // Setup port info
        try {
            localIPAddress = InetAddress.getLocalHost().getHostAddress();
        }
        catch (UnknownHostException e) {
            localIPAddress = "Unknown";
        }
        // Start process that checks health of socket connections
        SocketSendingTracker.getInstance().start();
        // Start the port listener for clients
        startClientListeners(localIPAddress);
        // Start the port listener for secured clients
        startClientSSLListeners(localIPAddress);
    }

    private void stopModules() {
        stopClientListeners();
        stopClientSSLListeners();
        // Stop process that checks health of socket connections
        SocketSendingTracker.getInstance().shutdown();
        // Stop service that forwards packets to the server
        if (serverSurrogate != null) {
            serverSurrogate.shutdown(false);
        }
    }

    private void startClientListeners(String localIPAddress) {
        // Start clients plain socket unless it's been disabled.
        int port = 5222;
        ServerPort serverPort = new ServerPort(port, serverName, localIPAddress,
                false, null, ServerPort.Type.client);
        try {
            socketThread = new SocketAcceptThread(serverPort);
            //socketThread.setDaemon(true);
            socketThread.setPriority(Thread.MAX_PRIORITY);
            socketThread.start();

            List<String> params = new ArrayList<String>();
            params.add(Integer.toString(socketThread.getPort()));
            Log.info(LocaleUtils.getLocalizedString("startup.plain", params));
        }
        catch (Exception e) {
            System.err.println("Error starting XMPP listener on port " + port + ": " +
                    e.getMessage());
            Log.error(LocaleUtils.getLocalizedString("admin.error.socket-setup"), e);
        }
    }

    private void stopClientListeners() {
        if (socketThread != null) {
            socketThread.shutdown();
            socketThread = null;
        }
    }

    private void startClientSSLListeners(String localIPAddress) {
        // Start clients SSL unless it's been disabled.
        int port = 5223;
        String algorithm = JiveGlobals.getXMLProperty("xmpp.socket.ssl.algorithm");
        if ("".equals(algorithm) || algorithm == null) {
            algorithm = "TLS";
        }
        ServerPort serverPort = new ServerPort(port, serverName, localIPAddress,
                true, algorithm, ServerPort.Type.client);
        try {
            sslSocketThread = new SSLSocketAcceptThread(serverPort);
            //sslSocketThread.setDaemon(true);
            sslSocketThread.setPriority(Thread.MAX_PRIORITY);
            sslSocketThread.start();

            List<String> params = new ArrayList<String>();
            params.add(Integer.toString(sslSocketThread.getPort()));
            Log.info(LocaleUtils.getLocalizedString("startup.ssl", params));
        }
        catch (Exception e) {
            System.err.println("Error starting SSL XMPP listener on port " + port + ": " +
                    e.getMessage());
            Log.error(LocaleUtils.getLocalizedString("admin.error.ssl"), e);
        }
    }

    private void stopClientSSLListeners() {
        if (sslSocketThread != null) {
            sslSocketThread.shutdown();
            sslSocketThread = null;
        }
    }

    /**
     * Restarts the server and all it's modules only if the server is restartable. Otherwise do
     * nothing.
     */
    public void restart() {
        if (isStandAlone() && isRestartable()) {
            try {
                Class wrapperClass = Class.forName(WRAPPER_CLASSNAME);
                Method restartMethod = wrapperClass.getMethod("restart", (Class []) null);
                restartMethod.invoke(null, (Object []) null);
            }
            catch (Exception e) {
                Log.error("Could not restart container", e);
            }
        }
    }

    /**
     * Stops the server only if running in standalone mode. Do nothing if the server is running
     * inside of another server.
     */
    public void stop() {
        // Only do a system exit if we're running standalone
        if (isStandAlone()) {
            // if we're in a wrapper, we have to tell the wrapper to shut us down
            if (isRestartable()) {
                try {
                    Class wrapperClass = Class.forName(WRAPPER_CLASSNAME);
                    Method stopMethod = wrapperClass.getMethod("stop", Integer.TYPE);
                    stopMethod.invoke(null, 0);
                }
                catch (Exception e) {
                    Log.error("Could not stop container", e);
                }
            }
            else {
                shutdownServer();
                stopDate = new Date();
                Thread shutdownThread = new ShutdownThread();
                shutdownThread.setDaemon(true);
                shutdownThread.start();
            }
        }
        else {
            // Close listening socket no matter what the condition is in order to be able
            // to be restartable inside a container.
            shutdownServer();
            stopDate = new Date();
        }
    }

    /**
     * Makes a best effort attempt to shutdown the server
     */
    private void shutdownServer() {
        // Stop modules
        stopModules();
        // hack to allow safe stopping
        Log.info("Connection Manager stopped");
    }

    public boolean isSetupMode() {
        return setupMode;
    }

    public boolean isRestartable() {
        boolean restartable;
        try {
            restartable = Class.forName(WRAPPER_CLASSNAME) != null;
        }
        catch (ClassNotFoundException e) {
            restartable = false;
        }
        return restartable;
    }

    /**
     * Returns if the server is running in standalone mode. We consider that it's running in
     * standalone if the "org.jivesoftware.multiplexer.starter.ServerStarter" class is present in the
     * system.
     *
     * @return true if the server is running in standalone mode.
     */
    public boolean isStandAlone() {
        boolean standalone;
        try {
            standalone = Class.forName(STARTER_CLASSNAME) != null;
        }
        catch (ClassNotFoundException e) {
            standalone = false;
        }
        return standalone;
    }

    /**
     * Returns the service responsible for forwarding stanzas to the server.
     *
     * @return the service responsible for forwarding stanzas to the server.
     */
    public ServerSurrogate getServerSurrogate() {
        return serverSurrogate;
    }

    /**
     * Returns the name of the main server where received packets will be forwarded.
     *
     * @return the name of the main server where received packets will be forwarded.
     */
    public String getServerName() {
        return serverName;
    }

    /**
     * Returns the name that uniquely identifies this connection manager. Use the property
     * <tt>xmpp.manager.name</tt> to manually set a name. If the property is not present then a
     * random name will be created for the manager each time it is started. Properties
     * are stored in conf/manager.xml. There are several ways for locating this file.
     * <ol>
     *  <li>Set the system property <b>managerHome</b> when starting up the server.
     *  <li>When running in standalone mode attempt to find it in [home]/conf/manager.xml.
     *  <li>Load the path from <b>manager_init.xml</b> which must be in the classpath.
     * </ol>
     *
     * @return the name that uniquely identifies this connection manager.
     */
    public String getName() {
        return name;
    }

    /**
     * Verifies that the given home guess is a real Connection Manager home directory.
     * We do the verification by checking for the Connection Manager config file in
     * the config dir of jiveHome.
     *
     * @param homeGuess a guess at the path to the home directory.
     * @param jiveConfigName the name of the config file to check.
     * @return a file pointing to the home directory or null if the
     *         home directory guess was wrong.
     * @throws java.io.FileNotFoundException if there was a problem with the home
     *                                       directory provided
     */
    private File verifyHome(String homeGuess, String jiveConfigName) throws FileNotFoundException {
        File managerHome = new File(homeGuess);
        File configFile = new File(managerHome, jiveConfigName);
        if (!configFile.exists()) {
            throw new FileNotFoundException();
        }
        else {
            try {
                return new File(managerHome.getCanonicalPath());
            }
            catch (Exception ex) {
                throw new FileNotFoundException();
            }
        }
    }

    /**
     * <p>Retrieve the jive home for the container.</p>
     *
     * @throws FileNotFoundException If jiveHome could not be located
     */
    private void locateHome() throws FileNotFoundException {
        String jiveConfigName = "conf" + File.separator + "manager.xml";
        // First, try to load it managerHome as a system property.
        if (managerHome == null) {
            String homeProperty = System.getProperty("managerHome");
            try {
                if (homeProperty != null) {
                    managerHome = verifyHome(homeProperty, jiveConfigName);
                }
            }
            catch (FileNotFoundException fe) {
                // Ignore.
            }
        }

        // If we still don't have home, let's assume this is standalone
        // and just look for home in a standard sub-dir location and verify
        // by looking for the config file
        if (managerHome == null) {
            try {
                managerHome = verifyHome("..", jiveConfigName).getCanonicalFile();
            }
            catch (FileNotFoundException fe) {
                // Ignore.
            }
            catch (IOException ie) {
                // Ignore.
            }
        }

        // If home is still null, no outside process has set it and
        // we have to attempt to load the value from manager_init.xml,
        // which must be in the classpath.
        if (managerHome == null) {
            InputStream in = null;
            try {
                in = getClass().getResourceAsStream("/manager_init.xml");
                if (in != null) {
                    SAXReader reader = new SAXReader();
                    Document doc = reader.read(in);
                    String path = doc.getRootElement().getText();
                    try {
                        if (path != null) {
                            managerHome = verifyHome(path, jiveConfigName);
                        }
                    }
                    catch (FileNotFoundException fe) {
                        fe.printStackTrace();
                    }
                }
            }
            catch (Exception e) {
                System.err.println("Error loading manager_init.xml to find home.");
                e.printStackTrace();
            }
            finally {
                try {
                    if (in != null) {
                        in.close();
                    }
                }
                catch (Exception e) {
                    System.err.println("Could not close open connection");
                    e.printStackTrace();
                }
            }
        }

        if (managerHome == null) {
            System.err.println("Could not locate home");
            throw new FileNotFoundException();
        }
        else {
            // Set the home directory for the config file
            JiveGlobals.setHomeDirectory(managerHome.toString());
            // Set the name of the config file
            JiveGlobals.setConfigName(jiveConfigName);
        }
    }

    /**
     * <p>A thread to ensure the server shuts down no matter what.</p>
     * <p>Spawned when stop() is called in standalone mode, we wait a few
     * seconds then call system exit().</p>
     *
     * @author Iain Shigeoka
     */
    private class ShutdownHookThread extends Thread {

        /**
         * <p>Logs the server shutdown.</p>
         */
        public void run() {
            shutdownServer();
            Log.info("Connection Manager halted");
            System.err.println("Connection Manager halted");
        }
    }

    /**
     * <p>A thread to ensure the server shuts down no matter what.</p>
     * <p>Spawned when stop() is called in standalone mode, we wait a few
     * seconds then call system exit().</p>
     *
     * @author Iain Shigeoka
     */
    private class ShutdownThread extends Thread {

        /**
         * <p>Shuts down the JVM after a 5 second delay.</p>
         */
        public void run() {
            try {
                Thread.sleep(5000);
                // No matter what, we make sure it's dead
                System.exit(0);
            }
            catch (InterruptedException e) {
                // Ignore.
            }

        }
    }
}
