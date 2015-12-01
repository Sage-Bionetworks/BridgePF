package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Set;

import org.junit.Test;

import com.google.common.collect.Sets;

public class OptionLookupTest {

    @Test
    public void nullDefaultIsOK() {
        OptionLookup lookup = new OptionLookup(null);
        assertNull(lookup.get("AAA"));
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
    
    @Test
    public void correctlySetsAndGetsStringSet() {
        OptionLookup lookup = new OptionLookup(null);
        lookup.put("AAA", "A,B,C");
        
        Set<String> set = lookup.getDataGroups("AAA");
        assertEquals(Sets.newHashSet("A","B","C"), set);
        
        set = lookup.getDataGroups("BBB");
        assertEquals(Sets.newHashSet(), set);
    }
}
