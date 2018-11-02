package org.sagebionetworks.bridge.hibernate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.IOException;

import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.TestUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonNodeAttributeConverterTest {

    private static final String JSON = TestUtils.createJson("{'test':100}");
    private static JsonNode node;
    
    private static JsonNodeAttributeConverter converter;
    
    @Before
    public void before() throws IOException {
        converter = new JsonNodeAttributeConverter();
        node = new ObjectMapper().readTree(JSON);
    }
    
    @Test
    public void convertToDatabaseColumn() {
        assertEquals(JSON, converter.convertToDatabaseColumn(node));
    }

    @Test
    public void convertToEntityAttribute() {
        assertEquals(node, converter.convertToEntityAttribute(JSON));
    }
    
    @Test
    public void convertToDatabaseColumnNullsafe() {
        assertNull(converter.convertToDatabaseColumn(null));
    }

    @Test
    public void convertToEntityAttributeNullsafe() {
        assertNull(converter.convertToEntityAttribute(null));
    }
}
