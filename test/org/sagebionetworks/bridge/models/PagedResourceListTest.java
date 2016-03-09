package org.sagebionetworks.bridge.models;

import static org.junit.Assert.assertEquals;

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
        
        PagedResourceList<AccountSummary> page = new PagedResourceList<>(accounts, 2, 100, 123);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(page);
        assertEquals(2, node.get("offsetBy").asInt());
        assertEquals(123, node.get("total").asInt());
        assertEquals(100, node.get("pageSize").asInt());
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
        assertEquals(page.getItems(), serPage.getItems());
    }
}
