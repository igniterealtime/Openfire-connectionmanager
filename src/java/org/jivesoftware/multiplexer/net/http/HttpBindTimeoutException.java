/**
 * $RCSfile:  $
 * $Revision:  $
 * $Date:  $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 * This software is the proprietary information of Jive Software. Use is subject to license terms.
 */
package org.jivesoftware.multiplexer.net.http;

/**
 * An exception which indicates that the maximum waiting time for a client response has been
 * surpassed and an empty response should be returned to the requesting client.
 *
 * @author Alexander Wenckus
 */
class HttpBindTimeoutException extends Exception {
    public HttpBindTimeoutException(String message) {
        super(message);
    }

    public HttpBindTimeoutException() {
        super();
    }
}
