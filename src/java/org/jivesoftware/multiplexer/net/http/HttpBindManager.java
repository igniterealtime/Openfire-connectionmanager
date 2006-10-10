/**
 * $RCSfile:  $
 * $Revision:  $
 * $Date:  $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 * This software is the proprietary information of Jive Software. Use is subject to license terms.
 */
package org.jivesoftware.multiplexer.net.http;

import org.mortbay.jetty.Server;
import org.mortbay.jetty.Connector;
import org.mortbay.jetty.servlet.ServletHolder;
import org.mortbay.jetty.servlet.ServletHandler;
import org.mortbay.jetty.nio.SelectChannelConnector;

/**
 * Manages connections to the server which use the HTTP Bind protocol specified in
 * <a href="http://www.xmpp.org/extensions/xep-0124.html">XEP-0124</a>. The manager maps a servlet
 * to an embedded servlet container using the ports provided in the constructor.
 *
 * @author Alexander Wenckus
 */
public class HttpBindManager {
    private int plainPort;
    private int sslPort;
    private Server server;

    public HttpBindManager(int plainPort, int sslPort) {
        this.plainPort = plainPort;
        this.sslPort = sslPort;
        this.server = new Server();
    }

    public void startup() throws Exception {
        SelectChannelConnector connector = new SelectChannelConnector();
        connector.setPort(plainPort);
        server.setConnectors(new Connector[]{connector});

        ServletHolder servletHolder = new ServletHolder(
                new HttpBindServlet(new HttpSessionManager()));
        ServletHandler servletHandler = new ServletHandler();
        servletHandler.addServletWithMapping(servletHolder, "/");
        server.addHandler(servletHandler);

        server.start();
    }

    public void shutdown() throws Exception {
        server.stop();
    }
}
