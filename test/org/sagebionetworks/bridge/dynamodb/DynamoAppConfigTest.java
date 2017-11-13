package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.sagebionetworks.bridge.models.OperatingSystem.ANDROID;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.appconfig.AndroidAppLink;
import org.sagebionetworks.bridge.models.appconfig.AppConfig;
import org.sagebionetworks.bridge.models.appconfig.AppleAppLink;
import org.sagebionetworks.bridge.models.schedules.SchemaReference;
import org.sagebionetworks.bridge.models.schedules.SurveyReference;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class DynamoAppConfigTest {

    private static final String LABEL = "label";
    private static final DateTime TIMESTAMP = DateTime.now(DateTimeZone.UTC);
    private static final HashSet<String> SET_B = Sets.newHashSet("c","d");
    private static final HashSet<String> SET_A = Sets.newHashSet("a","b");
    private static final List<SurveyReference> SURVEY_REFS = new ImmutableList.Builder<SurveyReference>()
            .add(new SurveyReference("surveyA", BridgeUtils.generateGuid(), DateTime.now(DateTimeZone.UTC)))
            .add(new SurveyReference("surveyB", BridgeUtils.generateGuid(), DateTime.now(DateTimeZone.UTC))).build();
    private static final List<SchemaReference> SCHEMA_REFS = new ImmutableList.Builder<SchemaReference>()
            .add(new SchemaReference("schemaA", 1))
            .add(new SchemaReference("schemaB", 2)).build();
    
    private static final AppleAppLink APPLE_APP_LINK = new AppleAppLink("studyId", Lists.newArrayList("/appId/","/appId/*"));
    private static final List<AppleAppLink> APPLE_APP_LINKS = Lists.newArrayList(APPLE_APP_LINK);
    
    private static final AndroidAppLink ANDROID_APP_LINK = new AndroidAppLink("namespace", "package_name",
            Lists.newArrayList("sha256_cert_fingerprints"));    
    private static final List<AndroidAppLink> ANDROID_APP_LINKS = Lists.newArrayList(ANDROID_APP_LINK);
    
    private static final StudyIdentifier STUDY_ID = new StudyIdentifierImpl(TestUtils.randomName(DynamoAppConfigDaoTest.class));
    
    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(DynamoCriteria.class).suppress(Warning.NONFINAL_FIELDS).allFieldsShouldBeUsed()
                .verify();
    }

    @Test
    public void canSerialize() throws Exception {
        Criteria criteria;
        criteria = TestUtils.createCriteria(2, 8, SET_A, SET_B);
        criteria.setMinAppVersion(ANDROID, 10);
        criteria.setMaxAppVersion(ANDROID, 15);
        criteria.setLanguage("fr");

        JsonNode clientData = TestUtils.getClientData();
        
        AppConfig appConfig = AppConfig.create();
        appConfig.setStudyId(STUDY_ID.getIdentifier());
        appConfig.setLabel(LABEL);
        appConfig.setCriteria(criteria);
        appConfig.setCreatedOn(TIMESTAMP.getMillis());
        appConfig.setModifiedOn(TIMESTAMP.getMillis());
        appConfig.setSurveyReferences(SURVEY_REFS);
        appConfig.setSchemaReferences(SCHEMA_REFS);
        appConfig.setClientData(clientData);
        appConfig.setAppleAppLinks(APPLE_APP_LINKS);
        appConfig.setAndroidAppLinks(ANDROID_APP_LINKS);
        appConfig.setVersion(3L);
        
        Set<String> fields = Sets.newHashSet("criteria", "label", "createdOn", "modifiedOn", "clientData",
                "surveyReferences", "schemaReferences", "version", "appleAppLinks", "androidAppLinks", "type");
                
        JsonNode node = BridgeObjectMapper.get().valueToTree(appConfig);
        assertEquals(fields, Sets.newHashSet(node.fieldNames()));
        
        assertEquals("AppConfig", node.get("type").asText());
        assertEquals(TIMESTAMP.toString(), node.get("createdOn").textValue());
        assertEquals(TIMESTAMP.toString(), node.get("modifiedOn").textValue());
        
        AppConfig deser = BridgeObjectMapper.get().treeToValue(node, AppConfig.class);
        assertNull(deser.getStudyId());
        assertEquals(appConfig.getCriteria(), deser.getCriteria());
        assertEquals(appConfig.getLabel(), deser.getLabel());
        assertEquals(appConfig.getClientData().toString(), deser.getClientData().toString());
        assertEquals(appConfig.getSurveyReferences(), deser.getSurveyReferences());
        assertEquals(appConfig.getSchemaReferences(), deser.getSchemaReferences());
        assertEquals(appConfig.getCreatedOn(), deser.getCreatedOn());
        assertEquals(appConfig.getModifiedOn(), deser.getModifiedOn());
        assertEquals(appConfig.getGuid(), deser.getGuid());
        assertEquals(APPLE_APP_LINKS, appConfig.getAppleAppLinks());
        assertEquals(ANDROID_APP_LINKS, appConfig.getAndroidAppLinks());
        assertEquals(appConfig.getVersion(), deser.getVersion());
    }
    
}
