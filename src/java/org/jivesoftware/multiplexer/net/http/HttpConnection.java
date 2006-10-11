/**
 * $RCSfile:  $
 * $Revision:  $
 * $Date:  $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 * This software is the proprietary information of Jive Software. Use is subject to license terms.
 */
package org.jivesoftware.multiplexer.net.http;

import org.jivesoftware.multiplexer.Connection;
import org.dom4j.Element;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 *
 */
public class HttpConnection implements Connection {
    private int majorVersion = 1;
    private int minorVersion = 0;

    public boolean validate() {
        return false;
    }

    public InetAddress getInetAddress() throws UnknownHostException {
        return null;
    }

    public void close() {
    }

    public void systemShutdown() {
    }

    public boolean isClosed() {
        return false;
    }

    public boolean isSecure() {
        return false;
    }

    public void deliver(Element doc) {
    }

    public void deliverRawText(String text) {
    }

    public boolean isFlashClient() {
        return false;
    }

    public int getMajorXMPPVersion() {
        return majorVersion;
    }

    public int getMinorXMPPVersion() {
        return minorVersion;
    }

    public String getLanguage() {
        return null;
    }

    public boolean isCompressed() {
        return false;
    }

    public CompressionPolicy getCompressionPolicy() {
        return null;
    }

    public TLSPolicy getTlsPolicy() {
        return null;
    }
}
