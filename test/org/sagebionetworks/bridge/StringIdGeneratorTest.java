package org.sagebionetworks.bridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.when;

import java.security.SecureRandom;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class StringIdGeneratorTest {

    private StringIdGenerator generator;
    
    @Mock
    private SecureRandom random;
    
    @Before
    public void before() {
        generator = new StringIdGenerator();
    }
    
    @Test
    public void testNoArgConstructor() {
        String value = generator.nextString();
        
        assertEquals(21, value.length());
        assertNotEquals(value, generator.nextString());
    }
    
    @Test
    public void testFullConstructor() {
        when(random.nextInt(7)).thenReturn(3);
        
        generator = new StringIdGenerator(10, random, "ABCDEFG");
        assertEquals("DDDDDDDDDD", generator.nextString());
        assertEquals("DDDDDDDDDD", generator.nextString());
    }
    
    @Test
    public void nextStringDifferent() {
        assertNotEquals(generator.nextString(), generator.nextString());
    }
    
}
