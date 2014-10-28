package org.sagebionetworks.bridge.crypto;

import java.security.KeyPair;
import java.security.SecureRandom;

import org.apache.shiro.codec.Base64;
import org.junit.Before;
import org.junit.Test;

public class RsaEncryptorDecryptorTest {

    private static final SecureRandom SR = new SecureRandom();
    private String randomText;

    @Before
    public void before() {
        byte[] bytes = new byte[128];
        SR.nextBytes(bytes);
        randomText = Base64.encodeToString(bytes);
    }

    @Test
    public void testPublicPrivate() {
        KeyPair keyPair = KeyPairFactory.newRsa2048();
        RsaEncryptor encryptor = new RsaEncryptor(keyPair.getPublic());
        RsaDecryptor decryptor = new RsaDecryptor(keyPair.getPrivate());
        String encrypted = encryptor.encrypt(randomText);
        System.out.println(encrypted);
        System.out.println(decryptor.decrypt(encrypted));
    }

    @Test
    public void testPrivatePublic() {
        KeyPair keyPair = KeyPairFactory.newRsa2048();
        RsaEncryptor encryptor = new RsaEncryptor(keyPair.getPrivate());
        RsaDecryptor decryptor = new RsaDecryptor(keyPair.getPublic());
        String encrypted = encryptor.encrypt(randomText);
        System.out.println(encrypted);
        System.out.println(decryptor.decrypt(encrypted));
    }
}
