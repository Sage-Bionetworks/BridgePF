package org.sagebionetworks.bridge.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class RangeTupleTest {

    @Test
    public void test() {
        RangeTuple<String> tuple = new RangeTuple<>("startValue", "endValue");
        
        assertEquals("startValue", tuple.getStart());
        assertEquals("endValue", tuple.getEnd());
    }
    
    @Test
    public void testNull() {
        RangeTuple<String> tuple = new RangeTuple<>(null, null);
        
        assertNull(tuple.getStart());
        assertNull(tuple.getEnd());
    }
}
