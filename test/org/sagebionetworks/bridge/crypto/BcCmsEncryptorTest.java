package org.sagebionetworks.bridge.crypto;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.security.KeyPair;
import java.security.cert.X509Certificate;

import org.apache.shiro.codec.Base64;
import org.junit.Before;
import org.junit.Test;

public class BcCmsEncryptorTest {

    private CmsEncryptor encryptor;

    @Before
    public void before() throws Exception {
        KeyPair keyPair = KeyPairFactory.newRsa2048();
        CertificateFactory certFactory = new BcCertificateFactory();
        X509Certificate cert = certFactory.newCertificate(keyPair);
        encryptor = new BcCmsEncryptor(cert, keyPair.getPrivate());
    }

    @Test
    public void test() throws Exception {
        String text = "some text";
        String base64Encoded = Base64.encodeToString(text.getBytes());
        assertEquals(base64Encoded, encryptor.decrypt(encryptor.encrypt(base64Encoded)));
    }

    @Test
    public void testEncryptRandomized() throws Exception {
        String text = "some more text";
        String base64Encoded = Base64.encodeToString(text.getBytes());
        assertFalse(encryptor.encrypt(base64Encoded).equals(encryptor.encrypt(base64Encoded)));
    }
}
