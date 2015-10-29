package org.sagebionetworks.bridge.models.accounts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.databind.JsonNode;

import nl.jqno.equalsverifier.EqualsVerifier;

public class UserConsentHistoryTest {
    @Test
    public void equalsHashCode() {
        EqualsVerifier.forClass(UserConsentHistory.class).allFieldsShouldBeUsed().verify();
    }
    
    // We do not currently expose this as JSON (in fact we never return a healthCode through the API), 
    // but it's possible we will, so verify this works.
    @Test
    public void serialization() throws Exception {
        long consentCreatedOn = 1446136164293L;
        long signedOn = 1446136182504L;
        long withdrewOn = 1446136196168L;
        
        UserConsentHistory history = new UserConsentHistory.Builder()
           .withHealthCode("AAA")
           .withStudyIdentifier("BBB")
           .withConsentCreatedOn(consentCreatedOn)
           .withName("CCC")
           .withBirthdate("1980-04-02")
           .withImageData("imageData")
           .withImageMimeType("image/png")
           .withSignedOn(signedOn)
           .withWithdrewOn(withdrewOn)
           .withHasSignedActiveConsent(true).build();
       
        // Do not print the healthCode in any loging that is done.
        assertTrue(history.toString().contains("healthCode=[REDACTED]"));
        
        String json = BridgeObjectMapper.get().writeValueAsString(history);
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        
        assertEquals("AAA", node.get("healthCode").asText());
        assertEquals("BBB", node.get("studyIdentifier").asText());
        assertEquals("2015-10-29T16:29:24.293Z", node.get("consentCreatedOn").asText());
        assertEquals("CCC", node.get("name").asText());
        assertEquals("1980-04-02", node.get("birthdate").asText());
        assertEquals("imageData", node.get("imageData").asText());
        assertEquals("image/png", node.get("imageMimeType").asText());
        assertEquals("2015-10-29T16:29:42.504Z", node.get("signedOn").asText());
        assertEquals("2015-10-29T16:29:56.168Z", node.get("withdrewOn").asText());
        assertEquals(true, node.get("hasSignedActiveConsent").asBoolean());
        assertEquals("UserConsentHistory", node.get("type").asText());
        
        UserConsentHistory newHistory = BridgeObjectMapper.get().readValue(json, UserConsentHistory.class);
        assertEquals(history, newHistory);
    }
}
