package org.sagebionetworks.bridge.models;

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.AccountStatus;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class DynamoPagedResourceListTest {

    @Test
    public void canSerialize() throws Exception {
        List<AccountSummary> accounts = Lists.newArrayListWithCapacity(2);
        accounts.add(new AccountSummary("firstName1", "lastName1", "email1@email.com", AccountStatus.DISABLED));
        accounts.add(new AccountSummary("firstName2", "lastName2", "email2@email.com", AccountStatus.ENABLED));
        
        Map<String,String> filters = Maps.newHashMap();
        filters.put("idFilter", "foo");
        filters.put("assignmentFilter", "bar");
        
        DynamoPagedResourceList<AccountSummary> page = new DynamoPagedResourceList<>(accounts, null, 100, 123, filters);
        JsonNode node = BridgeObjectMapper.get().valueToTree(page);
        assertEquals(123, node.get("total").asInt());
        assertEquals(100, node.get("pageSize").asInt());
        assertEquals("foo", node.get("idFilter").asText());
        assertEquals("bar", node.get("assignmentFilter").asText());
        assertEquals("PagedResourceList", node.get("type").asText());
        
        ArrayNode items = (ArrayNode)node.get("items");
        assertEquals(2, items.size());
        
        JsonNode child1 = items.get(0);
        assertEquals("firstName1", child1.get("firstName").asText());
        assertEquals("lastName1", child1.get("lastName").asText());
        assertEquals("email1@email.com", child1.get("email").asText());
        assertEquals("disabled", child1.get("status").asText());
        
        // We don't deserialize this, but let's just verify
        DynamoPagedResourceList<AccountSummary> serPage = BridgeObjectMapper.get().readValue(node.toString(), 
                new TypeReference<DynamoPagedResourceList<AccountSummary>>() {});
        
        assertEquals(page.getTotal(), serPage.getTotal());
        assertEquals(page.getLastKey(), serPage.getLastKey());
        assertEquals(page.getPageSize(), serPage.getPageSize());
        assertEquals(page.getFilters().get("idFilter"), serPage.getFilters().get("idFilter"));
        assertEquals(page.getFilters().get("assignmentFilter"), serPage.getFilters().get("assignmentFilter"));
        assertEquals(page.getItems(), serPage.getItems());
    }
    
}
