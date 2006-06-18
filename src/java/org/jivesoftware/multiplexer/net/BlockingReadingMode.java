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

package org.jivesoftware.multiplexer.net;

import com.jcraft.jzlib.JZlib;
import com.jcraft.jzlib.ZInputStream;
import org.dom4j.Element;
import org.jivesoftware.multiplexer.Connection;
import org.jivesoftware.multiplexer.Session;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.SocketException;
import java.nio.channels.AsynchronousCloseException;

/**
 * Process incoming packets using a blocking model. Once a session has been created
 * an endless loop is used to process incoming packets. Packets are processed
 * sequentially.
 *
 * @author Gaston Dombiak
 */
class BlockingReadingMode extends SocketReadingMode {

    private Status saslStatus = Status.waitingServer;

    public BlockingReadingMode(Socket socket, SocketReader socketReader) {
        super(socket, socketReader);
    }

    /**
     * A dedicated thread loop for reading the stream and sending incoming
     * packets to the appropriate router.
     */
    public void run() {
        try {
            socketReader.reader.getXPPParser().setInput(new InputStreamReader(socket.getInputStream(),
                    CHARSET));

            // Read in the opening tag and prepare for packet stream
            try {
                socketReader.createSession();
            }
            catch (IOException e) {
                Log.debug("Error creating session", e);
                throw e;
            }

            // Read the packet stream until it ends
            if (socketReader.session != null) {
                readStream();
            }

        }
        catch (EOFException eof) {
            // Normal disconnect
        }
        catch (SocketException se) {
            // The socket was closed. The server may close the connection for several
            // reasons (e.g. user requested to remove his account). Do nothing here.
        }
        catch (AsynchronousCloseException ace) {
            // The socket was closed.
        }
        catch (XmlPullParserException ie) {
            // It is normal for clients to abruptly cut a connection
            // rather than closing the stream document. Since this is
            // normal behavior, we won't log it as an error.
            // Log.error(LocaleUtils.getLocalizedString("admin.disconnect"),ie);
        }
        catch (Exception e) {
            Log.warn(LocaleUtils.getLocalizedString("admin.error.stream") + ". Connection: " +
                    socketReader.connection, e);
        }
        finally {
            if (socketReader.session != null) {
                if (Log.isDebugEnabled()) {
                    Log.debug("Logging off " + socketReader.connection);
                }
                try {
                    socketReader.session.close();
                }
                catch (Exception e) {
                    Log.warn(LocaleUtils.getLocalizedString("admin.error.connection")
                            + "\n" + socket.toString());
                }
            }
            else {
                // Close and release the created connection
                socketReader.connection.close();
                Log.error(LocaleUtils.getLocalizedString("admin.error.connection")
                        + "\n" + socket.toString());
            }
            socketReader.shutdown();
        }
    }

    /**
     * Read the incoming stream until it ends.
     */
    private void readStream() throws Exception {
        socketReader.open = true;
        while (socketReader.open) {
            Element doc = socketReader.reader.parseDocument().getRootElement();
            if (doc == null) {
                // Stop reading the stream since the client has sent an end of
                // stream element and probably closed the connection.
                return;
            }
            String tag = doc.getName();
            if ("starttls".equals(tag)) {
                // Negotiate TLS
                if (negotiateTLS()) {
                    tlsNegotiated();
                }
                else {
                    socketReader.open = false;
                }
            }
            else if ("auth".equals(tag)) {
                // User is trying to authenticate using SASL
                if (authenticateClient(doc)) {
                    // SASL authentication was successful so open a new stream and offer
                    // resource binding and session establishment (to client sessions only)
                    saslSuccessful();
                }
            }
            else if ("compress".equals(tag))
            {
                // Client is trying to initiate compression
                if (compressClient(doc)) {
                    // Compression was successful so open a new stream and offer
                    // resource binding and session establishment (to client sessions only)
                    compressionSuccessful();
                }
            }
            else {
                socketReader.process(doc);
            }
        }
    }

