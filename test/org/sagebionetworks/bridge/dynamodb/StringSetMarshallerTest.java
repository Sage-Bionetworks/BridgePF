package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;

import java.util.Set;
import java.util.TreeSet;

import org.junit.Test;

public class StringSetMarshallerTest {
    private static final StringSetMarshaller MARSHALLER = new StringSetMarshaller();
    
    @Test
    public void serializationTest() {
        TreeSet<String> orderedSet = new TreeSet<>();
        orderedSet.add("Paris");
        orderedSet.add("Brussels");
        orderedSet.add("London");
        
        String ser = MARSHALLER.convert(orderedSet);
        assertEquals("[\"Brussels\",\"London\",\"Paris\"]", ser);
        
        Set<String> deser = MARSHALLER.unconvert(ser);
        
        assertEquals(orderedSet, deser);
    }

}
