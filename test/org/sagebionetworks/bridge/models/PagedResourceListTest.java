package org.sagebionetworks.bridge.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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
import com.google.common.collect.Lists;

public class PagedResourceListTest {

    @SuppressWarnings("deprecation")
    @Test
    public void canSerialize() throws Exception {
        List<AccountSummary> accounts = Lists.newArrayListWithCapacity(2);
        accounts.add(new AccountSummary("firstName1", "lastName1", "email1@email.com", "id", DateTime.now(),
                AccountStatus.DISABLED, TestConstants.TEST_STUDY));
        accounts.add(new AccountSummary("firstName2", "lastName2", "email2@email.com", "id2", DateTime.now(),
                AccountStatus.ENABLED, TestConstants.TEST_STUDY));

        DateTime startTime = DateTime.parse("2016-02-03T10:10:10.000-08:00");
        DateTime endTime = DateTime.parse("2016-02-23T14:14:14.000-08:00");
        
        PagedResourceList<AccountSummary> page = new PagedResourceList<AccountSummary>(accounts, 2)
                .withRequestParam("offsetBy", 123)
                .withRequestParam("pageSize", 100)
                .withRequestParam("startTime", startTime)
                .withRequestParam("endTime", endTime)
                .withRequestParam("emailFilter", "filterString");
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(page);
        assertEquals(123, node.get("offsetBy").asInt());
        assertEquals(2, node.get("total").asInt());
        assertEquals(100, node.get("pageSize").asInt());
        assertEquals("filterString", node.get("emailFilter").asText());
        assertEquals("PagedResourceList", node.get("type").asText());
        
        JsonNode rp = node.get("requestParams");
        assertEquals(123, rp.get("offsetBy").asInt());
        assertEquals(100, rp.get("pageSize").asInt());
        assertEquals("filterString", rp.get("emailFilter").asText());
                
        ArrayNode items = (ArrayNode)node.get("items");
        assertEquals(2, items.size());
        
        JsonNode child1 = items.get(0);
        assertEquals("firstName1", child1.get("firstName").asText());
        assertEquals("lastName1", child1.get("lastName").asText());
        assertEquals("email1@email.com", child1.get("email").asText());
        assertEquals("id", child1.get("id").asText());
        assertEquals("disabled", child1.get("status").asText());
        
        PagedResourceList<AccountSummary> serPage = BridgeObjectMapper.get().readValue(node.toString(), 
                new TypeReference<PagedResourceList<AccountSummary>>() {});

        assertEquals(page.getTotal(), serPage.getTotal());
        assertEquals(100, page.getPageSize());
        assertEquals((Integer)123, page.getOffsetBy());
        assertEquals("filterString", page.getEmailFilter());
        
        Map<String,Object> params = page.getRequestParams();
        Map<String,Object> serParams = serPage.getRequestParams();
        assertEquals(params.get("offsetBy"), serParams.get("offsetBy"));
        assertEquals(params.get("pageSize"), serParams.get("pageSize"));
        assertEquals(params.get("emailFilter"), serParams.get("emailFilter"));
        
        assertEquals(page.getItems(), serPage.getItems());
    }
    
    @Test
    public void offsetByCanBeNull() throws Exception {
        List<AccountSummary> accounts = Lists.newArrayListWithCapacity(2);
        PagedResourceList<AccountSummary> page = new PagedResourceList<AccountSummary>(accounts, null)
                .withRequestParam("offsetBy", 123)
                .withRequestParam("pageSize", 100)
                .withRequestParam("emailFilter", "filterString");
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(page);
        assertNull(node.get("total"));
        assertEquals(123, node.get("offsetBy").asInt());
        assertEquals(100, node.get("pageSize").asInt());
        assertEquals("filterString", node.get("requestParams").get("emailFilter").asText());
        assertEquals("PagedResourceList", node.get("type").asText());
        assertEquals(6, node.size());
    }
    
    // This test was moved from another class that implemented PagedResourceList for
    // DynamoDB, that was easily incorporated into this implementation. This test verifies
    // that the results are the same as before.
    @Test
    public void canSerializeWithDynamoOffsetKey() throws Exception {
        List<String> accounts = Lists.newArrayListWithCapacity(2);
        accounts.add("value1");
        accounts.add("value2");
        
        PagedResourceList<String> page = new PagedResourceList<>(accounts, null)
                .withRequestParam("offsetBy", 123)
                .withRequestParam("pageSize", 100)
                .withRequestParam("idFilter", "foo")
                .withRequestParam("assignmentFilter", "bar");
        JsonNode node = BridgeObjectMapper.get().valueToTree(page);
        assertNull(node.get("total"));
        assertEquals(100, node.get("pageSize").asInt());
        assertEquals(100, node.get("requestParams").get("pageSize").asInt());
        assertEquals("foo", node.get("requestParams").get("idFilter").asText());
        assertEquals("bar", node.get("requestParams").get("assignmentFilter").asText());
        assertEquals("PagedResourceList", node.get("type").asText());
        
        ArrayNode items = (ArrayNode)node.get("items");
        assertEquals(2, items.size());
        assertEquals("value1", items.get(0).asText());
        assertEquals("value2", items.get(1).asText());
        
        // We don't deserialize this, but let's verify for the SDK
        PagedResourceList<String> serPage = BridgeObjectMapper.get().readValue(node.toString(), 
                new TypeReference<PagedResourceList<String>>() {});
        
        assertEquals(page.getTotal(), serPage.getTotal());
        assertEquals(page.getRequestParams().get("pageSize"), serPage.getRequestParams().get("pageSize"));
        assertEquals(page.getRequestParams().get("offsetKey"), serPage.getRequestParams().get("offsetKey"));
        assertEquals(page.getRequestParams().get("offsetBy"), serPage.getRequestParams().get("offsetBy"));
        assertEquals(page.getRequestParams().get("idFilter"), serPage.getRequestParams().get("idFilter"));
        assertEquals(page.getRequestParams().get("assignmentFilter"), serPage.getRequestParams().get("assignmentFilter"));
        assertEquals(page.getItems(), serPage.getItems());
    }
}
