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
        Set<String> set = BridgeUtils.commaListToOrderedSet("a, b , c");
        assertEquals(TestUtils.newLinkedHashSet("a","b","c"), set);
        
        set = BridgeUtils.commaListToOrderedSet("a,b,c");
        assertEquals(TestUtils.newLinkedHashSet("a","b","c"), set);
        
        set = BridgeUtils.commaListToOrderedSet("");
        assertEquals(TestUtils.newLinkedHashSet(), set);
        
        set = BridgeUtils.commaListToOrderedSet(null);
        assertNotNull(set);
        
        set = BridgeUtils.commaListToOrderedSet(" a");
        assertEquals(TestUtils.newLinkedHashSet("a"), set);
        
        // Does not produce a null value.
        set = BridgeUtils.commaListToOrderedSet("a,,b");
        assertEquals(TestUtils.newLinkedHashSet("a","b"), set);
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
        assertEquals(0, BridgeUtils.nullSafeImmutableSet(null).size());
        assertEquals(Sets.newHashSet("A"), BridgeUtils.nullSafeImmutableSet(Sets.newHashSet("A")));
        
        // This should throw an UnsupportedOperationException
        Set<String> set = BridgeUtils.nullSafeImmutableSet(Sets.newHashSet("A"));
        set.add("B");
    }
    
    @Test
    public void nullsAreRemoved() {
        // nulls are removed. They have to be to create ImmutableSet
        assertEquals(Sets.newHashSet("A"), BridgeUtils.nullSafeImmutableSet(Sets.newHashSet(null, "A")));
    }
    
}
