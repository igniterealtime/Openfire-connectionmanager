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

/**
 * Interface that keeps statistics of sockets. Currently the only supported statistic
 * is the last time a socket received a stanza or a heartbeat.
 *
 * @author Gaston Dombiak
 */
public interface SocketStatistic {

    /**
     * Returns the last time a stanza was read or a heartbeat was received. Hearbeats
     * are represented as whitespaces received while a Document is not being parsed.
     *
     * @return the time in milliseconds when the last stanza or heartbeat was received.
     */
    public long getLastActive();
}
