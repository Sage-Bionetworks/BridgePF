package org.sagebionetworks.bridge.models;

import static org.junit.Assert.assertEquals;

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

public class PagedResourceListTest {

    @SuppressWarnings("deprecation")
    @Test
    public void canSerialize() throws Exception {
        List<AccountSummary> accounts = Lists.newArrayListWithCapacity(2);
        accounts.add(new AccountSummary("firstName1", "lastName1", "email1@email.com", TestConstants.PHONE,
                null, ImmutableMap.of("substudy1", "externalId1"), "id", DateTime.now(), AccountStatus.DISABLED, TestConstants.TEST_STUDY, ImmutableSet.of()));
        accounts.add(new AccountSummary("firstName2", "lastName2", "email2@email.com", TestConstants.PHONE,
                null, ImmutableMap.of("substudy2", "externalId2"), "id2", DateTime.now(), AccountStatus.ENABLED, TestConstants.TEST_STUDY, ImmutableSet.of()));

        DateTime startTime = DateTime.parse("2016-02-03T10:10:10.000-08:00");
        DateTime endTime = DateTime.parse("2016-02-23T14:14:14.000-08:00");
        
        PagedResourceList<AccountSummary> page = new PagedResourceList<AccountSummary>(accounts, 2)
                .withRequestParam("offsetBy", 123)
                .withRequestParam("pageSize", 100)
                .withRequestParam("startTime", startTime)
                .withRequestParam("endTime", endTime)
                .withRequestParam("emailFilter", "filterString");
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(page);
        assertEquals(123, node.get("offsetBy").intValue());
        assertEquals(2, node.get("total").intValue());
        assertEquals(100, node.get("pageSize").intValue());
        assertEquals("filterString", node.get("emailFilter").asText());
        assertEquals(startTime.toString(), node.get("startTime").asText());
        assertEquals(endTime.toString(), node.get("endTime").asText());
        assertEquals("PagedResourceList", node.get("type").asText());
        
        JsonNode rp = node.get("requestParams");
        assertEquals(123, rp.get("offsetBy").intValue());
        assertEquals(100, rp.get("pageSize").intValue());
        assertEquals(startTime.toString(), rp.get("startTime").asText());
        assertEquals(endTime.toString(), rp.get("endTime").asText());
        assertEquals("filterString", rp.get("emailFilter").asText());
        assertEquals(ResourceList.REQUEST_PARAMS, rp.get(ResourceList.TYPE).textValue());
                
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
        assertEquals(100, serPage.getPageSize());
        assertEquals((Integer)123, serPage.getOffsetBy());
        assertEquals(startTime, serPage.getStartTime());
        assertEquals(endTime, serPage.getEndTime());
        assertEquals("filterString", serPage.getEmailFilter());
        
        Map<String,Object> params = page.getRequestParams();
        Map<String,Object> serParams = serPage.getRequestParams();
        assertEquals(params, serParams);
        
        assertEquals(page.getItems(), serPage.getItems());
    }
    
    @Test(expected = NullPointerException.class)
    public void totalCannotBeNull() throws Exception {
        List<AccountSummary> accounts = Lists.newArrayListWithCapacity(2);
        new PagedResourceList<AccountSummary>(accounts, null);
    }
}
