package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.sagebionetworks.bridge.models.OperatingSystem.ANDROID;

import java.util.HashSet;
import java.util.List;

import javax.annotation.Resource;

import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoAppConfigDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.appconfig.AppConfig;
import org.sagebionetworks.bridge.models.schedules.SchemaReference;
import org.sagebionetworks.bridge.models.schedules.SurveyReference;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoAppConfigDaoTest {
    
    private static final HashSet<String> SET_B = Sets.newHashSet("c","d");
    private static final HashSet<String> SET_A = Sets.newHashSet("a","b");
    private static final List<SurveyReference> SURVEY_REFS = new ImmutableList.Builder<SurveyReference>()
            .add(new SurveyReference("surveyA", BridgeUtils.generateGuid(), DateTime.now()))
            .add(new SurveyReference("surveyB", BridgeUtils.generateGuid(), DateTime.now())).build();
    private static final List<SchemaReference> SCHEMA_REFS = new ImmutableList.Builder<SchemaReference>()
            .add(new SchemaReference("schemaA", 1))
            .add(new SchemaReference("schemaB", 2)).build();
    
    private static final StudyIdentifier STUDY_ID = new StudyIdentifierImpl(TestUtils.randomName(DynamoAppConfigDaoTest.class));
    
    @Resource
    DynamoAppConfigDao dao;
    
    @After
    public void after() {
        dao.deleteAllAppConfigs(STUDY_ID);
        assertTrue(dao.getAppConfigs(STUDY_ID).isEmpty());
    }    
    
    @Test
    public void crudAppConfig() {
        Criteria criteria;
        criteria = TestUtils.createCriteria(2, 8, SET_A, SET_B);
        criteria.setMinAppVersion(ANDROID, 10);
        criteria.setMaxAppVersion(ANDROID, 15);
        criteria.setLanguage("fr");

        JsonNode clientData = TestUtils.getClientData();
        
        AppConfig config = AppConfig.create();
        config.setCriteria(criteria);
        config.setSurveyReferences(SURVEY_REFS);
        config.setSchemaReferences(SCHEMA_REFS);
        config.setClientData(clientData);
        config.setStudyId(STUDY_ID.getIdentifier());
        
        // save new app config
        AppConfig saved = dao.createAppConfig(config);
        assertNotNull(saved.getGuid());
        assertNotNull(saved.getVersion());
        assertEquals(SURVEY_REFS, saved.getSurveyReferences());
        assertEquals(SCHEMA_REFS, saved.getSchemaReferences());
        assertEquals(clientData.toString(), saved.getClientData().toString());
        assertEquals(criteria, saved.getCriteria());
        
        // update a changed version of the record
        String existingGuid = saved.getGuid();
        Long existingVersion = saved.getVersion();
        saved.setSchemaReferences(null);
        saved.setSurveyReferences(null);
        
        AppConfig updated = dao.updateAppConfig(saved);
        assertEquals(existingGuid, updated.getGuid());
        assertNotEquals(existingVersion, updated.getVersion());
        assertTrue(updated.getSurveyReferences().isEmpty());
        assertTrue(updated.getSchemaReferences().isEmpty());
        
        // make a copy.
        AppConfig copy = dao.createAppConfig(saved);
        assertNotEquals(saved.getGuid(), copy.getGuid());
        
        // retrieve list of 2 records
        List<AppConfig> lists = dao.getAppConfigs(STUDY_ID);
        assertEquals(2, lists.size());
        
        AppConfig first = dao.getAppConfig(STUDY_ID, saved.getGuid());
        assertNotNull(first);
        
        AppConfig second = dao.getAppConfig(STUDY_ID, copy.getGuid());
        assertNotNull(second);
        
        // delete one record
        dao.deleteAppConfig(STUDY_ID, updated.getGuid());
        try {
            dao.getAppConfig(STUDY_ID, updated.getGuid());
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
            
        }
        // deleteAll is used in test cleanup, and then verified
    }
    
}
