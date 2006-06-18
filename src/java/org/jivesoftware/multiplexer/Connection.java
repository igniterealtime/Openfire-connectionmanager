/**
 * $RCSfile: $
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

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Represents a connection on the server.
 *
 * @author Iain Shigeoka
 */
public interface Connection {

    /**
     * Verifies that the connection is still live. Typically this is done by
     * sending a whitespace character between packets.
     *
     * @return true if the socket remains valid, false otherwise.
     */
    public boolean validate();

    /**
     * Returns the InetAddress describing the connection.
     *
     * @return the InetAddress describing the underlying connection properties.
     */
    public InetAddress getInetAddress() throws UnknownHostException;

    /**
     * Close this session including associated socket connection. The order of
     * events for closing the session is:
     * <ul>
     *      <li>Set closing flag to prevent redundant shutdowns.
     *      <li>Call notifyEvent all listeners that the channel is shutting down.
     *      <li>Close the socket.
     * </ul>
     */
    public void close();

    /**
     * Notification message indicating that the server is being shutdown. Implementors
     * should send a stream error whose condition is system-shutdown before closing
     * the connection.
     */
    public void systemShutdown();

    /**
     * Returns true if the connection/session is closed.
     *
     * @return true if the connection is closed.
     */
    public boolean isClosed();

    /**
     * Returns true if this connection is secure.
     *
     * @return true if the connection is secure (e.g. SSL/TLS)
     */
    public boolean isSecure();

    /**
     * Delivers the packet to this connection without checking the recipient.
     * The method essentially calls <code>socket.send(packet.getWriteBuffer())</code>.
     *
     * @param doc the packet to deliver.
     */
    public void deliver(Element doc);

    /**
     * Delivers raw text to this connection. This is a very low level way for sending
     * XML stanzas to the client. This method should not be used unless you have very
     * good reasons for not using {@link #deliver(Element)}.<p>
     *
     * This method avoids having to get the writer of this connection and mess directly
     * with the writer. Therefore, this method ensures a correct delivery of the stanza
     * even if other threads were sending data concurrently.
     *
     * @param text the XML stanzas represented kept in a String.
     */
    public void deliverRawText(String text);

    /**
     * Returns true if the connected client is a flash client. Flash clients need
     * to receive a special character (i.e. \0) at the end of each xml packet. Flash
     * clients may send the character \0 in incoming packets and may start a connection
     * using another openning tag such as: "flash:client".
     *
     * @return true if the connected client is a flash client.
     */
    public boolean isFlashClient();

    /**
     * Returns the major version of XMPP being used by this connection
     * (major_version.minor_version. In most cases, the version should be
     * "1.0". However, older clients using the "Jabber" protocol do not set a
     * version. In that case, the version is "0.0".
     *
     * @return the major XMPP version being used by this connection.
     */
    public int getMajorXMPPVersion();

    /**
     * Returns the minor version of XMPP being used by this connection
     * (major_version.minor_version. In most cases, the version should be
     * "1.0". However, older clients using the "Jabber" protocol do not set a
     * version. In that case, the version is "0.0".
     *
     * @return the minor XMPP version being used by this connection.
     */
    public int getMinorXMPPVersion();

    /**
     * Returns the language code that should be used for this connection
     * (e.g. "en").
     *
     * @return the language code for the connection.
     */
    public String getLanguage();

    /**
     * Returns true if the connection is using compression.
     *
     * @return true if the connection is using compression.
     */
    boolean isCompressed();

    /**
     * Returns whether compression is optional or is disabled.
     *
     * @return whether compression is optional or is disabled.
     */
    CompressionPolicy getCompressionPolicy();

    /**
     * Returns whether TLS is mandatory, optional or is disabled. When TLS is mandatory clients
     * are required to secure their connections or otherwise their connections will be closed.
     * On the other hand, when TLS is disabled clients are not allowed to secure their connections
     * using TLS. Their connections will be closed if they try to secure the connection. in this
     * last case.
     *
     * @return whether TLS is mandatory, optional or is disabled.
     */
    TLSPolicy getTlsPolicy();

    /**
     * Enumeration of possible compression policies required to interact with the server.
     */
    enum CompressionPolicy {

        /**
         * compression is optional to interact with the server.
         */
        optional,

        /**
         * compression is not available. Entities that request a compression negotiation
         * will get a stream error and their connections will be closed.
         */
        disabled
    }

    /**
     * Enumeration of possible TLS policies required to interact with the server.
     */
    enum TLSPolicy {

        /**
         * TLS is required to interact with the server. Entities that do not secure their
         * connections using TLS will get a stream error and their connections will be closed.
         */
        required,

        /**
         * TLS is optional to interact with the server. Entities may or may not secure their
         * connections using TLS.
         */
        optional,

        /**
         * TLS is not available. Entities that request a TLS negotiation will get a stream
         * error and their connections will be closed.
         */
        disabled
    }
}
