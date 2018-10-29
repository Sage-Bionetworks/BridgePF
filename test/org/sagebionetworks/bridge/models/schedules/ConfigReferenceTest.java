package org.sagebionetworks.bridge.models.schedules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.sagebionetworks.bridge.TestUtils;
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
        ConfigReference configRef = new ConfigReference(null, null);
        assertNull(configRef.getId());
        assertNull(configRef.getRevision());
    }

    @Test
    public void jsonSerialization() throws Exception {
        // start with JSON
        String jsonText = TestUtils.createJson("{'id':'test-config', 'revision':7}");

        // convert to POJO
        ConfigReference configRef = BridgeObjectMapper.get().readValue(jsonText, ConfigReference.class);
        assertEquals("test-config", configRef.getId());
        assertEquals(7L, configRef.getRevision().longValue());

        // convert back to JSON
        JsonNode jsonNode = BridgeObjectMapper.get().convertValue(configRef, JsonNode.class);
        assertEquals(3, jsonNode.size());
        assertEquals("test-config", jsonNode.get("id").textValue());
        assertEquals(7, jsonNode.get("revision").longValue());
        assertEquals("ConfigReference", jsonNode.get("type").textValue());
    }
}
