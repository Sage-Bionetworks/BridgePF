package org.sagebionetworks.bridge;

import static com.google.common.base.Preconditions.checkArgument;

import java.security.SecureRandom;
import java.util.Objects;
import java.util.Random;

/**
 * This code came from Stack Exchange, with some changes to make it thread-safe. Unfortunately I 
 * then lost the reference to the page I took it from. Cleaned up to our formatting standards.
 */
public class SecureTokenGenerator {

    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    
    public static final SecureTokenGenerator INSTANCE = new SecureTokenGenerator();
    
    public static final SecureTokenGenerator PHONE_CODE_INSTANCE = new SecureTokenGenerator(6, new SecureRandom(), "0123456789");
    
    public static final SecureTokenGenerator NAME_SCOPE_INSTANCE = new SecureTokenGenerator(5, new SecureRandom(), ALPHANUMERIC);

    private final Random random;
    private final char[] characters;
    private final int length;

    private SecureTokenGenerator(int length, Random random, String characters) {
        checkArgument(length > 1);
        checkArgument(characters != null && characters.length() >= 2);

        this.length = length;
        this.random = Objects.requireNonNull(random);
        this.characters = characters.toCharArray();
    }

    /**
     * Create session identifiers. This is 4.36e+37 unique values, which is enough
     * for a good session key.
     */
    private SecureTokenGenerator() {
        this(21, new SecureRandom(), ALPHANUMERIC);
    }
    
    public String nextToken() {
        final char[] buffer = new char[length];
        for (int i = 0; i < buffer.length; ++i) {
            buffer[i] = characters[random.nextInt(characters.length)];
        }
        return new String(buffer);
    }
}
