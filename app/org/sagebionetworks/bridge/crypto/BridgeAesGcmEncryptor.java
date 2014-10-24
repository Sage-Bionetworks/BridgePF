package org.sagebionetworks.bridge.crypto;

import java.security.Security;

import org.apache.shiro.authc.credential.DefaultPasswordService;
import org.apache.shiro.codec.Base64;
import org.apache.shiro.crypto.AesCipherService;
import org.apache.shiro.crypto.OperationMode;
import org.apache.shiro.crypto.PaddingScheme;
import org.apache.shiro.crypto.hash.Hash;
import org.apache.shiro.util.ByteSource;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

public class BridgeAesGcmEncryptor {

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new RuntimeException("Must supply at least 2 parameters: "
                    + "password, target, decrypt (optional, when missing, encrypt)");
        }
        final String password = args[0];
        final BridgeAesGcmEncryptor encryptor = new BridgeAesGcmEncryptor(password);
        if (args.length == 2 || "encrypt".equalsIgnoreCase(args[2])) {
            System.out.println("Encrypted: " + encryptor.encrypt(args[1]));
        } else {
            System.out.println("Decrypted: " + encryptor.decrypt(args[1]));
        }
    }

    public BridgeAesGcmEncryptor(String password) {

        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password must not be null or empty.");
        }

        Security.addProvider(new BouncyCastleProvider());

        DefaultPasswordService pwdService = new DefaultPasswordService();
        Hash hash = pwdService.hashPassword(password);
        key = hash.toBase64();

        aesCipher = new AesCipherService();
        aesCipher.setKeySize(256);
        aesCipher.setMode(OperationMode.GCM);
        aesCipher.setPaddingScheme(PaddingScheme.NONE);
    }

    public String encrypt(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Text to encrypt cannot be null.");
        }
        byte[] base64 = Base64.encode(text.getBytes());
        ByteSource bytes = aesCipher.encrypt(base64, Base64.decode(key));
        return bytes.toBase64();
    }

    public String decrypt(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Text to decrypt cannot be null.");
        }
        ByteSource bytes = aesCipher.decrypt(Base64.decode(text), Base64.decode(key));
        return Base64.decodeToString(bytes.getBytes());
    }

    private final AesCipherService aesCipher;
    private final String key;
}
