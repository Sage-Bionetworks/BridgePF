package org.sagebionetworks.bridge.models.subpopulations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.sagebionetworks.bridge.dynamodb.DynamoStudyConsent1;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsentForm;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsentView;

import com.fasterxml.jackson.databind.JsonNode;

public class StudyConsentViewTest {

    @Test
    public void testSerialization() throws Exception {
        long createdOn = 200L;
        
        DynamoStudyConsent1 consent = new DynamoStudyConsent1();
        consent.setCreatedOn(createdOn);
        consent.setSubpopulationGuid("test");
        consent.setStoragePath("test."+createdOn);
        consent.setVersion(2L);
        
        StudyConsentView view = new StudyConsentView(consent, "<document/>");
        
        String json = BridgeObjectMapper.get().writeValueAsString(view);
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        
        assertEquals("<document/>", node.get("documentContent").asText());
        assertTrue(node.get("active").asBoolean());
        assertEquals("1970-01-01T00:00:00.200Z", node.get("createdOn").asText());
        assertEquals("test", node.get("subpopulationGuid").asText());
        assertEquals("StudyConsent", node.get("type").asText());
        
        StudyConsentForm form = BridgeObjectMapper.get().readValue(json, StudyConsentForm.class);
        assertEquals("<document/>", form.getDocumentContent());
    }
    
}
