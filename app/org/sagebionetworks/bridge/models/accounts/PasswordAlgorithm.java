package org.sagebionetworks.bridge.models.accounts;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.bouncycastle.crypto.generators.OpenBSDBCrypt;

import org.sagebionetworks.bridge.BridgeUtils;

/** Password hashing algorithms. Encapsulates methods for generating the hash and checking the hash. */
public enum PasswordAlgorithm {
    /**
     * Backwards compatible hashing algorithm provided by Stormpath. This does not meet security standards. Do not use
     * for new passwords.
     */
    STORMPATH_HMAC_SHA_256 {
        /** {@inheritDoc */
        @Override
        public boolean checkHash(String hash, String plaintext) throws InvalidKeyException, NoSuchAlgorithmException {
            // Password is in the form "$stormpath1$[base64-encoded salt]$[base64-encoded hashed password]"
            String[] stormpathHashParts = hash.split("\\$");
            String base64Salt = stormpathHashParts[2];
            byte[] salt = Base64.decodeBase64(base64Salt);
            String base64HashedPassword = stormpathHashParts[3];

            // Generate the base64 hash from the plaintext.
            String base64HashedPlaintext = hashPasswordWithSalt(plaintext, salt);
            return base64HashedPlaintext.equals(base64HashedPassword);
        }

        /** {@inheritDoc */
        @Override
        public String generateHash(String plaintext) throws InvalidKeyException, NoSuchAlgorithmException {
            byte[] salt = BridgeUtils.generateSalt();
            String base64HashedPassword = hashPasswordWithSalt(plaintext, salt);

            // Password is in the form "$stormpath1$[base64-encoded salt]$[base64-encoded hashed password]"
            return "$stormpath1$" + Base64.encodeBase64String(salt) + "$" + base64HashedPassword;
        }

        // Helper method that returns the HMAC hash for a plaintext and salt, without encoding the metadata.
        private String hashPasswordWithSalt(String plaintext, byte[] salt) throws InvalidKeyException,
                NoSuchAlgorithmException {
            Mac hmacSha256 = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(salt, "HmacSHA256");
            hmacSha256.init(secretKey);
            return Base64.encodeBase64String(hmacSha256.doFinal(plaintext.getBytes()));
        }
    },

    /** bcrypt hashing algorithm, which also encodes the salt and the cost in the result. */
    BCRYPT {
        private static final int DEFAULT_COST = 12;

        /** {@inheritDoc */
        @Override
        public boolean checkHash(String hash, String plaintext) {
            return OpenBSDBCrypt.checkPassword(hash, plaintext.toCharArray());
        }

        /** {@inheritDoc */
        @Override
        public String generateHash(String plaintext) {
            return OpenBSDBCrypt.generate(plaintext.toCharArray(), BridgeUtils.generateSalt(), DEFAULT_COST);
        }
    },

    /** PBKDF2 hashing algorithm using HMAC SHA 256. Encodes salt and number of iterations in the result. */
    PBKDF2_HMAC_SHA_256 {
        private static final int DEFAULT_NUM_ITERATIONS = 250000;

        /** {@inheritDoc */
        @Override
        public boolean checkHash(String hash, String plaintext) throws InvalidKeySpecException,
                NoSuchAlgorithmException {
            // Password is in the form "[iterations]$[base64-encoded salt]$[base64-encoded hashed password]"
            String[] hashParts = hash.split("\\$");
            int iterations = Integer.parseInt(hashParts[0]);
            String base64Salt = hashParts[1];
            byte[] salt = Base64.decodeBase64(base64Salt);
            String base64HashedPassword = hashParts[2];

            // Generate the base64 hash from the plaintext.
            String base64HashedPlaintext = hashPasswordWithSalt(plaintext, salt, iterations);
            return base64HashedPlaintext.equals(base64HashedPassword);
        }

        /** {@inheritDoc */
        @Override
        public String generateHash(String plaintext) throws InvalidKeySpecException, NoSuchAlgorithmException {
            byte[] salt = BridgeUtils.generateSalt();
            int iterations = DEFAULT_NUM_ITERATIONS;
            String base64HashedPassword = hashPasswordWithSalt(plaintext, salt, iterations);

            // Output format will be "[iterations]$[base64-encoded salt]$[base64-encoded hashed password]"
            return iterations + "$" + Base64.encodeBase64String(salt) + "$" + base64HashedPassword;
        }

        // Generates the password hash for the given plaintext, salt, and number of iterations. Result does not
        // include the metadata (salt and iterations).
        private String hashPasswordWithSalt(String plaintext, byte[] salt, int iterations)
                throws InvalidKeySpecException, NoSuchAlgorithmException {
            SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            KeySpec keySpec = new PBEKeySpec(plaintext.toCharArray(), salt, iterations, 256);
            byte[] hashedPassword = keyFactory.generateSecret(keySpec).getEncoded();
            return Base64.encodeBase64String(hashedPassword);
        }
    };

    public static final PasswordAlgorithm DEFAULT_PASSWORD_ALGORITHM = PBKDF2_HMAC_SHA_256;

    /** Given a hash with metadata (such as salt, cost, iterations), check whether the given plaintext matches. */
    public abstract boolean checkHash(String hash, String plaintext) throws InvalidKeySpecException,
            InvalidKeyException, NoSuchAlgorithmException;

    /** Generate a hash with metadata (such as salt, cost, iterations) from the given plaintext. */
    public abstract String generateHash(String plaintext) throws InvalidKeySpecException, InvalidKeyException,
            NoSuchAlgorithmException;
}
