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

    public void testWeirdChars() throws Exception {
        //String stanza = "<message id=\"wXBU0-86\" to=\"derek@jivesoftware.com\" from=\"phone@jivesoftware.com/spark\" type=\"chat\"><body>© 2003 - 2007 by Monsters and Critics.com, WotR Ltd. All Rights Reserved. All photos are copyright their respective owners and are used under license or with permission. * Note M&C cannot be held responsible for the content on other Web Sites.</body><thread>5Yo2Kd</thread><x xmlns=\"jabber:x:event\"><offline/><composing/></x></message>";
        String stanza = "<message id=\"wXBU0-86\" to=\"derek@jivesoftware.com\" from=\"phone@jivesoftware.com/spark\" type=\"chat\"><body>© 2003 - 2007 by Monsters and Critics.com, WotR Ltd. All Rights Reserved. All photos are copyright their respective owners and are used under license or with permission. * Note M\"&amp;C cannot be held responsible for the content on other Web Sites.</body><thread>5Yo2Kd</thread><x xmlns=\"jabber:x:event\"><offline/><composing/></x></message>";

        XmlPullParserFactory factory = XmlPullParserFactory.newInstance(MXParser.class.getName(), null);
        factory.setNamespaceAware(true);

        XMPPPacketReader xmppReader = new XMPPPacketReader();
        xmppReader.setXPPFactory(factory);
        Element doc = xmppReader.read(new StringReader(stanza)).getRootElement();
        assertNotNull(doc);
        assertEquals(stanza, doc.asXML());
    }

    public void testEmptyElement() throws Exception {
        String stanza = "<message/>";

        XmlPullParserFactory factory = XmlPullParserFactory.newInstance(MXParser.class.getName(), null);
        factory.setNamespaceAware(true);

        XMPPPacketReader xmppReader = new XMPPPacketReader();
        xmppReader.setXPPFactory(factory);
        Element doc = xmppReader.read(new StringReader(stanza)).getRootElement();
        assertNotNull(doc);
        assertEquals(stanza, doc.asXML());
    }

    public void testNestedElements() throws Exception {
        String msg1 = "<message><message xmlns=\"e\">1</message></message>";
        //String msg1 = "<message id=\"3W3We-84\" to=\"gato@localhost/IDEtalk\" type=\"chat\"><body>will update it...</body><thread>12jid1</thread><IDEtalk-data xmlns=\"http://idetalk.com/namespace/jabber\"><message xmlns=\"http://idetalk.com/namespace\" when=\"1171565295250\">will update it...</message></IDEtalk-data></message>";
        in.putString(msg1, Charset.forName(CHARSET).newEncoder());
        in.flip();
        // Fill parser with byte buffer content and parse it
        parser.read(in);
        // Make verifications
        assertTrue("Stream header is not being correctly parsed", parser.areThereMsgs());
        String[] values = parser.getMsgs();
        assertEquals("Wrong number of parsed stanzas", 1, values.length);
        assertEquals("Wrong stanza was parsed", msg1, values[0]);
    }

    public void testIncompleteStanza() throws Exception {
        String msg1 = "<message><something xmlns=\"http://idetalk.com/namespace\">12";
        in.putString(msg1, Charset.forName(CHARSET).newEncoder());
        in.flip();
        // Fill parser with byte buffer content and parse it
        parser.read(in);
        // Make verifications
        assertFalse("Found messages in incomplete stanza", parser.areThereMsgs());
    }

    public void testStanzaWithSpecialChars() throws Exception {
        String msg1 = "<message><something xmlns=\"http://idetalk.com/namespace\">12/</something></message>";
        String msg2 = "<message><something xmlns=\"http://idetalk.com/namespace\">12///</something></message>";
        String msg3 = "<message><something xmlns=\"http://idetalk.com/namespace\">12/\\/</something></message>";
        String msg4 = "<message><something xmlns=\"http://idetalk.com/namespace\">http://idetalk.com/namespace/</something></message>";
        in.putString(msg1, Charset.forName(CHARSET).newEncoder());
        in.putString(msg2, Charset.forName(CHARSET).newEncoder());
        in.putString(msg3, Charset.forName(CHARSET).newEncoder());
        in.putString(msg4, Charset.forName(CHARSET).newEncoder());
        in.flip();
        // Fill parser with byte buffer content and parse it
        parser.read(in);
        // Make verifications
        assertTrue("No messages were found in stanza", parser.areThereMsgs());
        String[] values = parser.getMsgs();
        assertEquals("Wrong number of parsed stanzas", 4, values.length);
        assertEquals("Wrong stanza was parsed", msg1, values[0]);
        assertEquals("Wrong stanza was parsed", msg2, values[1]);
        assertEquals("Wrong stanza was parsed", msg3, values[2]);
        assertEquals("Wrong stanza was parsed", msg4, values[3]);
    }

    public void testCompletedStanza() throws Exception {
        String msg1 = "<message><something xmlns=\"http://idetalk.com/namespace\">12";
        in.putString(msg1, Charset.forName(CHARSET).newEncoder());
        in.flip();
        // Fill parser with byte buffer content and parse it
        parser.read(in);
        // Make verifications
        assertFalse("Found messages in incomplete stanza", parser.areThereMsgs());

        String msg2 = "</something></message>";
        ByteBuffer in2 = ByteBuffer.allocate(4096);
        in2.setAutoExpand(true);
        in2.putString(msg2, Charset.forName(CHARSET).newEncoder());
        in2.flip();
        // Fill parser with byte buffer content and parse it
        parser.read(in2);
        in2.release();
        assertTrue("Stream header is not being correctly parsed", parser.areThereMsgs());
        String[] values = parser.getMsgs();
        assertEquals("Wrong number of parsed stanzas", 1, values.length);
        assertEquals("Wrong stanza was parsed", msg1 + msg2, values[0]);
    }

    public void testStanzaWithComments() throws Exception {
        String msg1 = "<iq from=\"lg@jivesoftware.com/spark\"><query xmlns=\"jabber:iq:privacy\"><!-- silly comment --></query></iq>";
        in.putString(msg1, Charset.forName(CHARSET).newEncoder());
        in.flip();
        // Fill parser with byte buffer content and parse it
        parser.read(in);
        // Make verifications
        assertTrue("No messages were found in stanza", parser.areThereMsgs());
        String[] values = parser.getMsgs();
        assertEquals("Wrong number of parsed stanzas", 1, values.length);
        assertEquals("Wrong stanza was parsed", msg1, values[0]);

        XmlPullParserFactory factory = XmlPullParserFactory.newInstance(MXParser.class.getName(), null);
        factory.setNamespaceAware(true);

        XMPPPacketReader xmppReader = new XMPPPacketReader();
        xmppReader.setXPPFactory(factory);
        Element doc = xmppReader.read(new StringReader(values[0])).getRootElement();
        assertNotNull(doc);
        assertEquals(msg1, doc.asXML());
    }

    public void testWeirdoContent() throws Exception {
        final String[] testStanzas =
        {
                "<?xml version=\"1.0\"?>",
                "<stream:stream xmlns:stream=\"http://etherx.jabber.org/streams\" xmlns=\"jabber:client\" to=\"localhost\" >",
                "<emppartag test=\"1\"/>",
                "<cdatatest><![CDATA[just<ignore everything& >>here<<<<< /> />]]&gt;]]></cdatatest>",
                "<esctest param=\"1\"> this \" is / a test /> test /> </esctest>",
                "<comtest>this <!-- comment --> is a comment</comtest>",
                "<emptag/>",
                "<iq type=\"get\" id=\"aab1a\" ><query xmlns=\"jabber:iq:roster\"/> <tag> text </tag></iq>",
                "<iq type=\"get\" id=\"aab1a\" ><query xmlns=\"jabber:iq:roster\"/> </iq>",
                "<message><body xmlns=\"http://idetalk.com/namespace\">12\"</body></message>" ,
                "<message to=\"lg@jivesoftware.com\" id=\"XRk8p-X\"><body> /> /> </body></message>" ,
        };
        String testMsg = "";
        for(String s : testStanzas) {
            testMsg += s;
        }
        ByteBuffer mybuffer = ByteBuffer.wrap(testMsg.getBytes());
        parser.read(mybuffer);

        String[] msgs = parser.getMsgs();
        for(int i = 0; i < testStanzas.length; i++) {
            assertTrue(i < msgs.length);
            assertEquals(testStanzas[i], msgs[i]);
        }
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