    protected void tlsNegotiated() throws XmlPullParserException, IOException {
        XmlPullParser xpp = socketReader.reader.getXPPParser();
        // Reset the parser to use the new reader
        xpp.setInput(new InputStreamReader(
                socketReader.connection.getTLSStreamHandler().getInputStream(), CHARSET));
        // Skip new stream element
        for (int eventType = xpp.getEventType(); eventType != XmlPullParser.START_TAG;) {
            eventType = xpp.next();
        }
        super.tlsNegotiated();
    }

    protected boolean authenticateClient(Element doc) throws Exception {
        // Ensure that connection was secured if TLS was required
        if (socketReader.connection.getTlsPolicy() == Connection.TLSPolicy.required &&
                !socketReader.connection.isSecure()) {
            socketReader.closeNeverSecuredConnection();
            return false;
        }

        boolean isComplete = false;
        boolean success = false;
        while (!isComplete && !socketReader.connection.isClosed()) {
            // Forward stanza to the server
            socketReader.process(doc);
            // Wait 5 minutes to get a response from the server
            synchronized (this) {
                wait(5 * 60 * 1000);
            }
            // Raise an error if no response from the server was received
            if (saslStatus == Status.waitingServer) {
                throw new Exception("No answer was received from the server");
            }

            // If client was challenged then wait for client answer
            if (saslStatus == Status.needResponse) {
                doc = socketReader.reader.parseDocument().getRootElement();
                if (doc == null) {
                    // Nothing was read because the connection was closed or dropped
                    isComplete = true;
                }
            }
            else {
                success = socketReader.session.getStatus() == Session.STATUS_AUTHENTICATED;
                isComplete = true;
            }
        }
        return success;
    }

    /**
     * Notification message indicating that a client needs to response to a SASL
     * challenge.
     */
    void clientChallenged() {
        // Set that client needs to send response
        saslStatus = Status.needResponse;
        synchronized (this) {
            notify();
        }
    }

    /**
     * Notification message indicating that sasl authentication has finished. The
     * <tt>success</tt> parameter indicates whether authentication was successful or not.
     *
     * @param success true when authentication was successful.
     */
    void clientAuthenticated(boolean success) {
        // Set result of authentication process
        saslStatus = success ? Status.authenticated : Status.failed;
        synchronized (this) {
            notify();
        }
    }

    protected void saslSuccessful() throws XmlPullParserException, IOException {
        MXParser xpp = socketReader.reader.getXPPParser();
        // Reset the parser since a new stream header has been sent from the client
        xpp.resetInput();

        // Skip the opening stream sent by the client
        for (int eventType = xpp.getEventType(); eventType != XmlPullParser.START_TAG;) {
            eventType = xpp.next();
        }
        super.saslSuccessful();
    }

    protected boolean compressClient(Element doc) throws XmlPullParserException, IOException {
        boolean answer = super.compressClient(doc);
        if (answer) {
            XmlPullParser xpp = socketReader.reader.getXPPParser();
            // Reset the parser since a new stream header has been sent from the client
            if (socketReader.connection.getTLSStreamHandler() == null) {
                ZInputStream in = new ZInputStream(socket.getInputStream());
                in.setFlushMode(JZlib.Z_PARTIAL_FLUSH);
                xpp.setInput(new InputStreamReader(in, CHARSET));
            }
            else {
                ZInputStream in = new ZInputStream(
                        socketReader.connection.getTLSStreamHandler().getInputStream());
                in.setFlushMode(JZlib.Z_PARTIAL_FLUSH);
                xpp.setInput(new InputStreamReader(in, CHARSET));
            }
        }
        return answer;
    }

    protected void compressionSuccessful() throws XmlPullParserException, IOException {
        XmlPullParser xpp = socketReader.reader.getXPPParser();
        // Skip the opening stream sent by the client
        for (int eventType = xpp.getEventType(); eventType != XmlPullParser.START_TAG;) {
            eventType = xpp.next();
        }
        super.compressionSuccessful();
    }

    public enum Status {
        /**
         * Server needs to process sasl stanza and send its answer to the client.
         */
        waitingServer,
        /**
         * Entity needs to respond last challenge. Session is still negotiating
         * SASL authentication.
         */
        needResponse,
        /**
         * SASL negotiation has failed. The entity may retry a few times before the connection
         * is closed.
         */
        failed,
        /**
         * SASL negotiation has been successful.
         */
        authenticated
    }

}
