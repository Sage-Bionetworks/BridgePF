package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.sagebionetworks.bridge.models.OperatingSystem.ANDROID;

import java.util.HashSet;
import java.util.List;

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
    private static final DateTime SURVEY_PUB_DATE = DateTime.now(DateTimeZone.UTC);
    private static final HashSet<String> SET_B = Sets.newHashSet("c","d");
    private static final HashSet<String> SET_A = Sets.newHashSet("a","b");
    private static final List<SurveyReference> SURVEY_REFS = ImmutableList.of(
            new SurveyReference("surveyA", BridgeUtils.generateGuid(), SURVEY_PUB_DATE),
            new SurveyReference("surveyB", BridgeUtils.generateGuid(), SURVEY_PUB_DATE));
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
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(appConfig);
        
        assertEquals(12, node.size());
        JsonNode critNode = node.get("criteria");
        assertEquals("fr", critNode.get("language").textValue());
        assertEquals(2, critNode.get("allOfGroups").size());
        assertEquals("a", critNode.get("allOfGroups").get(0).textValue());
        assertEquals("b", critNode.get("allOfGroups").get(1).textValue());
        assertEquals(2, critNode.get("noneOfGroups").size());
        assertEquals("d", critNode.get("noneOfGroups").get(0).textValue());
        assertEquals("c", critNode.get("noneOfGroups").get(1).textValue());
        assertEquals(2, critNode.get("minAppVersions").get("iPhone OS").intValue());
        assertEquals(10, critNode.get("minAppVersions").get("Android").intValue());
        assertEquals(8, critNode.get("maxAppVersions").get("iPhone OS").intValue());
        assertEquals(15, critNode.get("maxAppVersions").get("Android").intValue());
        assertEquals("Criteria", critNode.get("type").textValue());
        
        assertTrue(node.get("deleted").booleanValue());
        assertEquals(TIMESTAMP.toString(), node.get("createdOn").textValue());
        assertEquals(TIMESTAMP.toString(), node.get("modifiedOn").textValue());
        assertEquals("AppConfig", node.get("type").textValue());
        assertEquals(LABEL, node.get("label").textValue());
        assertEquals(3L, node.get("version").longValue());
        assertEquals(clientData, node.get("clientData"));
        assertEquals(clientData, node.get("configElements").get("config1"));
        assertEquals(1, node.get("configElements").size());
        
        assertEquals(2, node.get("configReferences").size());
        assertEquals("config1", node.get("configReferences").get(0).get("id").textValue());
        assertEquals(1L, node.get("configReferences").get(0).get("revision").longValue());
        assertEquals("config2", node.get("configReferences").get(1).get("id").textValue());
        assertEquals(2L, node.get("configReferences").get(1).get("revision").longValue());
        
        assertEquals(2, node.get("schemaReferences").size());
        assertEquals("schemaA", node.get("schemaReferences").get(0).get("id").textValue());
        assertEquals(1L, node.get("schemaReferences").get(0).get("revision").longValue());
        assertEquals("schemaB", node.get("schemaReferences").get(1).get("id").textValue());
        assertEquals(2L, node.get("schemaReferences").get(1).get("revision").longValue());
        
        assertEquals(2, node.get("surveyReferences").size());
        assertEquals("surveyA", node.get("surveyReferences").get(0).get("identifier").textValue());
        assertEquals(appConfig.getSurveyReferences().get(0).getGuid(),
                node.get("surveyReferences").get(0).get("guid").textValue());
        assertEquals(SURVEY_PUB_DATE.toString(), node.get("surveyReferences").get(0).get("createdOn").textValue());
        assertEquals("surveyB", node.get("surveyReferences").get(1).get("identifier").textValue());
        assertEquals(appConfig.getSurveyReferences().get(1).getGuid(),
                node.get("surveyReferences").get(1).get("guid").textValue());
        assertEquals(SURVEY_PUB_DATE.toString(), node.get("surveyReferences").get(1).get("createdOn").textValue());
        
        AppConfig deser = BridgeObjectMapper.get().treeToValue(node, AppConfig.class);
        assertNull(deser.getStudyId());
        
        assertEquals(clientData, deser.getClientData());
        assertEquals(clientData, deser.getConfigElements().get("config1"));
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
