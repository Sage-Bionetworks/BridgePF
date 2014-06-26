package org.sagebionetworks.bridge.crypto;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BridgeEncryptorTest {
    @Test
    public void test() {
        final String password = "this is a test password";
        BridgeEncryptor encryptor = new BridgeEncryptor(password);
        BridgeEncryptor decryptor = new BridgeEncryptor(password);
        assertEquals("test something", decryptor.decrypt(encryptor.encrypt("test something")));
    }
}
