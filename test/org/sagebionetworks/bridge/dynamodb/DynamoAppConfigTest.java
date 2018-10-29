package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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
import org.sagebionetworks.bridge.models.appconfig.AppConfig;
import org.sagebionetworks.bridge.models.schedules.ConfigReference;
import org.sagebionetworks.bridge.models.schedules.SchemaReference;
import org.sagebionetworks.bridge.models.schedules.SurveyReference;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class DynamoAppConfigTest {

    private static final String LABEL = "label";
    private static final DateTime TIMESTAMP = DateTime.now(DateTimeZone.UTC);
    private static final HashSet<String> SET_B = Sets.newHashSet("c","d");
    private static final HashSet<String> SET_A = Sets.newHashSet("a","b");
    private static final List<SurveyReference> SURVEY_REFS = ImmutableList.of(
            new SurveyReference("surveyA", BridgeUtils.generateGuid(), DateTime.now(DateTimeZone.UTC)),
            new SurveyReference("surveyB", BridgeUtils.generateGuid(), DateTime.now(DateTimeZone.UTC)));
    private static final List<SchemaReference> SCHEMA_REFS = ImmutableList.of(
            new SchemaReference("schemaA", 1),
            new SchemaReference("schemaB", 2));
    private static final List<ConfigReference> CONFIG_REFS = ImmutableList.of(
            new ConfigReference("config1", 1L),
            new ConfigReference("config2", 2L));
    private static final StudyIdentifier STUDY_ID = new StudyIdentifierImpl(TestUtils.randomName(DynamoAppConfigDaoTest.class));
    
    @Test
    public void hashCodeEquals() {
        EqualsVerifier.forClass(DynamoCriteria.class).suppress(Warning.NONFINAL_FIELDS).allFieldsShouldBeUsed()
                .verify();
    }
    
    @Test
    public void collectionsDoNotReturnNull() {
        AppConfig config = AppConfig.create();
        assertNotNull(config.getConfigElements());
        assertNotNull(config.getConfigReferences());
        assertNotNull(config.getSchemaReferences());
        assertNotNull(config.getSurveyReferences());
        
        config.setConfigElements(null);
        config.setConfigReferences(null);
        config.setSchemaReferences(null);
        config.setSurveyReferences(null);
        
        assertNotNull(config.getConfigElements());
        assertNotNull(config.getConfigReferences());
        assertNotNull(config.getSchemaReferences());
        assertNotNull(config.getSurveyReferences());
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
        appConfig.setConfigReferences(CONFIG_REFS);
        appConfig.setConfigElements(ImmutableMap.of("config1", TestUtils.getClientData()));
        appConfig.setClientData(clientData);
        appConfig.setVersion(3L);
        appConfig.setDeleted(true);
        
        Set<String> fields = Sets.newHashSet("criteria", "label", "createdOn", "modifiedOn", "clientData",
                "surveyReferences", "schemaReferences", "configReferences", "configElements", "version", 
                "type", "deleted");
                
        JsonNode node = BridgeObjectMapper.get().valueToTree(appConfig);
        assertEquals(fields, Sets.newHashSet(node.fieldNames()));
        
        TestUtils.assertNode(clientData, node.get("clientData"));
        assertEquals("AppConfig", node.get("type").asText());
        assertEquals(TIMESTAMP.toString(), node.get("createdOn").textValue());
        assertEquals(TIMESTAMP.toString(), node.get("modifiedOn").textValue());
        
        AppConfig deser = BridgeObjectMapper.get().treeToValue(node, AppConfig.class);
        assertNull(deser.getStudyId());
        
        TestUtils.assertNode(clientData, deser.getClientData());
        TestUtils.assertNode(clientData, deser.getConfigElements().get("config1"));
        assertEquals(appConfig.getCriteria(), deser.getCriteria());
        assertEquals(appConfig.getLabel(), deser.getLabel());
        assertEquals(appConfig.getSurveyReferences(), deser.getSurveyReferences());
        assertEquals(appConfig.getSchemaReferences(), deser.getSchemaReferences());
        assertEquals(appConfig.getConfigReferences(), deser.getConfigReferences());
        assertEquals(appConfig.getConfigElements(), deser.getConfigElements());
        assertEquals(appConfig.getCreatedOn(), deser.getCreatedOn());
        assertEquals(appConfig.getModifiedOn(), deser.getModifiedOn());
        assertEquals(appConfig.getGuid(), deser.getGuid());
        assertEquals(appConfig.getVersion(), deser.getVersion());
        assertEquals(appConfig.isDeleted(), deser.isDeleted());
    }
}
