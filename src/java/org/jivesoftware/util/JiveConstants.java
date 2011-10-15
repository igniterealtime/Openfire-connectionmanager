/**
 * $RCSfile$
 * $Revision: 1715 $
 * $Date: 2005-07-26 21:05:38 -0300 (Tue, 26 Jul 2005) $
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

package org.jivesoftware.util;

/**
 * Contains constant values representing various objects in Jive.
 */
public class JiveConstants {

    public static final int SYSTEM = 17;
    public static final int ROSTER = 18;
    public static final int OFFLINE = 19;
    public static final int MUC_ROOM = 23;

    public static final long SECOND = 1000;
    public static final long MINUTE = 60 * SECOND;
    public static final long HOUR = 60 * MINUTE;
    public static final long DAY = 24 * HOUR;
    public static final long WEEK = 7 * DAY;

    /**
     * Date/time format for use by SimpleDateFormat. The format conforms to
     * <a href="http://www.jabber.org/jeps/jep-0082.html">JEP-0082</a>, which defines
     * a unified date/time format for XMPP.
     */
    public static final String XMPP_DATETIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
}