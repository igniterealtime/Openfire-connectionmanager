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
