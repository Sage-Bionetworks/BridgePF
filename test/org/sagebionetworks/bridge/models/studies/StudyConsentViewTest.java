package org.sagebionetworks.bridge.models.studies;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.sagebionetworks.bridge.dynamodb.DynamoStudyConsent1;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;

public class StudyConsentViewTest {

    @Test
    public void testSerialization() throws Exception {
        long createdOn = 200L;
        
        DynamoStudyConsent1 consent = new DynamoStudyConsent1();
        consent.setActive(true);
        consent.setCreatedOn(createdOn);
        consent.setStudyKey("test");
        consent.setStoragePath("test."+createdOn);
        consent.setVersion(2L);
        
        StudyConsentView view = new StudyConsentView(consent, "<document/>");
        
        String json = BridgeObjectMapper.get().writeValueAsString(view);
        assertTrue("document correct", json.contains("\"documentContent\":\"<document/>\""));
        assertTrue("active state correct", json.contains("\"active\":true"));
        assertTrue("createdOn correct", json.contains("\"createdOn\":\"1970-01-01T00:00:00.200Z\""));
        assertTrue("type correct", json.contains("\"type\":\"StudyConsent\""));
        
        StudyConsentForm form = BridgeObjectMapper.get().readValue(json, StudyConsentForm.class);
        assertEquals("<document/>", form.getDocumentContent());
        
    }
    
}
