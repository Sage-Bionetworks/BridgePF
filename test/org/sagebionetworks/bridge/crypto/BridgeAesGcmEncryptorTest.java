package org.sagebionetworks.bridge.crypto;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public class BridgeAesGcmEncryptorTest {

    @Test
    public void test() {
        BridgeAesGcmEncryptor encryptor = new BridgeAesGcmEncryptor("a test password");
        assertEquals("a", encryptor.decrypt(encryptor.encrypt("a")));
        assertEquals("Encrypt me", encryptor.decrypt(encryptor.encrypt("Encrypt me")));
        assertEquals("$%*&^()!!@", encryptor.decrypt(encryptor.encrypt("$%*&^()!!@")));
    }

    @Test
    public void testEmpty() {
        BridgeAesGcmEncryptor encryptor = new BridgeAesGcmEncryptor("password");
        assertEquals("", encryptor.decrypt(encryptor.encrypt("")));
    }

    @Test
    public void testNonAscii() {
        BridgeAesGcmEncryptor encryptor = new BridgeAesGcmEncryptor("!#password#!");
        assertEquals("\u673A\u5BC6\u6587\u4EF6\u9500\u6BC1\u3002",
                encryptor.decrypt(encryptor.encrypt("\u673A\u5BC6\u6587\u4EF6\u9500\u6BC1\u3002")));
    }

    @Test
    public void testEncryptNoRepeat() {
        BridgeAesGcmEncryptor encryptor = new BridgeAesGcmEncryptor("a test password");
        assertFalse(encryptor.encrypt("Encrypt me").equals(encryptor.encrypt("Encrypt me")));
    }

    @Test(expected=IllegalArgumentException.class)
    public void testNullEncrypt() {
        BridgeAesGcmEncryptor encryptor = new BridgeAesGcmEncryptor("!#password#!");
        encryptor.encrypt(null);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testNullDecrypt() {
        BridgeAesGcmEncryptor encryptor = new BridgeAesGcmEncryptor("!#password#!");
        encryptor.decrypt(null);
    }
}
