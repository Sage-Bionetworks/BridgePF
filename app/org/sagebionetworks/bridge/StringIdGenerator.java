package org.sagebionetworks.bridge;

import java.security.SecureRandom;
import java.util.Objects;
import java.util.Random;

/**
 * This code came from Stack Exchange, reformatted to our standards. Unfortunately I then lost 
 * the reference to the page I took it from. Cleaned up to our formatting standards.
 */
class StringIdGenerator {

    private static final String ALPHANUM = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private final Random random;
    private final char[] symbols;
    private final int length;

    StringIdGenerator(int length, Random random, String symbols) {
        if (length < 1) {
            throw new IllegalArgumentException();
        }
        if (symbols.length() < 2) {
            throw new IllegalArgumentException();
        }
        this.length = length;
        this.random = Objects.requireNonNull(random);
        this.symbols = symbols.toCharArray();
    }

    /**
     * Create session identifiers. This is 4.36e+37 unique values, which is enough
     * for a good session key.
     */
    StringIdGenerator() {
        this(21, new SecureRandom(), ALPHANUM);
    }
    
    String nextString() {
        final char[] buffer = new char[length];
        for (int idx = 0; idx < buffer.length; ++idx) {
            buffer[idx] = symbols[random.nextInt(symbols.length)];
        }
        return new String(buffer);
    }
}
