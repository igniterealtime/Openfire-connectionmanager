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
