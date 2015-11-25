/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.multiplexer;

import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

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

    private static final ConcurrentHashMap<String, Boolean> usingStreamIDs = new ConcurrentHashMap<String, Boolean>();

    String managerName = ConnectionManager.getInstance().getName();

    private Random rand = new Random();

    public String createStreamID() {
        String streamID;
        do {
            streamID = managerName + Integer.toHexString(rand.nextInt());
        } while(usingStreamIDs.putIfAbsent(streamID, Boolean.TRUE) == null);
        return streamID;
    }

    public static void releaseId(String streamId){
        usingStreamIDs.remove(streamId);
    }

}
