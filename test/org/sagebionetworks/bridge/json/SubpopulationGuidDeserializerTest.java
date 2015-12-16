package org.sagebionetworks.bridge.json;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Map;

import org.junit.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.Maps;

public class SubpopulationGuidDeserializerTest {
    
    @Test
    public void consentStatusAsKeySerializes() throws Exception {
        // Don't use BridgeObjectMapper because this is one of the rare objects that is serialized/
        // deserialized in Redis, and that uses an unconfigured ObjectMapper instance
        ObjectMapper mapper = new ObjectMapper();
        Map<SubpopulationGuid,ConsentStatus> map = Maps.newHashMap();
        
        map.put(SubpopulationGuid.create(TestConstants.REQUIRED_SIGNED_CURRENT.getSubpopulationGuid()), TestConstants.REQUIRED_SIGNED_CURRENT);
        map.put(SubpopulationGuid.create(TestConstants.REQUIRED_SIGNED_OBSOLETE.getSubpopulationGuid()), TestConstants.REQUIRED_SIGNED_OBSOLETE);
        map.put(SubpopulationGuid.create(TestConstants.REQUIRED_UNSIGNED.getSubpopulationGuid()), TestConstants.REQUIRED_UNSIGNED);

        String json = mapper.writeValueAsString(map);
        JsonNode node = mapper.readTree(json);

        assertNotNull(node.get("foo1"));
        assertNotNull(node.get("foo2"));
        assertNotNull(node.get("foo5"));

        JsonNode consentNode = node.get("foo5");
        ConsentStatus consent5 = mapper.readValue(consentNode.toString(), ConsentStatus.class);
        assertEquals(TestConstants.REQUIRED_UNSIGNED, consent5);
        
        // Test deserialization in a class that has the annotation to restore the SubpopulationGuid key
        MapTest mapTest = new MapTest();
        mapTest.setConsentStatuses(map);
        
        MapTest deserializedMapTest = mapper.readValue(mapper.writeValueAsString(mapTest), MapTest.class);
        
        assertEquals(map, deserializedMapTest.getConsentStatuses());
    }
    
    private static class MapTest {
        // We're testing this deserializer.
        @JsonDeserialize(keyUsing = SubpopulationGuidDeserializer.class)
        private Map<SubpopulationGuid,ConsentStatus> consentStatuses;

        public Map<SubpopulationGuid, ConsentStatus> getConsentStatuses() {
            return consentStatuses;
        }
        public void setConsentStatuses(Map<SubpopulationGuid, ConsentStatus> consentStatuses) {
            this.consentStatuses = consentStatuses;
        }
    }
}
