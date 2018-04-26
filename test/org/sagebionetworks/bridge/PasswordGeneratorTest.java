package org.sagebionetworks.bridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.regex.Pattern;

import org.junit.Test;

public class PasswordGeneratorTest {

    @Test
    public void generatesPasswordCorrectLength() {
        assertEquals(16,PasswordGenerator.INSTANCE.nextPassword(16).length());
        assertEquals(101,PasswordGenerator.INSTANCE.nextPassword(101).length());
    }
    
    @Test
    public void containsEveryClassOfCharacter() {
        String password = PasswordGenerator.INSTANCE.nextPassword(32);
        assertTrue(password.matches(".*["+Pattern.quote("!#$%&'()*+,-./:;<=>?@[]^_`{|}~")+"].*"));
        assertTrue(password.matches(".*[A-Z].*"));
        assertTrue(password.matches(".*[a-z].*"));
        assertTrue(password.matches(".*[0-9].*"));
    }
}
