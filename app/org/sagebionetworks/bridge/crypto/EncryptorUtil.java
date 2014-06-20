package org.sagebionetworks.bridge.crypto;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jasypt.encryption.StringEncryptor;
import org.jasypt.encryption.pbe.PBEStringEncryptor;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.jasypt.salt.StringFixedSaltGenerator;

public class EncryptorUtil {

    /**
     * arg0: password,
     * arg1: salt
     * arg2: target value
     * arg3: encrypt or decrypt, encrypt is the default
     */
    public static void main(String[] args) {
        if (args.length < 3) {
            throw new RuntimeException("Must supply at least 3 parameters: "
                    + "password, salt, target, decrypt (optional, when missing, encrypt)");
        }
        final String password = args[0];
        final String salt = args[1];
        final StringEncryptor encryptor = getEncryptor(password, salt);
        if (args.length == 3 || "encrypt".equalsIgnoreCase(args[3])) {
            System.out.println("Encrypted: " + encryptor.encrypt(args[2]));
        } else {
            System.out.println("Decrypted: " + encryptor.decrypt(args[2]));
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
    public static PBEStringEncryptor getEncryptor(final String password, final String salt) {
        StandardPBEStringEncryptor encryptor = new StandardPBEStringEncryptor();
        encryptor.setProvider(new BouncyCastleProvider());
        encryptor.setAlgorithm("PBEWITHSHAAND256BITAES-CBC-BC");
        encryptor.setPassword(password);
        encryptor.setSaltGenerator(new StringFixedSaltGenerator(salt));
        return encryptor;
    }
}
