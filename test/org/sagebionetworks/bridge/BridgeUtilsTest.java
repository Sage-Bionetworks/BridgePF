package org.sagebionetworks.bridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.Map;
import java.util.Set;

import org.junit.Test;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class BridgeUtilsTest {

    @Test
    public void templateResolverWorks() {
        Map<String,String> map = Maps.newHashMap();
        map.put("baz", "Belgium");
        map.put("box", "Albuquerque");
        map.put("foo", "This is unused");
        
        // In particular, verifying that replacement of multiple identical tokens occurs,
        // unmatched variables are left alone
        String result = BridgeUtils.resolveTemplate("foo ${baz} bar ${baz} ${box} ${unused}", map);
        assertEquals("foo Belgium bar Belgium Albuquerque ${unused}", result);
    }
    
    @Test
    public void templateResolverHandlesSomeJunkValues() {
        Map<String,String> map = Maps.newHashMap();
        map.put("baz", null);
        
        // In particular, verifying that replacement of multiple identical tokens occurs,
        // unmatched variables are left alone
        String result = BridgeUtils.resolveTemplate("foo ${baz}", map);
        assertEquals("foo ${baz}", result);
        
        result = BridgeUtils.resolveTemplate(" ", map);
        assertEquals(" ", result);
    }
    
    @Test
    public void periodsNotInterpretedAsRegex() {
        Map<String,String> map = Maps.newHashMap();
        map.put("b.z", "bar");
        
        String result = BridgeUtils.resolveTemplate("${baz}", map);
        assertEquals("${baz}", result);
    }
    
    @Test
    public void commaListToSet() {
        Set<String> set = BridgeUtils.commaListToSet("a, b , c");
        assertEquals(Sets.newHashSet("a","b","c"), set);
        
        set = BridgeUtils.commaListToSet("a,b,c");
        assertEquals(Sets.newHashSet("a","b","c"), set);
        
        set = BridgeUtils.commaListToSet("");
        assertEquals(Sets.newHashSet(), set);
        
        set = BridgeUtils.commaListToSet(null);
        assertNotNull(set);
        
        set = BridgeUtils.commaListToSet(" a");
        assertEquals(Sets.newHashSet("a"), set);
        
        // Does not produce a null value.
        set = BridgeUtils.commaListToSet("a,,b");
        assertEquals(Sets.newHashSet("a","b"), set);
    }
    
    @Test
    public void setToCommaList() {
        Set<String> set = Sets.newHashSet("a", null, "", "b");
        
        assertEquals("a,b", BridgeUtils.setToCommaList(set));
        assertNull(BridgeUtils.setToCommaList(null));
        assertNull(BridgeUtils.setToCommaList(Sets.newHashSet()));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void nullsafeImmutableSet() {
        assertNotNull(BridgeUtils.nullSafeImmutableSet(null));
        assertNotNull(BridgeUtils.nullSafeImmutableSet(Sets.newHashSet("A")));
        
        // This should throw an UnsupportedOperationException
        Set<String> set = BridgeUtils.nullSafeImmutableSet(Sets.newHashSet("A"));
        set.add("B");
        
        // nulls are removed. They have to be to create ImmutableSet
        assertEquals(Sets.newHashSet("A"), BridgeUtils.nullSafeImmutableSet(Sets.newHashSet(null, "A")));
    }
    
}
