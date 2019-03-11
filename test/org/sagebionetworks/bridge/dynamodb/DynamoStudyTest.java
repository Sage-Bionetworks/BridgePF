package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.Test;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.OperatingSystem;
import org.sagebionetworks.bridge.models.studies.AndroidAppLink;
import org.sagebionetworks.bridge.models.studies.AppleAppLink;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.OAuthProvider;
import org.sagebionetworks.bridge.models.studies.OAuthProviderTest;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.SmsTemplate;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;

/**
 * Main functionality we want to verify in this test is that study can be serialized with all values, 
 * but filtered in the API to exclude read-only studies when exposed to researchers.
 */
public class DynamoStudyTest {
    private static final List<AppleAppLink> APPLE_APP_LINKS = Lists.newArrayList(TestConstants.APPLE_APP_LINK);
    private static final List<AndroidAppLink> ANDROID_APP_LINKS = Lists.newArrayList(TestConstants.ANDROID_APP_LINK);

    @Test
    public void automaticCustomEventsIsNeverNull() {
        // Starts as empty
        Study study = Study.create();
        assertTrue(study.getAutomaticCustomEvents().isEmpty());

        // Set value works
        Map<String, String> dummyMap = ImmutableMap.of("3-days-after-enrollment", "P3D");
        study.setAutomaticCustomEvents(dummyMap);
        assertEquals(dummyMap, study.getAutomaticCustomEvents());

        // Set to null makes it empty again
        study.setAutomaticCustomEvents(null);
        assertTrue(study.getAutomaticCustomEvents().isEmpty());
    }

    @Test
    public void uploadMetadataFieldDefListIsNeverNull() {
        // make field for test
        List<UploadFieldDefinition> fieldDefList = new ArrayList<>();
        fieldDefList.add(new UploadFieldDefinition.Builder().withName("test-field")
                .withType(UploadFieldType.ATTACHMENT_V2).build());

        // starts as empty
        Study study = new DynamoStudy();
        assertTrue(study.getUploadMetadataFieldDefinitions().isEmpty());

        // set value works
        study.setUploadMetadataFieldDefinitions(fieldDefList);
        assertEquals(fieldDefList, study.getUploadMetadataFieldDefinitions());

        // set to null makes it empty again
        study.setUploadMetadataFieldDefinitions(null);
        assertTrue(study.getUploadMetadataFieldDefinitions().isEmpty());
    }

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
        
        OAuthProvider oauthProvider = new OAuthProvider("clientId", "secret", "endpoint",
                OAuthProviderTest.CALLBACK_URL);
        study.getOAuthProviders().put("myProvider", oauthProvider);

        study.setAutomaticCustomEvents(ImmutableMap.of("3-days-after-enrollment", "P3D"));
        study.setVersion(2L);
        study.setMinSupportedAppVersions(ImmutableMap.<String, Integer>builder().put(OperatingSystem.IOS, 2).build());
        study.setUploadMetadataFieldDefinitions(ImmutableList.of(new UploadFieldDefinition.Builder()
                .withName("test-metadata-field").withType(UploadFieldType.INT).build()));
        study.setAndroidAppLinks(ANDROID_APP_LINKS);
        study.setAppleAppLinks(APPLE_APP_LINKS);

        final JsonNode node = BridgeObjectMapper.get().valueToTree(study);

