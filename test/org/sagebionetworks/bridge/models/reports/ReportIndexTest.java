package org.sagebionetworks.bridge.models.reports;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.databind.JsonNode;

public class ReportIndexTest {

    @Test
    public void canSerialize() throws Exception {
        ReportIndex index = ReportIndex.create();
        index.setKey("asdf:STUDY");
        index.setIdentifier("asdf");
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(index);
        assertEquals("asdf", node.get("identifier").asText());
        assertEquals("ReportIndex", node.get("type").asText());
        assertEquals(2, node.size());
        
        ReportIndex deser = BridgeObjectMapper.get().readValue(node.toString(), ReportIndex.class);
        assertEquals("asdf", deser.getIdentifier());
    }
}
