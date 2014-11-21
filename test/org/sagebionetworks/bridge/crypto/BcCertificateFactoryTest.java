package org.sagebionetworks.bridge.crypto;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.security.KeyPair;
import java.security.cert.X509Certificate;

import org.bouncycastle.asn1.x509.Extension;
import org.junit.Test;

public class BcCertificateFactoryTest {

    @Test
    public void test() throws Exception {

        final CertificateFactory certFactory = new BcCertificateFactory();
        KeyPair keyPair1 = KeyPairFactory.newRsa2048();
        final X509Certificate cert1 = certFactory.newCertificate(keyPair1, "*");
        assertNotNull(cert1);
        cert1.checkValidity();
        cert1.verify(keyPair1.getPublic(), BcCmsConstants.PROVIDER);

        assertNotNull(cert1.getExtensionValue(Extension.basicConstraints.getId()));
        assertTrue(cert1.getBasicConstraints() >= 0);
        assertNotNull(cert1.getExtensionValue(Extension.authorityKeyIdentifier.getId()));
        assertNotNull(cert1.getExtensionValue(Extension.subjectKeyIdentifier.getId()));

        KeyPair keyPair2 = KeyPairFactory.newRsa2048();
        final X509Certificate cert2 = certFactory.newCertificate(
                keyPair2.getPublic(), cert1, keyPair1.getPrivate());
        assertNotNull(cert2);
        cert2.checkValidity();
        cert2.verify(keyPair1.getPublic(), "BC");

        String pem1 = PemUtils.toPem(cert1);
        assertNotNull(pem1);
        String pem2 = PemUtils.toPem(cert2);
        assertNotNull(pem2);
    }
}
