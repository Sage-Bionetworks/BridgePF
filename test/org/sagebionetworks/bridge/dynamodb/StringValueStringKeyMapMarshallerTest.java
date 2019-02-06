package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

public class StringValueStringKeyMapMarshallerTest {

    @Test
    public void test() {
        Map<String,String> map = new LinkedHashMap<>(); // order preserved
        map.put("substudyA", "");
        map.put("substudyB", "extB");
        
        StringValueStringKeyMapMarshaller marshaller = new StringValueStringKeyMapMarshaller();
        
        String output = marshaller.convert(map);
        assertEquals("{\"substudyA\":\"\",\"substudyB\":\"extB\"}", output);
        
        Map<String, String> deser = marshaller.unconvert(output);
        assertEquals(2, deser.size());
        assertEquals("", deser.get("substudyA"));
        assertEquals("extB", deser.get("substudyB"));
    }
}
