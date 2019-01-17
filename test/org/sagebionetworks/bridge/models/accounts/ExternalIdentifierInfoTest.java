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
    public void canSerializeWithNoSubstudy() throws Exception {
        ExternalIdentifierInfo info = new ExternalIdentifierInfo("AAA", null, true);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(info);
        assertEquals("AAA", node.get("identifier").textValue());
        assertEquals(true, node.get("assigned").booleanValue());
        assertEquals("ExternalIdentifier", node.get("type").textValue());
        assertEquals(3, node.size());
        
        ExternalIdentifierInfo resInfo = BridgeObjectMapper.get().treeToValue(node, ExternalIdentifierInfo.class);
        assertEquals(info, resInfo);
    }
    
    @Test
    public void canSerializeWithSubstudy() throws Exception {
        ExternalIdentifierInfo info = new ExternalIdentifierInfo("AAA", "oneSubstudy", false);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(info);
        assertEquals("AAA", node.get("identifier").textValue());
        assertEquals(false, node.get("assigned").booleanValue());
        assertEquals("oneSubstudy", node.get("substudyId").textValue());
        assertEquals("ExternalIdentifier", node.get("type").textValue());
        assertEquals(4, node.size());
        
        ExternalIdentifierInfo resInfo = BridgeObjectMapper.get().treeToValue(node, ExternalIdentifierInfo.class);
        assertEquals(info, resInfo);
    }
}
