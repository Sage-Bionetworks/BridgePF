package org.sagebionetworks.bridge.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.joda.time.DateTime;
import org.junit.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;

public class DateTimeRangeResourceListTest {

    @Test
    public void canSerialize() throws Exception {
        DateTimeRangeResourceList<String> list = new DateTimeRangeResourceList<>(
                Lists.newArrayList("1", "2", "3"), DateTime.parse("2016-02-03T10:10:10.000-08:00"),
                DateTime.parse("2016-02-23T14:14:14.000-08:00"));
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(list);
        assertEquals("2016-02-03T10:10:10.000-08:00", node.get("startTime").asText());
        assertEquals("2016-02-23T14:14:14.000-08:00", node.get("endTime").asText());
        assertEquals("DateTimeRangeResourceList", node.get("type").asText());
        assertEquals("1", node.get("items").get(0).asText());
        assertEquals("2", node.get("items").get(1).asText());
        assertEquals("3", node.get("items").get(2).asText());
        
        JsonNode requestParams = node.get("requestParams");
        assertEquals("2016-02-03T10:10:10.000-08:00", requestParams.get("startTime").asText());
        assertEquals("2016-02-23T14:14:14.000-08:00", requestParams.get("endTime").asText());
        
        assertEquals(5, node.size());
        
        // We never deserialize this on the server side (only in the SDK).
    }
    
    @Test
    public void canSerializeWithNullParams() {
        DateTimeRangeResourceList<String> list = new DateTimeRangeResourceList<>(
                Lists.newArrayList("1", "2", "3"), null, null);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(list);
        assertNull(node.get("requestParams").get("startTime"));
        assertNull(node.get("requestParams").get("endTime"));
    }
    
}
