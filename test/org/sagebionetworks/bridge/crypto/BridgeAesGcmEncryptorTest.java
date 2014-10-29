package org.sagebionetworks.bridge.crypto;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public class BridgeAesGcmEncryptorTest {

    @Test
    public void test() {
        BridgeAesGcmEncryptor encryptor = new BridgeAesGcmEncryptor("XJHe3omwqIHDQ+Dr2EFnEoX+TlDuAZLrgOeE0TCPyq0=");
        assertEquals("a", encryptor.decrypt(encryptor.encrypt("a")));
        assertEquals("Encrypt me", encryptor.decrypt(encryptor.encrypt("Encrypt me")));
        assertEquals("$%*&^()!!@", encryptor.decrypt(encryptor.encrypt("$%*&^()!!@")));
    }

    @Test
    public void testEmpty() {
        BridgeAesGcmEncryptor encryptor = new BridgeAesGcmEncryptor();
        assertEquals("", encryptor.decrypt(encryptor.encrypt("")));
    }

    @Test
    public void testNonAscii() {
        BridgeAesGcmEncryptor encryptor = new BridgeAesGcmEncryptor("F0fvlKS72QYbyQZK6y+DCHMSMdvEYiIMZQXm3ST6YrQ=");
        assertEquals("\u673A\u5BC6\u6587\u4EF6\u9500\u6BC1\u3002",
                encryptor.decrypt(encryptor.encrypt("\u673A\u5BC6\u6587\u4EF6\u9500\u6BC1\u3002")));
    }

    @Test
    public void testEncryptRandomized() {
        BridgeAesGcmEncryptor encryptor = new BridgeAesGcmEncryptor("QDA7jti2QrJ5gzbkdTn+rcckcDJzFZ4KwgVjlMhpLUw=");
        assertFalse("Encryption should be randomized.", encryptor.encrypt("Encrypt me").equals(encryptor.encrypt("Encrypt me")));
    }

    @Test
    public void testDecryptDeterministic() {
        BridgeAesGcmEncryptor encryptor1 = new BridgeAesGcmEncryptor("jVoKFK0fxGPdDsWKZHSxIGR0P/QDUUEGpnetUf2jtDs=");
        BridgeAesGcmEncryptor encryptor2 = new BridgeAesGcmEncryptor("jVoKFK0fxGPdDsWKZHSxIGR0P/QDUUEGpnetUf2jtDs=");
        assertEquals("Decryption should be deterministic.", "Encrypt me", encryptor2.decrypt(encryptor1.encrypt("Encrypt me")));
    }

    @Test(expected=IllegalArgumentException.class)
    public void testNullEncrypt() {
        BridgeAesGcmEncryptor encryptor = new BridgeAesGcmEncryptor();
        encryptor.encrypt(null);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testNullDecrypt() {
        BridgeAesGcmEncryptor encryptor = new BridgeAesGcmEncryptor();
        encryptor.decrypt(null);
    }
}
