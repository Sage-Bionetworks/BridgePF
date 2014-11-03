package org.sagebionetworks.bridge.crypto;

import static org.junit.Assert.assertNotNull;

import java.security.KeyPair;
import java.security.cert.X509Certificate;

import org.junit.Test;

public class BcCertificateFactoryTest {

    @Test
    public void test() {
        CertificateFactory certFactory = new BcCertificateFactory();
        KeyPair keyPair1 = KeyPairFactory.newRsa2048();
        X509Certificate cert1 = certFactory.newCertificate(keyPair1);
        assertNotNull(cert1);
        KeyPair keyPair2 = KeyPairFactory.newRsa2048();
        X509Certificate cert2 = certFactory.newCertificate(keyPair2.getPublic(), cert1, keyPair1.getPrivate());
        assertNotNull(cert2);
    }
}
