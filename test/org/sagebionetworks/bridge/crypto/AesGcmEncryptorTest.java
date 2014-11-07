package org.sagebionetworks.bridge.crypto;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public class AesGcmEncryptorTest {

    @Test
    public void test() {
        AesGcmEncryptor encryptor = new AesGcmEncryptor("XJHe3omwqIHDQ+Dr2EFnEoX+TlDuAZLrgOeE0TCPyq0=");
        assertEquals("a", encryptor.decrypt(encryptor.encrypt("a")));
        assertEquals("Encrypt me", encryptor.decrypt(encryptor.encrypt("Encrypt me")));
        assertEquals("$%*&^()!!@", encryptor.decrypt(encryptor.encrypt("$%*&^()!!@")));
    }

    @Test
    public void testEmpty() {
        AesGcmEncryptor encryptor = new AesGcmEncryptor();
        assertEquals("", encryptor.decrypt(encryptor.encrypt("")));
    }

    @Test
    public void testNonAscii() {
        AesGcmEncryptor encryptor = new AesGcmEncryptor("F0fvlKS72QYbyQZK6y+DCHMSMdvEYiIMZQXm3ST6YrQ=");
        assertEquals("\u673A\u5BC6\u6587\u4EF6\u9500\u6BC1\u3002",
                encryptor.decrypt(encryptor.encrypt("\u673A\u5BC6\u6587\u4EF6\u9500\u6BC1\u3002")));
    }

    @Test
    public void testEncryptRandomized() {
        AesGcmEncryptor encryptor = new AesGcmEncryptor("QDA7jti2QrJ5gzbkdTn+rcckcDJzFZ4KwgVjlMhpLUw=");
        assertFalse("Encryption should be randomized.", encryptor.encrypt("Encrypt me").equals(encryptor.encrypt("Encrypt me")));
    }

    @Test
    public void testDecryptDeterministic() {
        AesGcmEncryptor encryptor1 = new AesGcmEncryptor("jVoKFK0fxGPdDsWKZHSxIGR0P/QDUUEGpnetUf2jtDs=");
        AesGcmEncryptor encryptor2 = new AesGcmEncryptor("jVoKFK0fxGPdDsWKZHSxIGR0P/QDUUEGpnetUf2jtDs=");
        assertEquals("Decryption should be deterministic.", "Encrypt me", encryptor2.decrypt(encryptor1.encrypt("Encrypt me")));
    }

    @Test(expected=IllegalArgumentException.class)
    public void testNullEncrypt() {
        AesGcmEncryptor encryptor = new AesGcmEncryptor();
        encryptor.encrypt(null);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testNullDecrypt() {
        AesGcmEncryptor encryptor = new AesGcmEncryptor();
        encryptor.decrypt(null);
    }
}
