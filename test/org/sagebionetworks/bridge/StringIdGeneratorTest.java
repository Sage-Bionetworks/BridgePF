package org.sagebionetworks.bridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.security.SecureRandom;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class StringIdGeneratorTest {

    @Mock
    private SecureRandom random;
    
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
    
}
