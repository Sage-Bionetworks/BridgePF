package org.sagebionetworks.bridge.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.reports.ReportIndex;
import org.sagebionetworks.bridge.models.reports.ReportType;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;

public class ReportTypeResourceListTest {

    @Test
    public void canSerialize() throws Exception {
        ReportIndex index1 = ReportIndex.create();
        index1.setKey("doesn't matter what this is");
        index1.setIdentifier("foo");
        
        ReportIndex index2 = ReportIndex.create();
        index2.setKey("doesn't matter what this is");
        index2.setIdentifier("bar");
        index2.setPublic(true);
        
        ReportTypeResourceList<ReportIndex> list = new ReportTypeResourceList<>(
                Lists.newArrayList(index1, index2), ReportType.PARTICIPANT);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(list);
        assertEquals("participant", node.get("reportType").asText());
        assertEquals("ReportTypeResourceList", node.get("type").asText());
        assertEquals("foo", node.get("items").get(0).get("identifier").asText());
        assertFalse(node.get("items").get(0).get("public").asBoolean());
        assertEquals("bar", node.get("items").get(1).get("identifier").asText());
        assertTrue(node.get("items").get(1).get("public").asBoolean());
        
        JsonNode requestParams = node.get("requestParams");
        assertEquals("participant", requestParams.get("reportType").asText());
        
        assertEquals(4, node.size());
        
        // We never deserialize this on the server side (only in the SDK).
    }
    
    @Test
    public void testMissingTypeSerialization() {
        ReportIndex index1 = ReportIndex.create();
        index1.setKey("doesn't matter what this is");
        index1.setIdentifier("foo");
        
        ReportIndex index2 = ReportIndex.create();
        index2.setKey("doesn't matter what this is");
        index2.setIdentifier("bar");
        index2.setPublic(true);
        
        ReportTypeResourceList<ReportIndex> list = new ReportTypeResourceList<>(
                Lists.newArrayList(index1, index2), null);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(list);
        assertNull(node.get("requestParams").get("reportType"));
    }
}
