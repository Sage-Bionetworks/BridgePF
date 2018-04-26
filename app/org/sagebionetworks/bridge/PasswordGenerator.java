package org.sagebionetworks.bridge;

import java.security.SecureRandom;

public class PasswordGenerator {
    public static final PasswordGenerator INSTANCE = new PasswordGenerator();

    private static final SecureRandom RANDOM = new SecureRandom();
    
    private static final String UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWERCASE = "abcdefghijklmnopqrstuvwxyz";
    private static final String NUMERIC = "0123456789";
    private static final String SYMBOLIC = "!#$%&'()*+,-./:;<=>?@[]^_`{|}~";
    
    private static final char[] UPPERCASE_ARRAY = UPPERCASE.toCharArray();
    private static final char[] LOWERCASE_ARRAY = LOWERCASE.toCharArray();
    private static final char[] NUMERIC_ARRAY = NUMERIC.toCharArray();
    private static final char[] SYMBOLIC_ARRAY = SYMBOLIC.toCharArray();
    private static final char[] ALPHANUMERIC_ARRAY = (UPPERCASE+LOWERCASE+NUMERIC).toCharArray();
    
    private PasswordGenerator() {
    }
    
    public String nextPassword(int length) {
        final char[] buffer = new char[length];
        for (int i = 0; i < buffer.length; ++i) {
            buffer[i] = ALPHANUMERIC_ARRAY[RANDOM.nextInt(ALPHANUMERIC_ARRAY.length)];
        }
        // absolutely ensure that all character types are present, and add a symbol
        replace(buffer, UPPERCASE_ARRAY);
        replace(buffer, LOWERCASE_ARRAY);
        replace(buffer, NUMERIC_ARRAY);
        replace(buffer, SYMBOLIC_ARRAY);
        return new String(buffer);
    }
    
    /** Select a position at random and put the string character there */
    private void replace(char[] buffer, char[] charSet) {
        char charAt = charSet[RANDOM.nextInt(charSet.length)];
        int pos = RANDOM.nextInt(buffer.length);
        buffer[pos] = charAt;
    }

}
