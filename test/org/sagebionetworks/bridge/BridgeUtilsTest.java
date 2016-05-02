package org.sagebionetworks.bridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import com.google.common.collect.Lists;
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
        orderedSetsEqual(TestUtils.newLinkedHashSet("a","b","c"), set);
        
        set = BridgeUtils.commaListToOrderedSet("a,b,c");
        orderedSetsEqual(TestUtils.newLinkedHashSet("a","b","c"), set);
        
        set = BridgeUtils.commaListToOrderedSet("");
        orderedSetsEqual(TestUtils.newLinkedHashSet(), set);
        
        set = BridgeUtils.commaListToOrderedSet(null);
        assertNotNull(set);
        
        set = BridgeUtils.commaListToOrderedSet(" a");
        orderedSetsEqual(TestUtils.newLinkedHashSet("a"), set);
        
        // Does not produce a null value.
        set = BridgeUtils.commaListToOrderedSet("a,,b");
        orderedSetsEqual(TestUtils.newLinkedHashSet("a","b"), set);
        
        set = BridgeUtils.commaListToOrderedSet("b,a");
        orderedSetsEqual(TestUtils.newLinkedHashSet("b","a"), set);
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
    public void nullsAreRemovedFromSet() {
        // nulls are removed. They have to be to create ImmutableSet
        assertEquals(Sets.newHashSet("A"), BridgeUtils.nullSafeImmutableSet(Sets.newHashSet(null, "A")));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void nullsafeImmutableList() {
        assertEquals(0, BridgeUtils.nullSafeImmutableList(null).size());
        
        assertEquals(Lists.newArrayList("A","B"), BridgeUtils.nullSafeImmutableList(Lists.newArrayList("A","B")));
        
        List<String> list = BridgeUtils.nullSafeImmutableList(Lists.newArrayList("A","B"));
        list.add("C");
    }
    
    @Test
    public void nullsAreRemovedFromList() {
        List<String> list = BridgeUtils.nullSafeImmutableList(Lists.newArrayList(null,"A",null,"B"));
        assertEquals(Lists.newArrayList("A","B"), list);
    }    
    
    @Test(expected = UnsupportedOperationException.class)
    public void nullsafeImmutableMap() {
        assertEquals(0, BridgeUtils.nullSafeImmutableMap(null).size());
        
        Map<String,String> map = Maps.newHashMap();
        map.put("A", "B");
        map.put("C", "D");
        
        assertEquals("B", map.get("A"));
        assertEquals("D", map.get("C"));
        assertEquals(map, BridgeUtils.nullSafeImmutableMap(map));
        
        Map<String,String> newMap = BridgeUtils.nullSafeImmutableMap(map);
        newMap.put("E","F");
    }
    
    @Test
    public void nullsAreRemovedFromMap() {
        Map<String,String> map = Maps.newHashMap();
        map.put("A", "B");
        map.put("C", null);
        
        Map<String,String> mapWithoutNulls = Maps.newHashMap();
        mapWithoutNulls.put("A", "B");
        
        assertEquals(mapWithoutNulls, BridgeUtils.nullSafeImmutableMap(map));
    }    
    
    @Test
    public void getIdFromStormpathHref() {
        String href = "https://enterprise.stormpath.io/v1/accounts/6278jk74xoPOXkruh9vJnh";
        String id = BridgeUtils.getIdFromStormpathHref(href);
        assertEquals("6278jk74xoPOXkruh9vJnh", id);
    }
    
    @Test
    public void getIdFromStormpathHrefNullSafe() {
        assertNull(BridgeUtils.getIdFromStormpathHref(null));
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void unexpectedIdFormatThrowsUnambiguousException() {
        BridgeUtils.getIdFromStormpathHref("https://enterprise.stormpath.io/v2/accounts/6278jk74xoPOXkruh9vJnh");
    }
    
    // assertEquals with two sets doesn't verify the order is the same... hence this test method.
    private <T> void orderedSetsEqual(Set<T> first, Set<T> second) {
        assertEquals(first.size(), second.size());
        
        Iterator<T> firstIterator = first.iterator();
        Iterator<T> secondIterator = second.iterator();
        while(firstIterator.hasNext()) {
            assertEquals(firstIterator.next(), secondIterator.next());
        }
    }
    
}
