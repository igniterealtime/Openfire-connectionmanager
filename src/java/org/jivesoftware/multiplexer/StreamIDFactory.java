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

import java.util.Random;

/**
 * A basic stream ID factory that produces id's using java.util.Random
 * and a simple hex representation of a random int prefixed by the connection
 * manager name.<p>
 *
 * Each connection manager has to provide unique stream IDs.
 *
 * @author Gaston Dombiak
 */
public class StreamIDFactory {
    /**
     * The random number to use, someone with Java can predict stream IDs if they can guess the current seed *
     */
    Random random = new Random();

    String managerName = ConnectionManager.getInstance().getName();

    public String createStreamID() {
        return managerName + Integer.toHexString(random.nextInt());
    }

}
