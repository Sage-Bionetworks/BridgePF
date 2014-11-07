package org.sagebionetworks.bridge.crypto;

import static org.junit.Assert.assertNotNull;

import java.security.KeyPair;
import java.security.cert.X509Certificate;

import org.junit.Test;

public class BcCertificateFactoryTest {

    @Test
    public void test() throws Exception {
        CertificateFactory certFactory = new BcCertificateFactory();
        KeyPair keyPair1 = KeyPairFactory.newRsa2048();
        X509Certificate cert1 = certFactory.newCertificate(keyPair1);
        assertNotNull(cert1);
        cert1.checkValidity();
        cert1.verify(keyPair1.getPublic(), "BC");
        String pem1 = CertificateUtils.toPem(cert1);
        assertNotNull(pem1);
        KeyPair keyPair2 = KeyPairFactory.newRsa2048();
        X509Certificate cert2 = certFactory.newCertificate(keyPair2.getPublic(), cert1, keyPair1.getPrivate());
        assertNotNull(cert2);
        cert2.checkValidity();
        cert2.verify(keyPair1.getPublic(), "BC");
        String pem2 = CertificateUtils.toPem(cert2);
        assertNotNull(pem2);
    }
}
