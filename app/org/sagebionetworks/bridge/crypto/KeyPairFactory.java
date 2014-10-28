package org.sagebionetworks.bridge.crypto;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class KeyPairFactory {

    private static final KeyPairGenerator rsa2048;
    static {
        try {
            rsa2048 = KeyPairGenerator.getInstance("RSA");
            SecureRandom random = SecureRandom.getInstance("NativePRNG");
            rsa2048.initialize(2048, random);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static KeyPair newRsa2048() {
        return rsa2048.generateKeyPair();
    }
}
