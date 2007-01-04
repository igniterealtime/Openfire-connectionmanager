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

import junit.framework.TestCase;
import org.apache.mina.common.ByteBuffer;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMPPPacketReader;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.StringReader;
import java.nio.charset.Charset;

/**
 * Simple test of XMLLightweightParser.
 *
 * @author Gaston Dombiak 
 */
public class XMLLightweightParserTest extends TestCase {

    private final static String CHARSET = "UTF-8";

    private XMLLightweightParser parser;
    private ByteBuffer in;

    public void testHeader() throws Exception {
        String msg1 =
                "<stream:stream to=\"localhost\" xmlns=\"jabber:client\" xmlns:stream=\"http://etherx.jabber.org/streams\" version=\"1.0\">";
        in.putString(msg1, Charset.forName(CHARSET).newEncoder());
        in.flip();
        // Fill parser with byte buffer content and parse it
        parser.read(in);
        // Make verifications
        assertTrue("Stream header is not being correctly parsed", parser.areThereMsgs());
        assertEquals("Wrong stanza was parsed", msg1, parser.getMsgs()[0]);
    }

    public void testHeaderWithXMLVersion() throws Exception {
        String msg1 = "<?xml version=\"1.0\"?>";
        String msg2 = "<stream:stream to=\"localhost\" xmlns=\"jabber:client\" xmlns:stream=\"http://etherx.jabber.org/streams\" version=\"1.0\">";
        in.putString(msg1 + msg2, Charset.forName(CHARSET).newEncoder());
        in.flip();
        // Fill parser with byte buffer content and parse it
        parser.read(in);
        // Make verifications
        assertTrue("Stream header is not being correctly parsed", parser.areThereMsgs());
        String[] values = parser.getMsgs();
        assertEquals("Wrong number of parsed stanzas", 2, values.length);
        assertEquals("Wrong stanza was parsed", msg1, values[0]);
        assertEquals("Wrong stanza was parsed", msg2, values[1]);
    }

    public void testStanzas() throws Exception {
        String msg1 = "<stream:stream to=\"localhost\" xmlns=\"jabber:client\" xmlns:stream=\"http://etherx.jabber.org/streams\" version=\"1.0\">";
        String msg2 = "<starttls xmlns=\"urn:ietf:params:xml:ns:xmpp-tls\"/>";
        String msg3 = "<stream:stream to=\"localhost\" xmlns=\"jabber:client\" xmlns:stream=\"http://etherx.jabber.org/streams\" version=\"1.0\">";
        String msg4 = "<iq id=\"428qP-0\" to=\"localhost\" type=\"get\"><query xmlns=\"jabber:iq:register\"></query></iq>";
        String msg5 = "<stream:stream to=\"localhost\" xmlns=\"jabber:client\" xmlns:stream=\"http://etherx.jabber.org/streams\" version=\"1.0\">";
        String msg6 = "<presence id=\"428qP-5\"></presence>";
        in.putString(msg1, Charset.forName(CHARSET).newEncoder());
        in.putString(msg2, Charset.forName(CHARSET).newEncoder());
        in.putString(msg3, Charset.forName(CHARSET).newEncoder());
        in.putString(msg4, Charset.forName(CHARSET).newEncoder());
        in.putString(msg5, Charset.forName(CHARSET).newEncoder());
        in.putString(msg6, Charset.forName(CHARSET).newEncoder());
        in.flip();
        // Fill parser with byte buffer content and parse it
        parser.read(in);
        // Make verifications
        assertTrue("Stream header is not being correctly parsed", parser.areThereMsgs());
        String[] values = parser.getMsgs();
        assertEquals("Wrong number of parsed stanzas", 6, values.length);
        assertEquals("Wrong stanza was parsed", msg1, values[0]);
        assertEquals("Wrong stanza was parsed", msg2, values[1]);
        assertEquals("Wrong stanza was parsed", msg3, values[2]);
        assertEquals("Wrong stanza was parsed", msg4, values[3]);
        assertEquals("Wrong stanza was parsed", msg5, values[4]);
        assertEquals("Wrong stanza was parsed", msg6, values[5]);
    }

