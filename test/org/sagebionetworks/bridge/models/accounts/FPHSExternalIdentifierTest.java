package org.sagebionetworks.bridge.models.accounts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.databind.JsonNode;

public class FPHSExternalIdentifierTest {

    @Test
    public void canRoundtripSerialize() throws Exception {
        FPHSExternalIdentifier identifier = FPHSExternalIdentifier.create("foo");
        
        String json = BridgeObjectMapper.get().writeValueAsString(identifier);
        
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        assertEquals("foo", node.get("externalId").asText());
        assertEquals(false, node.get("registered").asBoolean());
        assertEquals("ExternalIdentifier", node.get("type").asText());
        assertEquals(3, TestUtils.getFieldNamesSet(node).size());
        
        json = json.replace("\"registered\":false", "\"registered\":true");
        FPHSExternalIdentifier externalId = BridgeObjectMapper.get().readValue(json, FPHSExternalIdentifier.class);
        assertEquals("foo", externalId.getExternalId());
        assertTrue(externalId.isRegistered());
    }
}
