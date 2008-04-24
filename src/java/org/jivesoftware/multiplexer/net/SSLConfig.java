/**
 * $RCSfile$
 * $Revision: 1217 $
 * $Date: 2005-04-11 18:11:06 -0300 (Mon, 11 Apr 2005) $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.multiplexer.net;

import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.KeyManagerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.security.KeyStore;

/**
 * Configuration of Openfire's SSL settings.<p>
 *
 * This class was copied from Openfire. Properties are now stored in XML.
 *
 * @author Gaston Dombiak
 */
public class SSLConfig {

    private static SSLJiveServerSocketFactory sslFactory;
    private static KeyStore keyStore;
    private static String keypass;
    private static KeyStore trustStore;
    private static String trustpass;
    private static String keyStoreLocation;
    private static String trustStoreLocation;
    private static String storeType;
    private static SSLContext context;

    private SSLConfig() {
    }

    static {
        String algorithm = JiveGlobals.getXMLProperty("xmpp.socket.ssl.algorithm", "TLS");
        storeType = JiveGlobals.getXMLProperty("xmpp.socket.ssl.storeType", "jks");

        // Get the keystore location. The default location is security/keystore
        keyStoreLocation = JiveGlobals.getXMLProperty("xmpp.socket.ssl.keystore",
                "resources" + File.separator + "security" + File.separator + "keystore");
        keyStoreLocation = JiveGlobals.getHomeDirectory() + File.separator + keyStoreLocation;

        // Get the keystore password. The default password is "changeit".
        keypass = JiveGlobals.getXMLProperty("xmpp.socket.ssl.keypass", "changeit");
        keypass = keypass.trim();

        // Get the truststore location; default at security/truststore
        trustStoreLocation = JiveGlobals.getXMLProperty("xmpp.socket.ssl.truststore",
                "resources" + File.separator + "security" + File.separator + "truststore");
        trustStoreLocation = JiveGlobals.getHomeDirectory() + File.separator + trustStoreLocation;

        // Get the truststore passwprd; default is "changeit".
        trustpass = JiveGlobals.getXMLProperty("xmpp.socket.ssl.trustpass", "changeit");
        trustpass = trustpass.trim();

        try {
            keyStore = KeyStore.getInstance(storeType);
            keyStore.load(new FileInputStream(keyStoreLocation), keypass.toCharArray());

            trustStore = KeyStore.getInstance(storeType);
            trustStore.load(new FileInputStream(trustStoreLocation), trustpass.toCharArray());

            sslFactory = (SSLJiveServerSocketFactory)SSLJiveServerSocketFactory.getInstance(
                    algorithm, keyStore, trustStore);

            context = SSLContext.getInstance(algorithm);

            KeyManagerFactory keyFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyFactory.init(keyStore, SSLConfig.getKeyPassword().toCharArray());
            TrustManagerFactory c2sTrustFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            c2sTrustFactory.init(trustStore);
            context.init(keyFactory.getKeyManagers(),
                c2sTrustFactory.getTrustManagers(),
                new java.security.SecureRandom());
        }
        catch (Exception e) {
            Log.error("SSLConfig startup problem.\n" +
                    "  storeType: [" + storeType + "]\n" +
                    "  keyStoreLocation: [" + keyStoreLocation + "]\n" +
                    "  keypass: [" + keypass + "]\n" +
                    "  trustStoreLocation: [" + trustStoreLocation+ "]\n" +
                    "  trustpass: [" + trustpass + "]", e);
            keyStore = null;
            trustStore = null;
            sslFactory = null;
        }
    }

    public static String getKeyPassword() {
        return keypass;
    }

    public static String getTrustPassword() {
        return trustpass;
    }

    public static String[] getDefaultCipherSuites() {
        String[] suites;
        if (sslFactory == null) {
            suites = new String[]{};
        }
        else {
            suites = sslFactory.getDefaultCipherSuites();
        }
        return suites;
    }

    public static String[] getSpportedCipherSuites() {
        String[] suites;
        if (sslFactory == null) {
            suites = new String[]{};
        }
        else {
            suites = sslFactory.getSupportedCipherSuites();
        }
        return suites;
    }

    public static KeyStore getKeyStore() throws IOException {
        if (keyStore == null) {
            throw new IOException();
        }
        return keyStore;
    }

    public static KeyStore getTrustStore() throws IOException {
        if (trustStore == null) {
            throw new IOException();
        }
        return trustStore;
    }

    /**
     * Get the SSLContext for c2s connections
     *
     * @return the SSLContext for c2s connections
     */
    public static SSLContext getSSLContext() {
        return context;
    }

    public static void saveStores() throws IOException {
        try {
            keyStore.store(new FileOutputStream(keyStoreLocation), keypass.toCharArray());
            trustStore.store(new FileOutputStream(trustStoreLocation), trustpass.toCharArray());
        }
        catch (IOException e) {
            throw e;
        }
        catch (Exception e) {
            throw new IOException(e.getMessage());
        }
    }

    public static ServerSocket createServerSocket(int port, InetAddress ifAddress) throws
            IOException {
        if (sslFactory == null) {
            throw new IOException();
        }
        else {
            return sslFactory.createServerSocket(port, -1, ifAddress);
        }
    }

    public static String getKeystoreLocation() {
        return keyStoreLocation;
    }

    public static String getTruststoreLocation() {
        return trustStoreLocation;
    }

    public static String getStoreType() {
        return storeType;
    }

    public static SSLJiveServerSocketFactory getServerSocketFactory() {
        return sslFactory;
    }
}