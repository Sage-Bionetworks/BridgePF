package org.sagebionetworks.bridge.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.junit.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.AccountStatus;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

public class ForwardCursorPagedResourceListTest {
    @SuppressWarnings("deprecation")
    @Test
    public void canSerialize() throws Exception {
        List<AccountSummary> accounts = Lists.newArrayListWithCapacity(2);
        accounts.add(new AccountSummary("firstName1", "lastName1", "email1@email.com", TestConstants.PHONE,
                null, ImmutableMap.of("substudy1","externalId1"), "id", DateTime.now(), AccountStatus.DISABLED, TestConstants.TEST_STUDY, ImmutableSet.of()));
        accounts.add(new AccountSummary("firstName2", "lastName2", "email2@email.com", TestConstants.PHONE,
                null, ImmutableMap.of("substudy2","externalId2"), "id2", DateTime.now(), AccountStatus.ENABLED, TestConstants.TEST_STUDY, ImmutableSet.of()));
        
        DateTime startTime = DateTime.parse("2016-02-03T10:10:10.000-08:00");
        DateTime endTime = DateTime.parse("2016-02-23T14:14:14.000-08:00");
        
        ForwardCursorPagedResourceList<AccountSummary> page = new ForwardCursorPagedResourceList<AccountSummary>(
                accounts, "nextOffsetKey")
                .withRequestParam(ResourceList.OFFSET_KEY, "offsetKey")
                .withRequestParam(ResourceList.START_TIME, startTime)
                .withRequestParam(ResourceList.END_TIME, endTime)
                .withRequestParam(ResourceList.SCHEDULED_ON_START, startTime)
                .withRequestParam(ResourceList.SCHEDULED_ON_END, endTime)
                .withRequestParam(ResourceList.PAGE_SIZE, 100)
                .withRequestParam(ResourceList.EMAIL_FILTER, "filterString");
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(page);
        
        assertEquals("nextOffsetKey", node.get("offsetKey").asText());
        assertEquals("nextOffsetKey", node.get("nextPageOffsetKey").asText());
        assertEquals(startTime.toString(), node.get("startTime").asText());
        assertEquals(endTime.toString(), node.get("endTime").asText());
        assertEquals(startTime.toString(), node.get("scheduledOnStart").asText());
        assertEquals(endTime.toString(), node.get("scheduledOnEnd").asText());
        assertEquals(100, node.get("pageSize").intValue());
        assertEquals(2, node.get("total").intValue());
        assertEquals("ForwardCursorPagedResourceList", node.get("type").asText());
        
        JsonNode rp = node.get("requestParams");
        assertEquals(startTime.toString(), rp.get("startTime").asText());
        assertEquals(endTime.toString(), rp.get("endTime").asText());
        assertEquals(startTime.toString(), rp.get("scheduledOnStart").asText());
        assertEquals(endTime.toString(), rp.get("scheduledOnEnd").asText());
        assertEquals("filterString", rp.get("emailFilter").asText());
        assertEquals(100, rp.get("pageSize").intValue());
        assertEquals("offsetKey", rp.get("offsetKey").asText());
        assertEquals(ResourceList.REQUEST_PARAMS, rp.get(ResourceList.TYPE).textValue());
        
        ArrayNode items = (ArrayNode)node.get("items");
        assertEquals(2, items.size());
        
        JsonNode child1 = items.get(0);
        assertEquals("firstName1", child1.get("firstName").asText());
        assertEquals("lastName1", child1.get("lastName").asText());
        assertEquals("email1@email.com", child1.get("email").asText());
        assertEquals("id", child1.get("id").asText());
        assertEquals("disabled", child1.get("status").asText());
        
        ForwardCursorPagedResourceList<AccountSummary> serPage = BridgeObjectMapper.get().readValue(node.toString(),
                new TypeReference<ForwardCursorPagedResourceList<AccountSummary>>() {
                });
        
        assertEquals("nextOffsetKey", serPage.getNextPageOffsetKey());
        assertEquals("nextOffsetKey", serPage.getOffsetKey());
        assertEquals(startTime, serPage.getStartTime());
        assertEquals(endTime, serPage.getEndTime());
        assertEquals(startTime, serPage.getScheduledOnStart());
        assertEquals(endTime, serPage.getScheduledOnEnd());
        assertEquals((Integer)100, serPage.getPageSize());
        assertEquals((Integer)2, serPage.getTotal());
        
        Map<String,Object> params = serPage.getRequestParams();
        assertEquals(startTime.toString(), params.get("startTime"));
        assertEquals(endTime.toString(), params.get("endTime"));
        assertEquals(startTime.toString(), params.get("scheduledOnStart"));
        assertEquals(endTime.toString(), params.get("scheduledOnEnd"));
        assertEquals(100, params.get("pageSize"));
        assertEquals("filterString", params.get("emailFilter"));
        assertEquals("offsetKey", params.get("offsetKey"));
        
        assertEquals(page.getItems(), serPage.getItems());
    }
    
