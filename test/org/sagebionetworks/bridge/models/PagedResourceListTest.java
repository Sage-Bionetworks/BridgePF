package org.sagebionetworks.bridge.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.List;

import org.junit.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.AccountStatus;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.Lists;

public class PagedResourceListTest {

    @Test
    public void canSerialize() throws Exception {
        List<AccountSummary> accounts = Lists.newArrayListWithCapacity(2);
        accounts.add(new AccountSummary("firstName1", "lastName1", "email1@email.com", AccountStatus.DISABLED));
        accounts.add(new AccountSummary("firstName2", "lastName2", "email2@email.com", AccountStatus.ENABLED));
        
        PagedResourceList<AccountSummary> page = new PagedResourceList<AccountSummary>(accounts, 2, 100, 123)
                .withFilter("emailFilter", "filterString");
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(page);
        assertEquals(2, node.get("offsetBy").asInt());
        assertEquals(123, node.get("total").asInt());
        assertEquals(100, node.get("pageSize").asInt());
        assertEquals("filterString", node.get("emailFilter").asText());
        assertEquals("PagedResourceList", node.get("type").asText());
        
        ArrayNode items = (ArrayNode)node.get("items");
        assertEquals(2, items.size());
        
        JsonNode child1 = items.get(0);
        assertEquals("firstName1", child1.get("firstName").asText());
        assertEquals("lastName1", child1.get("lastName").asText());
        assertEquals("email1@email.com", child1.get("email").asText());
        assertEquals("disabled", child1.get("status").asText());
        
        PagedResourceList<AccountSummary> serPage = BridgeObjectMapper.get().readValue(node.toString(), 
                new TypeReference<PagedResourceList<AccountSummary>>() {});

        assertEquals(page.getTotal(), serPage.getTotal());
        assertEquals(page.getOffsetBy(), serPage.getOffsetBy());
        assertEquals(page.getPageSize(), serPage.getPageSize());
        assertEquals(page.getFilters().get("emailFilter"), serPage.getFilters().get("emailFilter"));
        
        assertEquals(page.getItems(), serPage.getItems());
    }
    
    @Test
    public void offsetByCanBeNull() throws Exception {
        List<AccountSummary> accounts = Lists.newArrayListWithCapacity(2);
        PagedResourceList<AccountSummary> page = new PagedResourceList<AccountSummary>(accounts, null, 100, 123)
                .withFilter("emailFilter", "filterString");
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(page);
        assertNull(node.get("offsetBy"));
        assertEquals(123, node.get("total").asInt());
        assertEquals(100, node.get("pageSize").asInt());
        assertEquals("filterString", node.get("emailFilter").asText());
        assertEquals("PagedResourceList", node.get("type").asText());
        assertEquals(5, node.size());
    }
    
    // This test was moved from another class that implemented PagedResourceList for
    // DynamoDB, that was easily incorporated into this implementation. This test verifies
    // that the results are the same as before.
    @Test
    public void canSerializeWithDynamoLastKey() throws Exception {
        List<String> accounts = Lists.newArrayListWithCapacity(2);
        accounts.add("value1");
        accounts.add("value2");
        
        PagedResourceList<String> page = new PagedResourceList<>(accounts, null, 100, 123)
                .withFilter("idFilter", "foo")
                .withFilter("assignmentFilter", "bar");
        JsonNode node = BridgeObjectMapper.get().valueToTree(page);
        assertEquals(123, node.get("total").asInt());
        assertEquals(100, node.get("pageSize").asInt());
        assertEquals("foo", node.get("idFilter").asText());
        assertEquals("bar", node.get("assignmentFilter").asText());
        assertEquals("PagedResourceList", node.get("type").asText());
        
        ArrayNode items = (ArrayNode)node.get("items");
        assertEquals(2, items.size());
        assertEquals("value1", items.get(0).asText());
        assertEquals("value2", items.get(1).asText());
        
        System.out.println(node.toString());
        
        // We don't deserialize this, but let's verify for the SDK
        PagedResourceList<String> serPage = BridgeObjectMapper.get().readValue(node.toString(), 
                new TypeReference<PagedResourceList<String>>() {});
        
        assertEquals(page.getTotal(), serPage.getTotal());
        assertEquals(page.getPageSize(), serPage.getPageSize());
        assertEquals(page.getFilters().get("lastKey"), serPage.getFilters().get("lastKey"));
        assertEquals(page.getLastKey(), serPage.getLastKey());
        assertEquals(page.getFilters().get("idFilter"), serPage.getFilters().get("idFilter"));
        assertEquals(page.getFilters().get("assignmentFilter"), serPage.getFilters().get("assignmentFilter"));
        assertEquals(page.getItems(), serPage.getItems());
    }
}
