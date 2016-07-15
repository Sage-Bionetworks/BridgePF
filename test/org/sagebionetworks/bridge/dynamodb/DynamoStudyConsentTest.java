package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.databind.JsonNode;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class DynamoStudyConsentTest {

    @Test 
    public void equalsHashCode() {
        EqualsVerifier.forClass(DynamoStudyConsent1.class).suppress(Warning.NONFINAL_FIELDS).allFieldsShouldBeUsed().verify();
    }
    
    // This is exposed through the StudyConsentController.getAllConsents() call, and 
    // so should be verified. It looks the same as other APIs except it does not include
    // the content of the consent. This is documented.
    @Test
    public void canSerialize() throws Exception {
       DynamoStudyConsent1 consent = new DynamoStudyConsent1();
       consent.setCreatedOn(123L);
       consent.setStoragePath("storagePath");
       consent.setSubpopulationGuid("ABC");
       consent.setVersion(2L);
       
       String json = BridgeObjectMapper.get().writeValueAsString(consent);
       JsonNode node = BridgeObjectMapper.get().readTree(json);
       
       assertEquals("1970-01-01T00:00:00.123Z", node.get("createdOn").asText());
       assertEquals("ABC", node.get("subpopulationGuid").asText());
       assertEquals("StudyConsent", node.get("type").asText());
       assertEquals(4, node.size());
    }
    
}
