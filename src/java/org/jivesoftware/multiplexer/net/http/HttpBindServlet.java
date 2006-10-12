/**
 * $RCSfile:  $
 * $Revision:  $
 * $Date:  $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 * This software is the proprietary information of Jive Software. Use is subject to license terms.
 */
package org.jivesoftware.multiplexer.net.http;

import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlPullParserException;
import org.jivesoftware.multiplexer.net.MXParser;
import org.jivesoftware.util.Log;
import org.dom4j.io.XMPPPacketReader;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;

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

    private static XmlPullParserFactory factory;

    static {
        try {
            factory = XmlPullParserFactory.newInstance(MXParser.class.getName(), null);
        }
        catch (XmlPullParserException e) {
            Log.error("Error creating a parser factory", e);
        }
    }

    public HttpBindServlet(HttpSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Document document;
        try {
            document = createDocument(request);
        }
        catch (Exception e) {
            Log.warn("Error parsing user request. [" + request.getRemoteAddr() + "]");
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "Unable to parse request content: " + e.getMessage());
            return;
        }

        Element node = document.getRootElement();
        if(node == null || !"body".equals(node.getName())) {
            Log.warn("Body missing from request content. [" + request.getRemoteAddr() + "]");
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "Body missing from request content.");
            return;
        }

        String sid = node.attributeValue("sid");
        // We have a new session
        if(sid == null) {
            createNewSession(request, response, node);
        }
        else {

        }
    }

    private void createNewSession(HttpServletRequest request, HttpServletResponse response,
                                  Element rootNode) throws IOException {
        long rid = getLongAttribue(rootNode.attributeValue("rid"), -1);
        if(rid <= 0) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Body missing RID (Request ID)");
            return;
        }

        HttpConnection connection = new HttpConnection(rid);
        connection.setSession(sessionManager.createSession(rootNode, connection));
        respond(response, connection);
    }

    private void respond(HttpServletResponse response, HttpConnection connection)
            throws IOException
    {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("text/xml");
        response.setCharacterEncoding("utf-8");

        byte [] content = connection.getDeliverable().getBytes();
        response.setContentLength(content.length);
        response.getOutputStream().write(content);
    }

    private long getLongAttribue(String value, long defaultValue) {
        if(value == null || "".equals(value)) {
            return defaultValue;
        }
        try {
            return Long.valueOf(value);
        }
        catch (Exception ex) {
            return defaultValue;
        }
    }

    private Document createDocument(HttpServletRequest request) throws
            DocumentException, IOException, XmlPullParserException
    {
        Document document = (Document) request.getAttribute("xml-document");
        if (document == null) {
            // Reader is associated with a new XMPPPacketReader
            XMPPPacketReader reader = new XMPPPacketReader();
            reader.setXPPFactory(factory);

            document = reader.read(request.getInputStream());
            request.setAttribute("xml-document", document);
        }
        return document;
    }
}
