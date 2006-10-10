/**
 * $RCSfile:  $
 * $Revision:  $
 * $Date:  $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 * This software is the proprietary information of Jive Software. Use is subject to license terms.
 */
package org.jivesoftware.multiplexer.net.http;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;
import java.io.IOException;

/**
 *
 */
public class HttpBindServlet extends HttpServlet {
    private HttpSessionManager sessionManager;

    public HttpBindServlet(HttpSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    protected void service(HttpServletRequest httpServletRequest,
                           HttpServletResponse httpServletResponse)
            throws ServletException, IOException
    {
        super.service(httpServletRequest, httpServletResponse);
    }
}
