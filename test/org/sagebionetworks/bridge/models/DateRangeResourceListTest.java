package org.sagebionetworks.bridge.models;

import static org.junit.Assert.assertEquals;

import org.joda.time.LocalDate;
import org.junit.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.core.type.TypeReference;
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
        
        list = BridgeObjectMapper.get().readValue(node.toString(), 
                new TypeReference<DateRangeResourceList<String>>() {});
        assertEquals(list.getStartDate(), LocalDate.parse("2016-02-03"));
        assertEquals(list.getEndDate(), LocalDate.parse("2016-02-23"));
        assertEquals(3, list.getTotal());
        assertEquals("1", list.getItems().get(0));
        assertEquals("2", list.getItems().get(1));
        assertEquals("3", list.getItems().get(2));
    }
    
}
