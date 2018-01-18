package org.sagebionetworks.bridge.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.junit.Test;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class ResourceListTest {

    @SuppressWarnings("deprecation")
    @Test
    public void canSerialize() throws Exception {
        
        ResourceList<String> list = new ResourceList<>(ImmutableList.of("A","B","C"))
                .withRequestParam("test", 13L);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(list);
        
        assertEquals(4, node.size());
        assertEquals(3, node.get("total").intValue());
        assertEquals(13, node.get("requestParams").get("test").intValue());
        assertEquals(ResourceList.REQUEST_PARAMS, node.get("requestParams").get(ResourceList.TYPE).textValue());
        assertEquals(3, node.get("items").size());
        assertEquals("ResourceList", node.get("type").asText());
        
        ResourceList<String> deser = BridgeObjectMapper.get().readValue(node.toString(), new TypeReference<ResourceList<String>>() {});
        
        assertEquals("A", deser.getItems().get(0));
        assertEquals("B", deser.getItems().get(1));
        assertEquals("C", deser.getItems().get(2));
        // This is deserialized as an integer, not a long, that is a property of the library. Looks the same in JSON.
        assertEquals((Integer)13, (Integer)deser.getRequestParams().get("test"));
        assertEquals((Integer)3, deser.getTotal());
        assertEquals(ResourceList.REQUEST_PARAMS, deser.getRequestParams().get(ResourceList.TYPE));
    }
    
    @Test
    public void noTotalPropertyWhenListEmpty() {
        ResourceList<String> list = new ResourceList<>(ImmutableList.of());
        JsonNode node = BridgeObjectMapper.get().valueToTree(list);
        assertFalse(node.has("total"));
    }
    
    @Test
    public void requestParams() {
        ResourceList<String> list = makeResourceList();
        
        assertEquals("bar", list.getRequestParams().get("foo"));
        assertNull(list.getRequestParams().get("baz"));
    }
    
    @Test
    public void getDateTime() {
        DateTimeZone MSK = DateTimeZone.forOffsetHours(3);
        DateTime dateTime = DateTime.now(MSK);
        
        ResourceList<String> list = makeResourceList();
        list.withRequestParam("dateTime1", dateTime);
        list.withRequestParam("dateTime2", dateTime.toString());
        
        TestUtils.assertDatesWithTimeZoneEqual(dateTime,  list.getDateTime("dateTime1"));
        TestUtils.assertDatesWithTimeZoneEqual(dateTime,  list.getDateTime("dateTime2"));
    }
    
    @Test
    public void getNullDateTime() {
        ResourceList<String> list = makeResourceList();
        
        assertNull(list.getDateTime("No value"));
    }
    
    @Test
    public void getLocalDate() {
        LocalDate localDate = LocalDate.parse("2017-04-15");
        
        ResourceList<String> list = makeResourceList();
        list.withRequestParam("localDate1", localDate);
        list.withRequestParam("localDate2", localDate.toString());
        
        assertEquals(localDate, list.getLocalDate("localDate1"));
        assertEquals(localDate, list.getLocalDate("localDate2"));
    }
    
    @Test
    public void getNullLocalDate() {
        ResourceList<String> list = makeResourceList();
        
        assertNull(list.getLocalDate("No value"));
    }
    
    @SuppressWarnings("deprecation")
    @Test
    public void getTotal() {
        ResourceList<String> list = makeResourceList();
        
        assertEquals((Integer)3, list.getTotal());
    }
    
    @Test(expected = NullPointerException.class)
    public void nullList() {
        new ResourceList<>(null);
    }
    
    @SuppressWarnings("deprecation")
    @Test
    public void emptyList() {
        ResourceList<String> list = new ResourceList<>(Lists.newArrayList());

        assertTrue(list.getItems().isEmpty());
        assertNull(list.getTotal());
    }
    
    @Test
    public void blankOrNullRequestParamKeysNotAdded() {
        List<String> items = Lists.newArrayList("A","B","C");
        ResourceList<String> list = new ResourceList<>(items);
        
        list.withRequestParam(null, "a");
        list.withRequestParam("", "a");
        list.withRequestParam("  ", "a");
        
        assertEquals(1, list.getRequestParams().size());
        assertEquals(ResourceList.REQUEST_PARAMS, list.getRequestParams().get(ResourceList.TYPE));
    }

    @Test
    public void nullRequestParamValuesNotAdded() {
        List<String> items = Lists.newArrayList("A","B","C");
        ResourceList<String> list = new ResourceList<>(items);
        list.withRequestParam("a valid key", null);
        
        assertEquals(1, list.getRequestParams().size());
        assertEquals(ResourceList.REQUEST_PARAMS, list.getRequestParams().get(ResourceList.TYPE));
    }
    
    @Test
    public void cannnotChanngeRequestParamsType() {
        List<String> items = Lists.newArrayList("A","B","C");
        ResourceList<String> list = new ResourceList<>(items);
        list.withRequestParam("type", "not the right type");
        assertEquals(ResourceList.REQUEST_PARAMS, list.getRequestParams().get(ResourceList.TYPE));
    }
    
    private ResourceList<String> makeResourceList() {
        List<String> items = Lists.newArrayList("A","B","C");
        
        ResourceList<String> list = new ResourceList<>(items);
        list.withRequestParam("foo", "bar");
        return list;
    }
}
