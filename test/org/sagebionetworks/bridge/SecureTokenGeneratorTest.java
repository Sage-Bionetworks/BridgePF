package org.sagebionetworks.bridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class SecureTokenGeneratorTest {

    @Test
    public void testNoArgConstructor() {
        String value = SecureTokenGenerator.INSTANCE.nextToken();
        
        assertEquals(21, value.length());
        assertNotEquals(value, SecureTokenGenerator.INSTANCE.nextToken());
    }
    
    @Test
    public void nextStringDifferent() {
        assertNotEquals(SecureTokenGenerator.INSTANCE.nextToken(), SecureTokenGenerator.INSTANCE.nextToken());
    }
    
    @Test
    public void phoneCodeString() {
        String token = SecureTokenGenerator.PHONE_CODE_INSTANCE.nextToken();
        assertEquals(6, token.length());
        assertTrue(token.matches("^\\d+$")); // composed only of digits
    }
    
}