        assertTrue(node.get("autoVerificationEmailSuppressed").booleanValue());
        assertEqualsAndNotNull(study.getConsentNotificationEmail(), node.get("consentNotificationEmail").asText());
        assertFalse(node.get("participantIpLockingEnabled").booleanValue());
        assertTrue(node.get("studyIdExcludedInExport").booleanValue());
        assertEqualsAndNotNull(study.getSupportEmail(), node.get("supportEmail").asText());
        assertEqualsAndNotNull(study.getSynapseDataAccessTeamId(), node.get("synapseDataAccessTeamId").longValue());
        assertEqualsAndNotNull(study.getSynapseProjectId(), node.get("synapseProjectId").textValue());
        assertEqualsAndNotNull(study.getTechnicalEmail(), node.get("technicalEmail").asText());
        assertEqualsAndNotNull(study.getUploadValidationStrictness().toString().toLowerCase(),
                node.get("uploadValidationStrictness").textValue());
        assertTrue(node.get("usesCustomExportSchedule").asBoolean());
        assertEqualsAndNotNull(study.getSponsorName(), node.get("sponsorName").asText());
        assertEqualsAndNotNull(study.getName(), node.get("name").asText());
        assertEqualsAndNotNull(study.getShortName(), node.get("shortName").textValue());
        assertEqualsAndNotNull(study.isActive(), node.get("active").asBoolean());
        assertEqualsAndNotNull(study.getIdentifier(), node.get("identifier").asText());
        assertEqualsAndNotNull(study.getMinAgeOfConsent(), node.get("minAgeOfConsent").asInt());
        assertEqualsAndNotNull(study.getPasswordPolicy(), JsonUtils.asEntity(node, "passwordPolicy", PasswordPolicy.class));
        assertEqualsAndNotNull(study.getVerifyEmailTemplate(),
                JsonUtils.asEntity(node, "verifyEmailTemplate", EmailTemplate.class));
        assertEqualsAndNotNull(study.getResetPasswordTemplate(),
                JsonUtils.asEntity(node, "resetPasswordTemplate", EmailTemplate.class));
        assertEqualsAndNotNull(study.getEmailSignInTemplate(),
                JsonUtils.asEntity(node, "emailSignInTemplate", EmailTemplate.class));
        assertEqualsAndNotNull(study.getAccountExistsTemplate(),
                JsonUtils.asEntity(node, "accountExistsTemplate", EmailTemplate.class));
        assertEqualsAndNotNull(study.getSignedConsentTemplate(),
                JsonUtils.asEntity(node, "signedConsentTemplate", EmailTemplate.class));
        assertEqualsAndNotNull(study.getAppInstallLinkTemplate(),
                JsonUtils.asEntity(node, "appInstallLinkTemplate", EmailTemplate.class));
        assertEquals(study.getResetPasswordSmsTemplate(),
                JsonUtils.asEntity(node, "resetPasswordSmsTemplate", SmsTemplate.class));
        assertEquals(study.getPhoneSignInSmsTemplate(),
                JsonUtils.asEntity(node, "phoneSignInSmsTemplate", SmsTemplate.class));
        assertEquals(study.getAppInstallLinkSmsTemplate(),
                JsonUtils.asEntity(node, "appInstallLinkSmsTemplate", SmsTemplate.class));
        assertEquals(study.getVerifyPhoneSmsTemplate(),
                JsonUtils.asEntity(node, "verifyPhoneSmsTemplate", SmsTemplate.class));
        assertEquals(study.getAccountExistsSmsTemplate(),
                JsonUtils.asEntity(node, "accountExistsSmsTemplate", SmsTemplate.class));
        assertEquals(study.getSignedConsentSmsTemplate(),
                JsonUtils.asEntity(node, "signedConsentSmsTemplate", SmsTemplate.class));
        assertEqualsAndNotNull(study.getUserProfileAttributes(), JsonUtils.asStringSet(node, "userProfileAttributes"));
        assertEqualsAndNotNull(study.getTaskIdentifiers(), JsonUtils.asStringSet(node, "taskIdentifiers"));
        assertEqualsAndNotNull(study.getActivityEventKeys(), JsonUtils.asStringSet(node, "activityEventKeys"));
        assertEqualsAndNotNull(study.getDataGroups(), JsonUtils.asStringSet(node, "dataGroups"));
        assertEqualsAndNotNull(study.getVersion(), node.get("version").longValue());
        assertTrue(node.get("strictUploadValidationEnabled").asBoolean());
        assertTrue(node.get("healthCodeExportEnabled").asBoolean());
        assertTrue(node.get("emailVerificationEnabled").asBoolean());
        assertTrue(node.get("externalIdValidationEnabled").asBoolean());
        assertTrue(node.get("externalIdRequiredOnSignup").asBoolean());
        assertTrue(node.get("emailSignInEnabled").asBoolean());
        assertTrue(node.get("reauthenticationEnabled").booleanValue());
        assertTrue(node.get("autoVerificationPhoneSuppressed").booleanValue());
        assertTrue(node.get("verifyChannelOnSignInEnabled").booleanValue());
        assertEquals(0, node.get("accountLimit").asInt());
        assertFalse(node.get("disableExport").asBoolean());
        assertEqualsAndNotNull("Study", node.get("type").asText());
        assertEqualsAndNotNull(study.getPushNotificationARNs().get(OperatingSystem.IOS),
                node.get("pushNotificationARNs").get(OperatingSystem.IOS).asText());
        assertEqualsAndNotNull(study.getPushNotificationARNs().get(OperatingSystem.ANDROID),
                node.get("pushNotificationARNs").get(OperatingSystem.ANDROID).asText());

