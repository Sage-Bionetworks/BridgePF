package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableList;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.schedules.CompoundActivityDefinition;
import org.sagebionetworks.bridge.models.schedules.SchemaReference;
import org.sagebionetworks.bridge.models.schedules.SurveyReference;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;

@ContextConfiguration("classpath:test-context.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class DynamoCompoundActivityDefinitionDaoDdbTest {
    private static final List<SchemaReference> SCHEMA_LIST = ImmutableList.of(new SchemaReference("test-schema",
            null));
    private static final List<SurveyReference> SURVEY_LIST = ImmutableList.of(new SurveyReference("test-survey",
            "test-survey-guid", null));

    @Autowired
    private DynamoCompoundActivityDefinitionDao dao;

    private String taskId;

    @Before
    public void setup() {
        taskId = TestUtils.randomName(this.getClass());
    }

    @After
    public void cleanup() {
        try {
            dao.deleteCompoundActivityDefinition(TestConstants.TEST_STUDY, taskId);
        } catch (RuntimeException ex) {
            // squelch errors
        }
    }

    @Test
    public void crud() {
        // Create a def with version 3 to test that create fills these in correctly
        DynamoCompoundActivityDefinition defToCreate = new DynamoCompoundActivityDefinition();
        defToCreate.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        defToCreate.setTaskId(taskId);
        defToCreate.setVersion(3L);
        defToCreate.setSchemaList(SCHEMA_LIST);
        defToCreate.setSurveyList(SURVEY_LIST);

        // create and validate
        DynamoCompoundActivityDefinition createdDef = (DynamoCompoundActivityDefinition)
                dao.createCompoundActivityDefinition(defToCreate);
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, createdDef.getStudyId());
        assertEquals(taskId, createdDef.getTaskId());
        assertEquals(1, createdDef.getVersion().longValue());
        assertEquals(SCHEMA_LIST, createdDef.getSchemaList());
        assertEquals(SURVEY_LIST, createdDef.getSurveyList());

        // get the created def and validate
        DynamoCompoundActivityDefinition gettedCreatedDef = (DynamoCompoundActivityDefinition)
                dao.getCompoundActivityDefinition(TestConstants.TEST_STUDY, taskId);
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, gettedCreatedDef.getStudyId());
        assertEquals(taskId, gettedCreatedDef.getTaskId());
        assertEquals(1, gettedCreatedDef.getVersion().longValue());
        assertEquals(SCHEMA_LIST, gettedCreatedDef.getSchemaList());
        assertEquals(SURVEY_LIST, gettedCreatedDef.getSurveyList());

        // Update def to have no surveys. Version 1 so we don't get concurrent modification.
        DynamoCompoundActivityDefinition defToUpdate = new DynamoCompoundActivityDefinition();
        defToUpdate.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        defToUpdate.setTaskId(taskId);
        defToUpdate.setVersion(1L);
        defToUpdate.setSchemaList(SCHEMA_LIST);
        defToUpdate.setSurveyList(ImmutableList.of());

        // update and validate
        DynamoCompoundActivityDefinition updatedDef = (DynamoCompoundActivityDefinition)
                dao.updateCompoundActivityDefinition(defToUpdate);
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, updatedDef.getStudyId());
        assertEquals(taskId, updatedDef.getTaskId());
        assertEquals(2, updatedDef.getVersion().longValue());
        assertEquals(SCHEMA_LIST, updatedDef.getSchemaList());
        assertTrue(updatedDef.getSurveyList().isEmpty());

        // get the updated def and validate
        DynamoCompoundActivityDefinition gettedUpdatedDef = (DynamoCompoundActivityDefinition)
                dao.getCompoundActivityDefinition(TestConstants.TEST_STUDY, taskId);
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, gettedUpdatedDef.getStudyId());
        assertEquals(taskId, gettedUpdatedDef.getTaskId());
        assertEquals(2, gettedUpdatedDef.getVersion().longValue());
        assertEquals(SCHEMA_LIST, gettedUpdatedDef.getSchemaList());
        assertTrue(gettedUpdatedDef.getSurveyList().isEmpty());

        // Create a second def to test list.
        String taskId2 = taskId + "2";
        DynamoCompoundActivityDefinition defToCreate2 = new DynamoCompoundActivityDefinition();
        defToCreate2.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        defToCreate2.setTaskId(taskId2);
        defToCreate2.setSchemaList(SCHEMA_LIST);
        defToCreate2.setSurveyList(SURVEY_LIST);
        dao.createCompoundActivityDefinition(defToCreate2);

        // Test list. Since there might be other defs from other tests, page through the defs to find the ones
        // corresponding to the test.
        boolean foundDef1 = false;
        boolean foundDef2 = false;
        List<CompoundActivityDefinition> defList = dao.getAllCompoundActivityDefinitionsInStudy(
                TestConstants.TEST_STUDY);
        for (CompoundActivityDefinition oneDef : defList) {
            DynamoCompoundActivityDefinition oneDdbDef = (DynamoCompoundActivityDefinition) oneDef;
            String listedTaskId = oneDdbDef.getTaskId();
            if (taskId.equals(listedTaskId)) {
                assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, oneDdbDef.getStudyId());
                assertEquals(2, oneDdbDef.getVersion().longValue());
                assertEquals(SCHEMA_LIST, oneDdbDef.getSchemaList());
                assertTrue(oneDdbDef.getSurveyList().isEmpty());
                foundDef1 = true;
            } else if (taskId2.equals(listedTaskId)) {
                assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, oneDdbDef.getStudyId());
                assertEquals(1, oneDdbDef.getVersion().longValue());
                assertEquals(SCHEMA_LIST, oneDdbDef.getSchemaList());
                assertEquals(SURVEY_LIST, oneDdbDef.getSurveyList());
                foundDef2 = true;
            }
        }
        assertTrue(foundDef1);
        assertTrue(foundDef2);

        // delete def 2
        dao.deleteCompoundActivityDefinition(TestConstants.TEST_STUDY, taskId2);

        // get def 2 should throw
        try {
            dao.getCompoundActivityDefinition(TestConstants.TEST_STUDY, taskId2);
            fail("expected exception");
        } catch (EntityNotFoundException ex) {
            // expected exception
        }

        // list should not include def 2
        List<CompoundActivityDefinition> defListWithoutDef2 = dao.getAllCompoundActivityDefinitionsInStudy(
                TestConstants.TEST_STUDY);
        //noinspection Convert2streamapi
        for (CompoundActivityDefinition oneDef : defListWithoutDef2) {
            if (taskId2.equals(oneDef.getTaskId())) {
                fail("Found def with task ID " + taskId2 + " when it should have been deleted.");
            }
        }
    }

    @Test
    public void deleteAll() {
        // Set up test: Create 1 def in TEST_STUDY and 2 defs in a new study. This is to test we can delete all defs in
        // a study, but not affect other studies.
        DynamoCompoundActivityDefinition originalStudyDef = new DynamoCompoundActivityDefinition();
        originalStudyDef.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        originalStudyDef.setTaskId(taskId);
        dao.createCompoundActivityDefinition(originalStudyDef);

        String otherStudyIdStr = TestUtils.randomName(this.getClass());
        StudyIdentifier otherStudyId = new StudyIdentifierImpl(otherStudyIdStr);

        String otherStudyTaskId1 = "other-study-" + taskId + "1";
        DynamoCompoundActivityDefinition otherStudyDef1 = new DynamoCompoundActivityDefinition();
        otherStudyDef1.setStudyId(otherStudyIdStr);
        otherStudyDef1.setTaskId(otherStudyTaskId1);
        dao.createCompoundActivityDefinition(otherStudyDef1);

        String otherStudyTaskId2 = "other-study-" + taskId + "2";
        DynamoCompoundActivityDefinition otherStudyDef2 = new DynamoCompoundActivityDefinition();
        otherStudyDef2.setStudyId(otherStudyIdStr);
        otherStudyDef2.setTaskId(otherStudyTaskId2);
        dao.createCompoundActivityDefinition(otherStudyDef2);

        // verify the 2 defs in other study
        List<CompoundActivityDefinition> otherStudyDefList = dao.getAllCompoundActivityDefinitionsInStudy(
                otherStudyId);
        assertEquals(2, otherStudyDefList.size());
        Set<String> otherStudyTaskIdSet = otherStudyDefList.stream().map(CompoundActivityDefinition::getTaskId)
                .collect(Collectors.toSet());
        assertEquals(2, otherStudyTaskIdSet.size());
        assertTrue(otherStudyTaskIdSet.contains(otherStudyTaskId1));
        assertTrue(otherStudyTaskIdSet.contains(otherStudyTaskId2));

        // delete all defs in other study
        dao.deleteAllCompoundActivityDefinitionsInStudy(otherStudyId);

        // verify no defs in other study
        List<CompoundActivityDefinition> otherStudyDefList2 = dao.getAllCompoundActivityDefinitionsInStudy(
                otherStudyId);
        assertEquals(0, otherStudyDefList2.size());

        // originalStudyDef is untouched
        CompoundActivityDefinition gettedOriginalStudyDef = dao.getCompoundActivityDefinition(TestConstants.TEST_STUDY,
                taskId);
        assertNotNull(gettedOriginalStudyDef);

        // cleanup() will take care of cleaning up originalStudyDef
    }

    @Test(expected = ConcurrentModificationException.class)
    public void createConcurrentModification() {
        // create def
        DynamoCompoundActivityDefinition def = new DynamoCompoundActivityDefinition();
        def.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        def.setTaskId(taskId);
        def.setSchemaList(SCHEMA_LIST);
        def.setSurveyList(SURVEY_LIST);
        dao.createCompoundActivityDefinition(def);

        // create it again, will throw
        dao.createCompoundActivityDefinition(def);
    }

    @Test(expected = EntityNotFoundException.class)
    public void deleteNonExistentDef() {
        dao.deleteCompoundActivityDefinition(TestConstants.TEST_STUDY, taskId);
    }

    @Test(expected = EntityNotFoundException.class)
    public void getNonExistentDef() {
        dao.getCompoundActivityDefinition(TestConstants.TEST_STUDY, taskId);
    }

    @Test(expected = EntityNotFoundException.class)
    public void updateNonExistentDef() {
        // dummy def, to make sure we aren't throwing for a reason other than non-existent def
        DynamoCompoundActivityDefinition def = new DynamoCompoundActivityDefinition();
        def.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        def.setTaskId(taskId);
        def.setSchemaList(SCHEMA_LIST);
        def.setSurveyList(SURVEY_LIST);

        // update non-existent def, will throw
        dao.updateCompoundActivityDefinition(def);
    }

    @Test(expected = ConcurrentModificationException.class)
    public void updateConcurrentModification() {
        // create def
        DynamoCompoundActivityDefinition def = new DynamoCompoundActivityDefinition();
        def.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        def.setTaskId(taskId);
        def.setSchemaList(SCHEMA_LIST);
        def.setSurveyList(SURVEY_LIST);
        dao.createCompoundActivityDefinition(def);

        // update def to bump to version 2
        DynamoCompoundActivityDefinition goodUpdate = new DynamoCompoundActivityDefinition();
        goodUpdate.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        goodUpdate.setTaskId(taskId);
        goodUpdate.setSchemaList(SCHEMA_LIST);
        goodUpdate.setSurveyList(SURVEY_LIST);
        goodUpdate.setVersion(1L);
        dao.updateCompoundActivityDefinition(goodUpdate);

        // attempt to update version 1 again, this will throw - Note we need to create a new def instance object
        // because DDB Mapper modifies arguments
        DynamoCompoundActivityDefinition conflictUpdate = new DynamoCompoundActivityDefinition();
        conflictUpdate.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        conflictUpdate.setTaskId(taskId);
        conflictUpdate.setSchemaList(SCHEMA_LIST);
        conflictUpdate.setSurveyList(SURVEY_LIST);
        conflictUpdate.setVersion(1L);
        dao.updateCompoundActivityDefinition(conflictUpdate);
    }
}
