package org.sagebionetworks.bridge;

import java.security.SecureRandom;
import java.util.Iterator;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;

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
        // Looping until we have 4 indices can take a long time or hang if length is <4, prevent either
        Preconditions.checkArgument(length >= 16);
        
        final char[] buffer = new char[length];
        for (int i = 0; i < buffer.length; ++i) {
            buffer[i] = ALPHANUMERIC_ARRAY[RANDOM.nextInt(ALPHANUMERIC_ARRAY.length)];
        }
        // ensure that all character types are always present
        Iterator<Integer> indices = getFourUniqueIntegers(length).iterator();
        replace(buffer, UPPERCASE_ARRAY, indices.next());
        replace(buffer, LOWERCASE_ARRAY, indices.next());
        replace(buffer, NUMERIC_ARRAY, indices.next());
        replace(buffer, SYMBOLIC_ARRAY, indices.next());
        return new String(buffer);
    }
    
    private Set<Integer> getFourUniqueIntegers(int max) {
        Set<Integer> set = Sets.newHashSetWithExpectedSize(4);
        while (set.size() < 4) {
            set.add(RANDOM.nextInt(max));
        }
        return set;
    }
    
    /** Insert a randomly selected character at position in the array */
    private void replace(char[] buffer, char[] charArray, int pos) {
        buffer[pos] = charArray[RANDOM.nextInt(charArray.length)];
    }

}
