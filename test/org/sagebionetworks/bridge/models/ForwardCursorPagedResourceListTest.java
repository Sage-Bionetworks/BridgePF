package org.sagebionetworks.bridge.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.joda.time.DateTime;
import org.junit.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.AccountStatus;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.Lists;

public class ForwardCursorPagedResourceListTest {
    @Test
    public void canSerialize() throws Exception {
        List<AccountSummary> accounts = Lists.newArrayListWithCapacity(2);
        accounts.add(new AccountSummary("firstName1", "lastName1", "email1@email.com", "id", DateTime.now(),
                AccountStatus.DISABLED, TestConstants.TEST_STUDY));
        accounts.add(new AccountSummary("firstName2", "lastName2", "email2@email.com", "id2", DateTime.now(),
                AccountStatus.ENABLED, TestConstants.TEST_STUDY));
        
        ForwardCursorPagedResourceList<AccountSummary> page = new ForwardCursorPagedResourceList<AccountSummary>(
                accounts, "anOffsetKey").withRequestParam(ResourceList.PAGE_SIZE, 100)
                        .withRequestParam(ResourceList.EMAIL_FILTER, "filterString");
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(page);
        assertEquals("anOffsetKey", node.get("offsetKey").asText());
        assertEquals("anOffsetKey", node.get("nextPageOffsetKey").asText());
        assertEquals(100, node.get("pageSize").asInt());
        assertEquals("filterString", node.get("requestParams").get("emailFilter").asText());
        assertTrue(node.get("hasNext").asBoolean());
        assertEquals("ForwardCursorPagedResourceList", node.get("type").asText());
        
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

        assertEquals(page.getNextPageOffsetKey(), serPage.getNextPageOffsetKey());
        assertEquals(page.getRequestParams().get("pageSize"), serPage.getRequestParams().get("pageSize"));
        assertEquals(page.getRequestParams().get("emailFilter"), serPage.getRequestParams().get("emailFilter"));
        assertEquals(page.hasNext(), serPage.hasNext());
        
        assertEquals(page.getItems(), serPage.getItems());
    }
    
    @Test
    public void offsetKeyCanBeNull() throws Exception {
        List<AccountSummary> accounts = Lists.newArrayListWithCapacity(2);
        ForwardCursorPagedResourceList<AccountSummary> page = new ForwardCursorPagedResourceList<AccountSummary>(
                accounts, null).withRequestParam(ResourceList.PAGE_SIZE, 100)
                .withRequestParam(ResourceList.EMAIL_FILTER, "filterString");
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(page);
        System.out.println(node.toString());
        assertNull(node.get("offsetKey"));
        assertEquals(100, node.get("pageSize").asInt());
        assertEquals("filterString", node.get("requestParams").get("emailFilter").asText());
        assertEquals("ForwardCursorPagedResourceList", node.get("type").asText());
        assertFalse(node.get("hasNext").asBoolean());
        assertEquals(5, node.size());
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
        assertEquals(100, node.get("pageSize").asInt());
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
