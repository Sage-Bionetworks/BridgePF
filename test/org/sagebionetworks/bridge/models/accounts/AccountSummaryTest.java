package org.sagebionetworks.bridge.models.accounts;

import static org.junit.Assert.assertEquals;

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
    public void canSerializer() throws Exception {
        AccountSummary summary = new AccountSummary("firstName", "lastName", "email@email.com", AccountStatus.UNVERIFIED);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(summary);
        assertEquals("firstName", node.get("firstName").asText());
        assertEquals("lastName", node.get("lastName").asText());
        assertEquals("email@email.com", node.get("email").asText());
        assertEquals("unverified", node.get("status").asText());
        assertEquals("AccountSummary", node.get("type").asText());
        
        AccountSummary newSummary = BridgeObjectMapper.get().treeToValue(node, AccountSummary.class);
        assertEquals(summary, newSummary);
    }
    
}
