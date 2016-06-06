package org.sagebionetworks.bridge.models;

import static org.junit.Assert.assertEquals;

import org.joda.time.LocalDate;
import org.junit.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;

public class DateRangeResourceListTest {

    @Test
    public void canSerialize() throws Exception {
        DateRangeResourceList<String> list = new DateRangeResourceList<>(
                Lists.newArrayList("1", "2", "3"), LocalDate.parse("2016-02-03"),
                LocalDate.parse("2016-02-23"));
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(list);
        assertEquals("2016-02-03", node.get("startDate").asText());
        assertEquals("2016-02-23", node.get("endDate").asText());
        assertEquals(3, node.get("total").asInt());
        assertEquals("DateRangeResourceList", node.get("type").asText());
        assertEquals("1", node.get("items").get(0).asText());
        assertEquals("2", node.get("items").get(1).asText());
        assertEquals("3", node.get("items").get(2).asText());
        assertEquals(5, node.size());
        
        // We never deserialize this on the server side (only in the SDK).
    }
    
}
