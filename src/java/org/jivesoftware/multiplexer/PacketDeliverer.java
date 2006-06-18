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

/**
 * Delivers packets to locally connected streams. This is the opposite
 * of the packet transporter.
 *
 * @author Iain Shigeoka
 */
public interface PacketDeliverer {

    /**
     * Delivers the given packet based on packet recipient and sender. The
     * deliverer defers actual routing decisions to other classes.
     * <h2>Warning</h2>
     * Be careful to enforce concurrency DbC of concurrent by synchronizing
     * any accesses to class resources.
     *
     * @param doc the packet to route
     */
    public void deliver(Element doc);
}
