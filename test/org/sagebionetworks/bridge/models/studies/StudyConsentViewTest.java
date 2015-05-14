package org.sagebionetworks.bridge.models.studies;

import static org.junit.Assert.assertEquals;

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
        assertEquals("{\"documentContent\":\"<document/>\",\"createdOn\":200,\"active\":true,\"type\":\"StudyConsent\"}", json);
        
        StudyConsentForm form = BridgeObjectMapper.get().readValue(json, StudyConsentForm.class);
        assertEquals("<document/>", form.getDocumentContent());
        
    }
    
}
