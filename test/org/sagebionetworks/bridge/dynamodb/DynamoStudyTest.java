package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.*;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

import org.junit.Test;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Main functionality we want to verify in this test is that study can be serialized with all values, 
 * but filtered in the API to exclude read-only studies when exposed to researchers.
 *
 */
public class DynamoStudyTest {
    
    @Test
    public void equalsHashCode() {
        // studyIdentifier is derived from the identifier
        EqualsVerifier.forClass(DynamoStudy.class).allFieldsShouldBeUsed()
            .suppress(Warning.NONFINAL_FIELDS)
            .withPrefabValues(ObjectMapper.class, new ObjectMapper(), new ObjectMapper())
            .withPrefabValues(JsonFactory.class, new JsonFactory(), new JsonFactory()).verify();
    }

    @Test
    public void studyFullySerializesForCaching() throws Exception {
        final DynamoStudy study = TestUtils.getValidStudy(DynamoStudyTest.class);
        study.setVersion(2L);
        study.getMinSupportedAppVersions().put("iPhone OS", 2);
        study.setStormpathHref("test");
        
        final String json = BridgeObjectMapper.get().writeValueAsString(study);
        final JsonNode node = BridgeObjectMapper.get().readTree(json);
        
        assertEqualsAndNotNull(study.getConsentNotificationEmail(), node.get("consentNotificationEmail").asText());
        assertEqualsAndNotNull(study.getSupportEmail(), node.get("supportEmail").asText());
        assertEqualsAndNotNull(study.getSynapseDataAccessTeamId(), node.get("synapseDataAccessTeamId").longValue());
        assertEqualsAndNotNull(study.getSynapseProjectId(), node.get("synapseProjectId").textValue());
        assertEqualsAndNotNull(study.getTechnicalEmail(), node.get("technicalEmail").asText());
        assertEqualsAndNotNull(study.getSponsorName(), node.get("sponsorName").asText());
        assertEqualsAndNotNull(study.getName(), node.get("name").asText());
        assertEqualsAndNotNull(study.isActive(), node.get("active").asBoolean());
        assertEqualsAndNotNull(study.getIdentifier(), node.get("identifier").asText());
        assertEqualsAndNotNull(study.getMinAgeOfConsent(), node.get("minAgeOfConsent").asInt());
        assertEqualsAndNotNull(study.getMaxNumOfParticipants(), node.get("maxNumOfParticipants").asInt());
        assertEqualsAndNotNull(study.getStormpathHref(), node.get("stormpathHref").asText());
        assertEqualsAndNotNull(study.getPasswordPolicy(), JsonUtils.asEntity(node, "passwordPolicy", PasswordPolicy.class));
        assertEqualsAndNotNull(study.getVerifyEmailTemplate(), JsonUtils.asEntity(node, "verifyEmailTemplate",
                EmailTemplate.class));
        assertEqualsAndNotNull(study.getResetPasswordTemplate(), JsonUtils.asEntity(node, "resetPasswordTemplate", EmailTemplate.class));
        assertEqualsAndNotNull(study.getUserProfileAttributes(), JsonUtils.asStringSet(node, "userProfileAttributes"));
        assertEqualsAndNotNull(study.getTaskIdentifiers(), JsonUtils.asStringSet(node, "taskIdentifiers"));
        assertEqualsAndNotNull(study.getDataGroups(), JsonUtils.asStringSet(node, "dataGroups"));
        assertEqualsAndNotNull((Long)study.getVersion(), (Long)node.get("version").asLong());
        assertTrue(node.get("strictUploadValidationEnabled").asBoolean());
        assertTrue(node.get("healthCodeExportEnabled").asBoolean());
        assertTrue(node.get("emailVerificationEnabled").asBoolean());
        assertEqualsAndNotNull("Study", node.get("type").asText());
        
        JsonNode supportedVersionsNode = JsonUtils.asJsonNode(node, "minSupportedAppVersions");
        assertNotNull(supportedVersionsNode);
        assertEqualsAndNotNull(study.getMinSupportedAppVersions().get("iPhone OS"), (Integer)supportedVersionsNode.get("iPhone OS").asInt());
        
        // Using the filtered view of a study, this should not include a couple of fields we don't expose to researchers.
        // Negates the need for a view wrapper object, is contextually adjustable, unlike @JsonIgnore.
        // You do need to create a new instance of the writer from a new mapper, SFAICT. This is stored as 
        // Study.STUDY_WRITER.
        final String filteredJson = Study.STUDY_WRITER.writeValueAsString(study);
        final JsonNode filteredNode = BridgeObjectMapper.get().readTree(filteredJson);
        assertNull(filteredNode.get("stormpathHref"));
        assertNull(filteredNode.get("active"));

        // Deserialize back to a POJO and verify.
        final Study deserStudy = BridgeObjectMapper.get().readValue(json, Study.class);
        assertEquals(study, deserStudy);
    }
    
    @Test
    public void testThatEmptyMinSupportedVersionMapperDoesNotThrowException() throws Exception {
        final DynamoStudy study = TestUtils.getValidStudy(DynamoStudyTest.class);
        study.setVersion(2L);
        study.setStormpathHref("test");
        
        final String json = BridgeObjectMapper.get().writeValueAsString(study);
        BridgeObjectMapper.get().readTree(json);

        // Deserialize back to a POJO and verify.
        final Study deserStudy = BridgeObjectMapper.get().readValue(json, Study.class);
        assertEquals(study, deserStudy);
    }
    
    @Test
    public void settingStringOrObjectStudyIdentifierSetsTheOther() {
        DynamoStudy study = new DynamoStudy();
        study.setIdentifier("test-study");
        assertEquals(study.getStudyIdentifier(), new StudyIdentifierImpl("test-study"));
        
        study.setIdentifier(null);
        assertNull(study.getStudyIdentifier());
    }
    
    void assertEqualsAndNotNull(Object expected, Object actual) {
        assertNotNull(expected);
        assertNotNull(actual);
        assertEquals(expected, actual);
    }
    
}
