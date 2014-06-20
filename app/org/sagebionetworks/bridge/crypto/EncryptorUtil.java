package org.sagebionetworks.bridge.crypto;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jasypt.encryption.StringEncryptor;
import org.jasypt.encryption.pbe.PBEStringEncryptor;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.jasypt.salt.RandomSaltGenerator;
import org.jasypt.salt.StringFixedSaltGenerator;

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
        final StringEncryptor encryptor = getEncryptor(password);
        if (args.length == 2 || "encrypt".equalsIgnoreCase(args[2])) {
            System.out.println("Encrypted: " + encryptor.encrypt(args[1]));
        } else {
            System.out.println("Decrypted: " + encryptor.decrypt(args[1]));
        }
    }

    /**
     * Gets a password-based string encryptor.
     */
    @Deprecated
    public static PBEStringEncryptor getEncryptorOld(final String password, final String salt) {
        StandardPBEStringEncryptor encryptor = new StandardPBEStringEncryptor();
        encryptor.setPassword(password);
        encryptor.setSaltGenerator(new StringFixedSaltGenerator(salt));
        return encryptor;
    }

    /**
     * Gets a password-based string encryptor.
     */
    public static PBEStringEncryptor getEncryptor(final String password) {
        StandardPBEStringEncryptor encryptor = new StandardPBEStringEncryptor();
        encryptor.setProvider(new BouncyCastleProvider());
        encryptor.setAlgorithm("PBEWITHSHAAND256BITAES-CBC-BC");
        encryptor.setPassword(password);
        encryptor.setSaltGenerator(new RandomSaltGenerator());
        return encryptor;
    }
}
