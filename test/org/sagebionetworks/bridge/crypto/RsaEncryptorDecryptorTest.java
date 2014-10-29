package org.sagebionetworks.bridge.crypto;

import static org.junit.Assert.assertEquals;

import java.security.KeyPair;
import java.security.SecureRandom;
import java.util.Random;

import org.apache.shiro.codec.Base64;
import org.junit.Before;
import org.junit.Test;

public class RsaEncryptorDecryptorTest {

    private static final Random RANDOM = new SecureRandom();
    private String text;

    @Before
    public void before() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        text = Base64.encodeToString(bytes);
    }

    @Test
    public void testPublicPrivate() {
        KeyPair keyPair = KeyPairFactory.newRsa2048();
        RsaEncryptor encryptor = new RsaEncryptor(keyPair.getPublic());
        RsaDecryptor decryptor = new RsaDecryptor(keyPair.getPrivate());
        String encrypted = encryptor.encrypt(text);
        assertEquals(text, decryptor.decrypt(encrypted));
    }

    @Test
    public void testPrivatePublic() {
        KeyPair keyPair = KeyPairFactory.newRsa2048();
        RsaEncryptor encryptor = new RsaEncryptor(keyPair.getPrivate());
        RsaDecryptor decryptor = new RsaDecryptor(keyPair.getPublic());
        String encrypted = encryptor.encrypt(text);
        assertEquals(text, decryptor.decrypt(encrypted));
    }
}
