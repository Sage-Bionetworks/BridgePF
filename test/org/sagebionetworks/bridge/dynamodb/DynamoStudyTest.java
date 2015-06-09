package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.*;

import org.junit.Test;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Main functionality we want to verify in this test is that study can be serialized with all values, 
 * but filtered in the API to exclude read-only studies when exposed to researchers.
 *
 */
public class DynamoStudyTest {

    @Test
    public void studyFullySerializesForCaching() throws Exception {
        DynamoStudy study = TestUtils.getValidStudy();
        study.setVersion(2L);
        
        String json = BridgeObjectMapper.get().writeValueAsString(study);
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        
        assertEquals(study.getConsentNotificationEmail(), node.get("consentNotificationEmail").asText());
        assertEquals(study.getSupportEmail(), node.get("supportEmail").asText());
        assertEquals(study.getTechnicalEmail(), node.get("technicalEmail").asText());
        assertEquals(study.getSponsorName(), node.get("sponsorName").asText());
        assertEquals(study.getName(), node.get("name").asText());
        assertEquals(study.isActive(), node.get("active").asBoolean());
        assertEquals(study.getIdentifier(), node.get("identifier").asText());
        assertEquals(study.getMinAgeOfConsent(), node.get("minAgeOfConsent").asInt());
        assertEquals(study.getMaxNumOfParticipants(), node.get("maxNumOfParticipants").asInt());
        assertEquals(study.getStormpathHref(), node.get("stormpathHref").asText());
        assertEquals(study.getResearcherRole(), node.get("researcherRole").asText());
        assertEquals(study.getPasswordPolicy(), JsonUtils.asEntity(node, "passwordPolicy", PasswordPolicy.class));
        assertEquals(study.getVerifyEmailTemplate(), JsonUtils.asEntity(node, "verifyEmailTemplate", EmailTemplate.class));
        assertEquals(study.getResetPasswordTemplate(), JsonUtils.asEntity(node, "resetPasswordTemplate", EmailTemplate.class));
        assertEquals(study.getUserProfileAttributes(), JsonUtils.asStringSet(node, "userProfileAttributes"));
        assertEquals((Long)study.getVersion(), (Long)node.get("version").asLong());
        assertEquals("Study", node.get("type").asText());
        
        // It all has to be in data as well, until we've migrated
        JsonNode studyFromData = new ObjectMapper().readTree(study.getData());
        assertEquals(study.getResearcherRole(), studyFromData.get("researcherRole").asText());
        assertEquals(study.getMinAgeOfConsent(), studyFromData.get("minAgeOfConsent").asInt());
        assertEquals(study.getMaxNumOfParticipants(), studyFromData.get("maxNumOfParticipants").asInt());
        assertEquals(study.getStormpathHref(), studyFromData.get("stormpathHref").asText());
        assertEquals(study.getSupportEmail(), studyFromData.get("supportEmail").asText());
        assertEquals(study.getConsentNotificationEmail(), studyFromData.get("consentNotificationEmail").asText());
        assertEquals(study.getSponsorName(), studyFromData.get("sponsorName").asText());
        assertEquals(study.getTechnicalEmail(), studyFromData.get("technicalEmail").asText());
        assertEquals(study.isActive(), studyFromData.get("active").asBoolean());
        assertEquals(study.getPasswordPolicy(), JsonUtils.asEntity(studyFromData, "passwordPolicy", PasswordPolicy.class));
        assertEquals(study.getVerifyEmailTemplate(), JsonUtils.asEntity(studyFromData, "verifyEmailTemplate", EmailTemplate.class));
        assertEquals(study.getResetPasswordTemplate(), JsonUtils.asEntity(studyFromData, "resetPasswordTemplate", EmailTemplate.class));
        assertEquals(study.getUserProfileAttributes(), JsonUtils.asStringSet(studyFromData, "userProfileAttributes"));
        
        // Using the filtered view of a study, this should not include a couple of fields we don't expose to researchers.
        // Negates the need for a view wrapper object, is contextually adjustable, unlike @JsonIgnore.
        // You do need to create a new instance of the writer from a new mapper, SFAICT. This is stored as 
        // DynamoStudy.STUDY_WRITER.
        json = DynamoStudy.STUDY_WRITER.writeValueAsString(study);
        study = BridgeObjectMapper.get().readValue(json, DynamoStudy.class);
        assertNull(study.getResearcherRole());
        assertNull(study.getStormpathHref());
        assertEquals("Study", node.get("type").asText());
    }
    
}
