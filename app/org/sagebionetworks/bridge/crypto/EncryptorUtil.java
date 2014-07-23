package org.sagebionetworks.bridge.crypto;

import org.jasypt.encryption.StringEncryptor;

public class EncryptorUtil {

    /**
     * arg0: password,
     * arg1: target value
     * arg2: optional, encrypt or decrypt, encrypt is the default
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            throw new RuntimeException("Must supply at least 2 parameters: "
                    + "password, target, decrypt (optional, when missing, encrypt)");
        }
        final String password = args[0];
        final StringEncryptor encryptor = new BridgeEncryptor(password);
        if (args.length == 2 || "encrypt".equalsIgnoreCase(args[2])) {
            System.out.println("Encrypted: " + encryptor.encrypt(args[1]));
        } else {
            System.out.println("Decrypted: " + encryptor.decrypt(args[1]));
        }
    }
}
