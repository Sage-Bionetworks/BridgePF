package org.sagebionetworks.bridge.models.accounts;

import static org.junit.Assert.assertEquals;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.databind.JsonNode;

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
        AccountSummary summary = new AccountSummary("firstName", "lastName", "email@email.com", dateTime.withZone(DateTimeZone.UTC), AccountStatus.UNVERIFIED);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(summary);
        assertEquals("firstName", node.get("firstName").asText());
        assertEquals("lastName", node.get("lastName").asText());
        assertEquals("email@email.com", node.get("email").asText());
        assertEquals(dateTime.withZone(DateTimeZone.UTC).toString(), node.get("createdOn").asText());
        assertEquals("unverified", node.get("status").asText());
        assertEquals("AccountSummary", node.get("type").asText());
        
        AccountSummary newSummary = BridgeObjectMapper.get().treeToValue(node, AccountSummary.class);
        assertEquals(summary, newSummary);
    }
    
}
