package org.sagebionetworks.bridge.crypto;

import java.nio.charset.StandardCharsets;
import java.security.Security;

import org.apache.shiro.codec.Base64;
import org.apache.shiro.crypto.AesCipherService;
import org.apache.shiro.crypto.OperationMode;
import org.apache.shiro.crypto.PaddingScheme;
import org.apache.shiro.util.ByteSource;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

public class AesGcmEncryptor {

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new RuntimeException("Must supply at least 2 parameters: "
                    + "key, target, decrypt (optional, when missing, encrypt)");
        }
        final String key = args[0];
        final AesGcmEncryptor encryptor = new AesGcmEncryptor(key);
        if (args.length == 2 || "encrypt".equalsIgnoreCase(args[2])) {
            System.out.println("Encrypted: " + encryptor.encrypt(args[1]));
        } else {
            System.out.println("Decrypted: " + encryptor.decrypt(args[1]));
        }
    }

    AesGcmEncryptor() {
        aesCipher = createCipher();
        key = Base64.encodeToString(aesCipher.generateNewKey(KEY_BIT_SIZE).getEncoded());
    }

    public AesGcmEncryptor(String key) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Key must not be null or empty.");
        }
        this.key = key;
        aesCipher = createCipher();
    }

    public String encrypt(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Text to encrypt cannot be null.");
        }
        byte[] base64 = Base64.encode(text.getBytes(StandardCharsets.UTF_8));
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

    private AesCipherService createCipher() {
        Security.addProvider(new BouncyCastleProvider());
        AesCipherService cipher = new AesCipherService();
        cipher.setKeySize(KEY_BIT_SIZE);
        cipher.setMode(OperationMode.GCM);
        cipher.setPaddingScheme(PaddingScheme.NONE);
        return cipher;
    }

    private static final int KEY_BIT_SIZE = 256;
    private final AesCipherService aesCipher;
    private final String key;
}