        JsonNode automaticCustomEventsNode = node.get("automaticCustomEvents");
        assertEquals(1, automaticCustomEventsNode.size());
        assertEquals("P3D", automaticCustomEventsNode.get("3-days-after-enrollment").textValue());

        JsonNode appleLink = node.get("appleAppLinks").get(0);
        assertEquals("studyId", appleLink.get("appID").textValue());
        assertEquals("/appId/", appleLink.get("paths").get(0).textValue());
        assertEquals("/appId/*", appleLink.get("paths").get(1).textValue());
        
        JsonNode androidLink = node.get("androidAppLinks").get(0);
        assertEquals("namespace", androidLink.get("namespace").textValue());
        assertEquals("package_name", androidLink.get("package_name").textValue());
        assertEquals("sha256_cert_fingerprints", androidLink.get("sha256_cert_fingerprints").get(0).textValue());

        // validate minAppVersion
        JsonNode supportedVersionsNode = JsonUtils.asJsonNode(node, "minSupportedAppVersions");
        assertNotNull(supportedVersionsNode);
        assertEqualsAndNotNull(
                study.getMinSupportedAppVersions().get(OperatingSystem.IOS), 
                supportedVersionsNode.get(OperatingSystem.IOS).intValue());

        // validate metadata field defs
        JsonNode metadataFieldDefListNode = node.get("uploadMetadataFieldDefinitions");
        assertEquals(1, metadataFieldDefListNode.size());
        JsonNode oneMetadataFieldDefNode = metadataFieldDefListNode.get(0);
        assertEquals("test-metadata-field", oneMetadataFieldDefNode.get("name").textValue());
        assertEquals("int", oneMetadataFieldDefNode.get("type").textValue());

        JsonNode providerNode = node.get("oAuthProviders").get("myProvider");
        assertEquals("clientId", providerNode.get("clientId").textValue());
        assertEquals("secret", providerNode.get("secret").textValue());
        assertEquals("endpoint", providerNode.get("endpoint").textValue());
        assertEquals(OAuthProviderTest.CALLBACK_URL, providerNode.get("callbackUrl").textValue());
        assertEquals("OAuthProvider", providerNode.get("type").textValue());
        
        // Deserialize back to a POJO and verify.
        final Study deserStudy = BridgeObjectMapper.get().readValue(node.toString(), Study.class);
        assertEquals(study, deserStudy);
    }
    
    @Test
    public void testThatEmptyMinSupportedVersionMapperDoesNotThrowException() throws Exception {
        final DynamoStudy study = TestUtils.getValidStudy(DynamoStudyTest.class);
        study.setVersion(2L);

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
