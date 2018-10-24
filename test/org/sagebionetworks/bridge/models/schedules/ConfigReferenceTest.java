package org.sagebionetworks.bridge.models.schedules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.databind.JsonNode;

import nl.jqno.equalsverifier.EqualsVerifier;

public class ConfigReferenceTest {

    @Test
    public void equalsVerifier() {
        EqualsVerifier.forClass(ConfigReference.class).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void normalCase() {
        ConfigReference configRef = new ConfigReference("config1", 7L);
        assertEquals("config1", configRef.getId());
        assertEquals(7, configRef.getRevision().intValue());
    }

    @Test
    public void nullProps() {
        ConfigReference schemaRef = new ConfigReference(null, null);
        assertNull(schemaRef.getId());
        assertNull(schemaRef.getRevision());
    }

    @Test
    public void jsonSerialization() throws Exception {
        // start with JSON
        String jsonText = "{\n" +
                "   \"id\":\"test-config\",\n" +
                "   \"revision\":7\n" +
                "}";

        // convert to POJO
        ConfigReference schemaRef = BridgeObjectMapper.get().readValue(jsonText, ConfigReference.class);
        assertEquals("test-config", schemaRef.getId());
        assertEquals(7L, schemaRef.getRevision().longValue());

        // convert back to JSON
        JsonNode jsonNode = BridgeObjectMapper.get().convertValue(schemaRef, JsonNode.class);
        assertEquals(3, jsonNode.size());
        assertEquals("test-config", jsonNode.get("id").textValue());
        assertEquals(7, jsonNode.get("revision").longValue());
        assertEquals("ConfigReference", jsonNode.get("type").textValue());
    }
}
