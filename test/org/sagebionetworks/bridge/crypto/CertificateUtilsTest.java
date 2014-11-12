package org.sagebionetworks.bridge.crypto;

import static org.junit.Assert.assertNotNull;

import java.security.KeyPair;
import java.security.cert.X509Certificate;

import org.junit.Test;

public class CertificateUtilsTest {

    @Test
    public void test() throws Exception {
        CertificateFactory certFactory = new BcCertificateFactory();
        KeyPair keyPair = KeyPairFactory.newRsa2048();
        X509Certificate cert = certFactory.newCertificate(keyPair);
        String pem = CertificateUtils.toPem(cert);
        assertNotNull(pem);
        cert = CertificateUtils.fromPem(pem);
        cert.checkValidity();
        cert.verify(keyPair.getPublic(), "BC");
    }
}
