package org.sagebionetworks.bridge.models.accounts;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.databind.JsonNode;

import nl.jqno.equalsverifier.EqualsVerifier;

public class ExternalIdentifierInfoTest {

    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(ExternalIdentifierInfo.class).allFieldsShouldBeUsed().verify();
    }

    @Test
    public void canSerialize() throws Exception {
        ExternalIdentifierInfo info = new ExternalIdentifierInfo("AAA", true);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(info);
        assertEquals("AAA", node.get("identifier").asText());
        assertEquals(true, node.get("assigned").asBoolean());
        assertEquals("ExternalIdentifier", node.get("type").asText());
        assertEquals(3, node.size());
        
        ExternalIdentifierInfo resInfo = BridgeObjectMapper.get().treeToValue(node, ExternalIdentifierInfo.class);
        assertEquals(info, resInfo);
    }
    
}
