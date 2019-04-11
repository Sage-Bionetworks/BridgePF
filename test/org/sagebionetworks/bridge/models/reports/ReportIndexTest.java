package org.sagebionetworks.bridge.models.reports;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.databind.JsonNode;

public class ReportIndexTest {

    @Test
    public void canSerialize() throws Exception {
        ReportIndex index = ReportIndex.create();
        index.setKey("asdf:STUDY");
        index.setIdentifier("asdf");
        index.setPublic(true);
        index.setSubstudyIds(TestConstants.USER_SUBSTUDY_IDS);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(index);
        assertEquals("asdf", node.get("identifier").textValue());
        assertEquals("ReportIndex", node.get("type").textValue());
        assertTrue(node.get("public").booleanValue());
        assertEquals("substudyA", node.get("substudyIds").get(0).textValue());
        assertEquals("substudyB", node.get("substudyIds").get(1).textValue());
        assertEquals(4, node.size());
        
        ReportIndex deser = BridgeObjectMapper.get().readValue(node.toString(), ReportIndex.class);
        assertEquals("asdf", deser.getIdentifier());
        assertTrue(deser.isPublic());
        assertEquals(TestConstants.USER_SUBSTUDY_IDS, deser.getSubstudyIds());
    }
}
