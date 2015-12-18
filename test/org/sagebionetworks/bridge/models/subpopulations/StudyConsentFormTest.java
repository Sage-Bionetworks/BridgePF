package org.sagebionetworks.bridge.models.subpopulations;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.fasterxml.jackson.databind.JsonNode;

public class StudyConsentFormTest {

    // The only part of a study consent that users send to the server is the html content
    // in a JSON payload. The rest is constructed on the server.
    @Test
    public void canSerialize() throws Exception {
        StudyConsentForm form = new StudyConsentForm("<p>This is content</p>");
        
        String json = BridgeObjectMapper.get().writeValueAsString(form);
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        
        assertEquals("<p>This is content</p>", node.get("documentContent").asText());
        assertEquals("StudyConsent", node.get("type").asText());
        
        StudyConsentForm newForm = BridgeObjectMapper.get().readValue(json, StudyConsentForm.class);
        assertEquals(form.getDocumentContent(), newForm.getDocumentContent());
    }
    
}
