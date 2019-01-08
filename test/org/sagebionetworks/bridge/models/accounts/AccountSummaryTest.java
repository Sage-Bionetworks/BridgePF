package org.sagebionetworks.bridge.models.accounts;

import static org.junit.Assert.assertEquals;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import nl.jqno.equalsverifier.EqualsVerifier;

public class AccountSummaryTest {
    
    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(AccountSummary.class).allFieldsShouldBeUsed().verify();
    }
    
    @Test
    public void canSerialize() throws Exception {
        // Set the time zone so it's not UTC, it should be converted to UTC so the strings are 
        // equal below (to demonstrate the ISO 8601 string is in UTC time zone).
        DateTime dateTime = DateTime.now().withZone(DateTimeZone.forOffsetHours(-8));
        AccountSummary summary = new AccountSummary("firstName", "lastName", "email@email.com", TestConstants.PHONE,
                "oldExternalId", ImmutableMap.of("sub1", "externalId"), "ABC", dateTime, AccountStatus.UNVERIFIED,
                TestConstants.TEST_STUDY, ImmutableSet.of("sub1", "sub2"));
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(summary);
        assertEquals("firstName", node.get("firstName").textValue());
        assertEquals("lastName", node.get("lastName").textValue());
        assertEquals("email@email.com", node.get("email").textValue());
        assertEquals("ABC", node.get("id").textValue());
        assertEquals(TestConstants.PHONE.getNumber(), node.get("phone").get("number").textValue());
        assertEquals(TestConstants.PHONE.getRegionCode(), node.get("phone").get("regionCode").textValue());
        assertEquals(TestConstants.PHONE.getNationalFormat(), node.get("phone").get("nationalFormat").textValue());
        assertEquals("oldExternalId", node.get("externalId").textValue());
        assertEquals("externalId", node.get("externalIds").get("sub1").textValue());
        assertEquals(dateTime.withZone(DateTimeZone.UTC).toString(), node.get("createdOn").textValue());
        assertEquals("unverified", node.get("status").textValue());
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, node.get("studyIdentifier").get("identifier").textValue());
        assertEquals("sub1", node.get("substudyIds").get(0).textValue());
        assertEquals("sub2", node.get("substudyIds").get(1).textValue());
        assertEquals("AccountSummary", node.get("type").textValue());
        
        AccountSummary newSummary = BridgeObjectMapper.get().treeToValue(node, AccountSummary.class);
        assertEquals(summary, newSummary);
    }
}
