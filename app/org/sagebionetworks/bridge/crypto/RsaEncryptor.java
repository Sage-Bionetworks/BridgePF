package org.sagebionetworks.bridge.crypto;

import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import org.apache.shiro.codec.Base64;

public class RsaEncryptor {

    public RsaEncryptor(Key key) {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null.");
        }
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            cipher.init(Cipher.DECRYPT_MODE, key);
        } catch(NoSuchAlgorithmException e) {
            throw new IllegalArgumentException(e);
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException(e);
        } catch (NoSuchPaddingException e) {
            throw new IllegalArgumentException(e);
        }
        this.key = key;
    }

    public String encrypt(String base64Encoded) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            return cipherDoFinal(cipher, base64Encoded);
        } catch (InvalidKeyException e) {
            throw new RuntimeException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (NoSuchPaddingException e) {
            throw new RuntimeException(e);
        }
    }

    public String decrypt(String base64Encoded) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key);
            return cipherDoFinal(cipher, base64Encoded);
        } catch (InvalidKeyException e) {
            throw new RuntimeException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (NoSuchPaddingException e) {
            throw new RuntimeException(e);
        }
    }

    private String cipherDoFinal(Cipher cipher, String base64Encoded) {
        try {
            byte[] encrypted = cipher.doFinal(Base64.decode(base64Encoded));
            return Base64.encodeToString(encrypted);
        } catch(BadPaddingException e) {
            throw new RuntimeException(e);
        } catch (IllegalBlockSizeException e) {
            throw new RuntimeException(e);
        }
    }

    private static final String ALGORITHM = "RSA/ECB/PKCS1Padding";
    private final Key key;
}
