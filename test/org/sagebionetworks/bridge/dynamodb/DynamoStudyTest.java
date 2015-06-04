package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.*;

import org.junit.Test;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Sets;

/**
 * Main functionality we want to verify in this test is that study can be serialized with all values, 
 * but filtered in the API to exclude read-only studies when exposed to researchers.
 *
 */
public class DynamoStudyTest {

    @Test
    public void studyFullySerializesForCaching() throws Exception {
        DynamoStudy study = new DynamoStudy();
        study.setConsentNotificationEmail("email1@test.com");
        study.setSupportEmail("email2@test.com");
        study.setName("Test Study Name");
        study.setIdentifier("teststudy");
        study.setMinAgeOfConsent(18);
        study.setMaxNumOfParticipants(200);
        study.setStormpathHref("http://test.com/");
        study.setResearcherRole("test_researcher");
        study.setPasswordPolicy(new PasswordPolicy(8, true, true, true));
        study.setVerifyEmailTemplate(new EmailTemplate("Subject1", "Body1"));
        study.setResetPasswordTemplate(new EmailTemplate("Subject2", "Body2"));
        study.setUserProfileAttributes(Sets.newHashSet("a", "b"));
        study.setVersion(2L);
        
        String json = BridgeObjectMapper.get().writeValueAsString(study);
        JsonNode node = BridgeObjectMapper.get().readTree(json);
        
        assertEquals("email1@test.com", node.get("consentNotificationEmail").asText());
        assertEquals("email2@test.com", node.get("supportEmail").asText());
        assertEquals("Test Study Name", node.get("name").asText());
        assertEquals("teststudy", node.get("identifier").asText());
        assertEquals(18, node.get("minAgeOfConsent").asInt());
        assertEquals(200, node.get("maxNumOfParticipants").asInt());
        assertEquals("http://test.com/", node.get("stormpathHref").asText());
        assertEquals("test_researcher", node.get("researcherRole").asText());
        assertEquals(new PasswordPolicy(8, true, true, true), JsonUtils.asEntity(node, "passwordPolicy", PasswordPolicy.class));
        assertEquals(new EmailTemplate("Subject1", "Body1"), JsonUtils.asEntity(node, "verifyEmailTemplate", EmailTemplate.class));
        assertEquals(new EmailTemplate("Subject2", "Body2"), JsonUtils.asEntity(node, "resetPasswordTemplate", EmailTemplate.class));
        assertEquals(Sets.newHashSet("a", "b"), JsonUtils.asStringSet(node, "userProfileAttributes"));
        assertEquals(2L, node.get("version").asLong());
        assertEquals("Study", node.get("type").asText());
        
        // Using the filtered view of a study, this should not include a couple of fields we don't expose to researchers.
        // Negates the need for a view wrapper object, is contextually adjustable, unlike @JsonIgnore.
        // You do need to create a new instance of the mapper SFAICT.
        json = DynamoStudy.STUDY_WRITER.writeValueAsString(study);
        study = BridgeObjectMapper.get().readValue(json, DynamoStudy.class);
        assertNull(study.getResearcherRole());
        assertNull(study.getStormpathHref());
    }
    
}