    public void testCompleteStanzas() throws Exception {
        String msg1 = "<stream:stream to=\"localhost\" xmlns=\"jabber:client\" xmlns:stream=\"http://etherx.jabber.org/streams\" version=\"1.0\">";
        String msg2 = "<starttls xmlns=\"urn:ietf:params:xml:ns:xmpp-tls\"/>";
        String msg3 = "<stream:stream to=\"localhost\" xmlns=\"jabber:client\" xmlns:stream=\"http://etherx.jabber.org/streams\" version=\"1.0\">";
        String msg4 = "<iq id=\"428qP-0\" to=\"localhost\" type=\"get\"><query xmlns=\"jabber:iq:register\"></query></iq>";
        String msg5 = "<stream:stream to=\"localhost\" xmlns=\"jabber:client\" xmlns:stream=\"http://etherx.jabber.org/streams\" version=\"1.0\">";
        String msg6 = "<presence id=\"428qP-5\"></presence>";
        String msg7 = "</stream:stream>";
        in.putString(msg1, Charset.forName(CHARSET).newEncoder());
        in.putString(msg2, Charset.forName(CHARSET).newEncoder());
        in.putString(msg3, Charset.forName(CHARSET).newEncoder());
        in.putString(msg4, Charset.forName(CHARSET).newEncoder());
        in.putString(msg5, Charset.forName(CHARSET).newEncoder());
        in.putString(msg6, Charset.forName(CHARSET).newEncoder());
        in.putString(msg7, Charset.forName(CHARSET).newEncoder());
        in.flip();
        // Fill parser with byte buffer content and parse it
        parser.read(in);
        // Make verifications
        assertTrue("Stream header is not being correctly parsed", parser.areThereMsgs());
        String[] values = parser.getMsgs();
        assertEquals("Wrong number of parsed stanzas", 7, values.length);
        assertEquals("Wrong stanza was parsed", msg1, values[0]);
        assertEquals("Wrong stanza was parsed", msg2, values[1]);
        assertEquals("Wrong stanza was parsed", msg3, values[2]);
        assertEquals("Wrong stanza was parsed", msg4, values[3]);
        assertEquals("Wrong stanza was parsed", msg5, values[4]);
        assertEquals("Wrong stanza was parsed", msg6, values[5]);
        assertEquals("Wrong stanza was parsed", msg7, values[6]);
    }

    public void testIQ() throws Exception {
        String iq =
                "<iq type=\"set\" to=\"lachesis\" from=\"0sups/Connection Worker - 1\" id=\"360-22348\"><session xmlns=\"http://jabber.org/protocol/connectionmanager\" id=\"0sups87b1694\"><close/></session></iq>";
        in.putString(iq, Charset.forName(CHARSET).newEncoder());
        in.flip();
        // Fill parser with byte buffer content and parse it
        parser.read(in);
        // Make verifications
        assertTrue("Stream header is not being correctly parsed", parser.areThereMsgs());
        String parsedIQ = parser.getMsgs()[0];
        assertEquals("Wrong stanza was parsed", iq, parsedIQ);

        SAXReader reader = new SAXReader();
        reader.setEncoding(CHARSET);
        Element doc = reader.read(new StringReader(parsedIQ)).getRootElement();
        assertNotNull("Failed to parse IQ stanza", doc);
    }

    public void testParsing() throws Exception {
        String stanza = "<presence type=\"unavailable\" from=\"user11840@lachesis/tsung\" to=\"user11846@lachesis\"/>";

        XmlPullParserFactory factory = XmlPullParserFactory.newInstance(MXParser.class.getName(), null);
        factory.setNamespaceAware(true);

        XMPPPacketReader xmppReader = new XMPPPacketReader();
        xmppReader.setXPPFactory(factory);
        Element doc = xmppReader.read(new StringReader(stanza)).getRootElement();
        assertNotNull(doc);
        assertEquals(stanza, doc.asXML());
    }

    protected void setUp() throws Exception {
        super.setUp();
        // Create parser
        parser = new XMLLightweightParser(CHARSET);
        // Crete byte buffer and append text
        in = ByteBuffer.allocate(4096);
        in.setAutoExpand(true);
    }


    protected void tearDown() throws Exception {
        super.tearDown();
        // Release byte buffer
        in.release();
    }
}
