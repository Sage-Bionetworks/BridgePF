package org.sagebionetworks.bridge.models.schedules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.fasterxml.jackson.databind.JsonNode;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class SchemaReferenceTest {
    @Test
    public void normalCase() {
        SchemaReference schemaRef = new SchemaReference("test-schema", 7);
        assertEquals("test-schema", schemaRef.getId());
        assertEquals(7, schemaRef.getRevision().intValue());
        assertEquals("SchemaReference{id='test-schema', revision=7}", schemaRef.toString());
    }

    @Test
    public void nullProps() {
        // This is technically invalid, but this is validated by the validator. We need to make sure nothing crashes if
        // everything is null.
        SchemaReference schemaRef = new SchemaReference(null, null);
        assertNull(schemaRef.getId());
        assertNull(schemaRef.getRevision());
        assertNotNull(schemaRef.toString());
    }

    @Test
    public void jsonSerialization() throws Exception {
        // start with JSON
        String jsonText = "{\n" +
                "   \"id\":\"test-schema\",\n" +
                "   \"revision\":7\n" +
                "}";

        // convert to POJO
        SchemaReference schemaRef = BridgeObjectMapper.get().readValue(jsonText, SchemaReference.class);
        assertEquals("test-schema", schemaRef.getId());
        assertEquals(7, schemaRef.getRevision().intValue());

        // convert back to JSON
        JsonNode jsonNode = BridgeObjectMapper.get().convertValue(schemaRef, JsonNode.class);
        assertEquals(3, jsonNode.size());
        assertEquals("test-schema", jsonNode.get("id").textValue());
        assertEquals(7, jsonNode.get("revision").intValue());
        assertEquals("SchemaReference", jsonNode.get("type").textValue());
    }

    @Test
    public void equalsVerifier() {
        EqualsVerifier.forClass(SchemaReference.class).allFieldsShouldBeUsed().verify();
    }
}
