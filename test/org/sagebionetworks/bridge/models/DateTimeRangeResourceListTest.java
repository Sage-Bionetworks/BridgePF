package org.sagebionetworks.bridge.models;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.joda.time.DateTime;
import org.junit.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;

public class DateTimeRangeResourceListTest {

    @SuppressWarnings("deprecation")
    @Test
    public void canSerialize() throws Exception {
        DateTime startTime = DateTime.parse("2016-02-03T10:10:10.000-08:00");
        DateTime endTime = DateTime.parse("2016-02-23T14:14:14.000-08:00");
        List<String> items = Lists.newArrayList("1", "2", "3");
        DateTimeRangeResourceList<String> list = new DateTimeRangeResourceList<>(items)
                .withRequestParam(ResourceList.START_TIME, startTime)
                .withRequestParam(ResourceList.END_TIME, endTime);
        
        assertEquals(startTime, list.getStartTime());
        assertEquals(endTime, list.getEndTime());
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(list);
        assertEquals("2016-02-03T10:10:10.000-08:00", node.get("startTime").asText());
        assertEquals("2016-02-23T14:14:14.000-08:00", node.get("endTime").asText());
        assertEquals(3, node.get("items").size());
        assertEquals("DateTimeRangeResourceList", node.get("type").asText());
        assertEquals("1", node.get("items").get(0).asText());
        assertEquals("2", node.get("items").get(1).asText());
        assertEquals("3", node.get("items").get(2).asText());
        assertEquals(6, node.size());
        assertEquals("2016-02-03T10:10:10.000-08:00", node.get("requestParams").get("startTime").asText());
        assertEquals("2016-02-23T14:14:14.000-08:00", node.get("requestParams").get("endTime").asText());
        assertEquals(ResourceList.REQUEST_PARAMS, node.get("requestParams").get(ResourceList.TYPE).asText());
        
        // We never deserialize this on the server side (only in the SDK).
    }
    
}
