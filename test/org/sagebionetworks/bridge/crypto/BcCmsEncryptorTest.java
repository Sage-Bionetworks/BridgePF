package org.sagebionetworks.bridge.crypto;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.security.KeyPair;
import java.security.cert.X509Certificate;

import org.junit.Before;
import org.junit.Test;

import scala.actors.threadpool.Arrays;

public class BcCmsEncryptorTest {

    private CmsEncryptor encryptor;
    private CmsEncryptor decryptor;

    @Before
    public void before() throws Exception {
        KeyPair keyPair = KeyPairFactory.newRsa2048();
        CertificateFactory certFactory = new BcCertificateFactory();
        X509Certificate cert = certFactory.newCertificate(keyPair);
        encryptor = new BcCmsEncryptor(cert, keyPair.getPrivate());
        decryptor = new BcCmsEncryptor(cert, keyPair.getPrivate());
    }

    @Test
    public void test() throws Exception {
        String text = "some text";
        byte[] bytes = text.getBytes();
        byte[] encrypted = encryptor.encrypt(bytes);
        assertNotNull(encrypted);
        assertFalse(text.equals(new String(encrypted)));
        byte[] decrypted = encryptor.decrypt(encrypted);
        assertEquals(text, new String(decrypted));
    }

    @Test
    public void testDecryptDeterministic() throws Exception {
        String text = "some more text";
        byte[] bytes = text.getBytes("UTF-8");
        byte[] encrypted = encryptor.encrypt(bytes);
        byte[] decrypted = decryptor.decrypt(encrypted);
        assertEquals(text, new String(decrypted, "UTF-8"));
    }

    @Test
    public void testEncryptRandomized() throws Exception {
        String text = "some even more text";
        byte[] bytes = text.getBytes();
        byte[] encrypted1 = encryptor.encrypt(bytes);
        assertNotNull(encrypted1);
        byte[] encrypted2 = encryptor.encrypt(bytes);
        assertNotNull(encrypted2);
        assertFalse(Arrays.equals(encrypted1, encrypted2));
    }
}