    @Test
    public void hasNext() {
        ForwardCursorPagedResourceList<AccountSummary> list;
        JsonNode node;
        
        list = new ForwardCursorPagedResourceList<>(Lists.newArrayList(), null);
        assertFalse(list.hasNext());
        node = BridgeObjectMapper.get().valueToTree(list);
        assertFalse(node.get("hasNext").booleanValue());
        assertNull(node.get("offsetKey"));
        assertNull(node.get("nextPageOffsetKey"));
        
        list = new ForwardCursorPagedResourceList<>(Lists.newArrayList(), "nextPageKey");
        assertTrue(list.hasNext());
        node = BridgeObjectMapper.get().valueToTree(list);
        assertTrue(node.get("hasNext").booleanValue());
        assertEquals("nextPageKey", node.get("offsetKey").asText());
        assertEquals("nextPageKey", node.get("nextPageOffsetKey").asText());
    }
    
    // This test was moved from another class that implemented PagedResourceList for
    // DynamoDB, that was easily incorporated into this implementation. This test verifies
    // that the results are the same as before.
    @Test
    public void canSerializeWithDynamoOffsetKey() throws Exception {
        List<String> accounts = Lists.newArrayListWithCapacity(2);
        accounts.add("value1");
        accounts.add("value2");
        
        ForwardCursorPagedResourceList<String> page = new ForwardCursorPagedResourceList<>(accounts, null)
                .withRequestParam(ResourceList.PAGE_SIZE, 100)
                .withRequestParam(ResourceList.ID_FILTER, "foo")
                .withRequestParam(ResourceList.ASSIGNMENT_FILTER, "bar");
        JsonNode node = BridgeObjectMapper.get().valueToTree(page);
        assertEquals(100, node.get("pageSize").intValue());
        assertEquals("foo", node.get("requestParams").get("idFilter").asText());
        assertEquals("bar", node.get("requestParams").get("assignmentFilter").asText());
        assertEquals("ForwardCursorPagedResourceList", node.get("type").asText());
        
        ArrayNode items = (ArrayNode)node.get("items");
        assertEquals(2, items.size());
        assertEquals("value1", items.get(0).asText());
        assertEquals("value2", items.get(1).asText());
        
        // We don't deserialize this, but let's verify for the SDK
        ForwardCursorPagedResourceList<String> serPage = BridgeObjectMapper.get().readValue(node.toString(), 
                new TypeReference<ForwardCursorPagedResourceList<String>>() {});
        
        assertEquals(page.getNextPageOffsetKey(), serPage.getNextPageOffsetKey());
        assertEquals(page.getRequestParams().get("pageSize"), serPage.getRequestParams().get("pageSize"));
        assertEquals(page.getRequestParams().get("idFilter"), serPage.getRequestParams().get("idFilter"));
        assertEquals(page.getRequestParams().get("assignmentFilter"), serPage.getRequestParams().get("assignmentFilter"));
        assertEquals(page.getItems(), serPage.getItems());
    }
}
