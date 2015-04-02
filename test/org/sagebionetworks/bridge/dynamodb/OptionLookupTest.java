package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

public class OptionLookupTest {

    @Test
    public void mustProvideDefaultValue() {
        try {
            new OptionLookup(null);
            fail("Should have thrown exception");
        } catch(IllegalArgumentException e) {
            assertEquals("defaultValue cannot be missing, null, or blank", e.getMessage());
        }
    }
    
    @Test
    public void returnsDefaultValueWhenNothingSet() {
        OptionLookup lookup = new OptionLookup("defaultValue");
        
        String value = lookup.get("AAA");
        assertEquals("defaultValue", value);
    }
    
    @Test
    public void returnsDefaultValueWhenNullSet() {
        OptionLookup lookup = new OptionLookup("defaultValue");
        lookup.put("AAA", null);
        
        String value = lookup.get("AAA");
        assertEquals("defaultValue", value);
    }
    
    @Test
    public void returnsDefaultValueWhenEmptySet() {
        OptionLookup lookup = new OptionLookup("defaultValue");
        lookup.put("AAA", "");
        
        String value = lookup.get("AAA");
        assertEquals("defaultValue", value);
    }
    
    @Test
    public void returnsValueWhenSet() {
        OptionLookup lookup = new OptionLookup("defaultValue");
        lookup.put("AAA", "setValue");
        
        String value = lookup.get("AAA");
        assertEquals("setValue", value);
    }
}
