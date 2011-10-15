/**
 * $RCSfile$
 * $Revision: 1217 $
 * $Date: 2005-04-11 18:11:06 -0300 (Mon, 11 Apr 2005) $
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

package org.jivesoftware.multiplexer.net;

import org.jivesoftware.util.Log;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.security.KeyStore;

/**
 * Securue socket factory wrapper allowing simple setup of all security
 * SSL related parameters.<p>
 *
 * This class was copied from Openfire.
 *
 * @author Gaston Dombiak
 */
public class SSLJiveServerSocketFactory extends SSLServerSocketFactory {

    public static SSLServerSocketFactory getInstance(String algorithm,
                                                     KeyStore keystore,
                                                     KeyStore truststore) throws
            IOException {

        try {
            SSLContext sslcontext = SSLContext.getInstance(algorithm);
            SSLServerSocketFactory factory;
            KeyManagerFactory keyFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyFactory.init(keystore, SSLConfig.getKeyPassword().toCharArray());
            TrustManagerFactory trustFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustFactory.init(truststore);

            sslcontext.init(keyFactory.getKeyManagers(),
                    trustFactory.getTrustManagers(),
                    new java.security.SecureRandom());
            factory = sslcontext.getServerSocketFactory();
            return new SSLJiveServerSocketFactory(factory);
        }
        catch (Exception e) {
            Log.error(e);
            throw new IOException(e.getMessage());
        }
    }

    private SSLServerSocketFactory factory;

    private SSLJiveServerSocketFactory(SSLServerSocketFactory factory) {
        this.factory = factory;
    }

    @Override
	public ServerSocket createServerSocket(int i) throws IOException {
        return factory.createServerSocket(i);
    }

    @Override
	public ServerSocket createServerSocket(int i, int i1) throws IOException {
        return factory.createServerSocket(i, i1);
    }

    @Override
	public ServerSocket createServerSocket(int i, int i1, InetAddress inetAddress) throws IOException {
        return factory.createServerSocket(i, i1, inetAddress);
    }

    @Override
	public String[] getDefaultCipherSuites() {
        return factory.getDefaultCipherSuites();
    }

    @Override
	public String[] getSupportedCipherSuites() {
        return factory.getSupportedCipherSuites();
    }
}
