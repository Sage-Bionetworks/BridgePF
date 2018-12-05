package org.sagebionetworks.bridge.models.accounts;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.databind.JsonNode;

public class ExternalIdentifierTest {

    @Test
    public void canSerialize() throws Exception {
        String json = TestUtils.createJson("{'externalId':'AAA','substudyId':'sub-study-id'}");
        
        ExternalIdentifier identifier = BridgeObjectMapper.get().readValue(json, ExternalIdentifier.class);
        assertEquals("AAA", identifier.getIdentifier());
        assertEquals("sub-study-id", identifier.getSubstudyId());
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(identifier);
        
        assertEquals(3, node.size());
        assertEquals("AAA", node.get("identifier").textValue());
        assertEquals("sub-study-id", node.get("substudyId").textValue());
        assertEquals("ExternalIdentifier", node.get("type").textValue());
    }
    
}
