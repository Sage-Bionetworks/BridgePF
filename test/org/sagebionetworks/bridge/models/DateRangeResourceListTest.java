package org.sagebionetworks.bridge.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.sagebionetworks.bridge.models.ResourceList.START_DATE;
import static org.sagebionetworks.bridge.models.ResourceList.END_DATE;

import org.joda.time.LocalDate;
import org.junit.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;

public class DateRangeResourceListTest {

    @SuppressWarnings("deprecation")
    @Test
    public void canSerialize() throws Exception {
        DateRangeResourceList<String> list = new DateRangeResourceList<>(
                Lists.newArrayList("1", "2", "3"))
                .withRequestParam(START_DATE, LocalDate.parse("2016-02-03"))
                .withRequestParam(END_DATE, LocalDate.parse("2016-02-23"));
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(list);
        assertEquals("2016-02-03", node.get("startDate").asText());
        assertEquals("2016-02-23", node.get("endDate").asText());
        assertEquals("2016-02-03", node.get("requestParams").get("startDate").asText());
        assertEquals("2016-02-23", node.get("requestParams").get("endDate").asText());
        assertEquals(ResourceList.REQUEST_PARAMS, node.get("requestParams").get(ResourceList.TYPE).asText());
        assertEquals("DateRangeResourceList", node.get("type").asText());
        assertEquals(3, node.get("items").size());
        assertEquals("1", node.get("items").get(0).asText());
        assertEquals("2", node.get("items").get(1).asText());
        assertEquals("3", node.get("items").get(2).asText());
        assertEquals(6, node.size());
        
        list = BridgeObjectMapper.get().readValue(node.toString(), 
                new TypeReference<DateRangeResourceList<String>>() {});
        assertEquals(LocalDate.parse("2016-02-03"), list.getStartDate());
        assertEquals(LocalDate.parse("2016-02-23"), list.getEndDate());
        assertEquals(3, list.getItems().size());
        assertEquals("1", list.getItems().get(0));
        assertEquals("2", list.getItems().get(1));
        assertEquals("3", list.getItems().get(2));
        assertEquals("2016-02-03", list.getRequestParams().get("startDate"));
        assertEquals("2016-02-23", list.getRequestParams().get("endDate"));
        assertEquals(ResourceList.REQUEST_PARAMS, list.getRequestParams().get(ResourceList.TYPE));
    }
    
    @SuppressWarnings("deprecation")
    @Test
    public void getTotal() throws Exception {
        DateRangeResourceList<String> list = new DateRangeResourceList<>(
                Lists.newArrayList("1", "2", "3"));
        
        assertEquals((Integer)3, list.getTotal());
        assertNull(list.getRequestParams().get("total")); // not a request parameter
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(list);
        assertEquals(3, node.get("total").intValue());
    }

    @Test(expected = NullPointerException.class)
    public void nullList() {
        new DateRangeResourceList<>(null);
    }
    
    @SuppressWarnings("deprecation")
    @Test
    public void emptyList() {
        DateRangeResourceList<String> list = new DateRangeResourceList<>(Lists.newArrayList());

        assertTrue(list.getItems().isEmpty());
        // We are carrying over an exceptional behavior into this deprecated method where this 
        // list returns 0 instead of null, to keep integration tests and any potential clients
        // working. This value is going away as it is entirely redundent with the list size.
        assertEquals((Integer)0, list.getTotal());
    }
}
