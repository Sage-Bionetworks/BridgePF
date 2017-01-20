package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;

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
        // Create a def with no studyId and version 3 to test that create fills these in correctly
        DynamoCompoundActivityDefinition defToCreate = new DynamoCompoundActivityDefinition();
        defToCreate.setStudyId(null);
        defToCreate.setTaskId(taskId);
        defToCreate.setVersion(3L);
        defToCreate.setSchemaList(SCHEMA_LIST);
        defToCreate.setSurveyList(SURVEY_LIST);

        // create and validate
        DynamoCompoundActivityDefinition createdDef = (DynamoCompoundActivityDefinition)
                dao.createCompoundActivityDefinition(TestConstants.TEST_STUDY, defToCreate);
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

        // Update def to have no surveys. Version 1 so we don't get concurrent modification. Also, use nonsense strings
        // for Study ID and Task ID to make sure the DAO is correctly ignoring those.
        DynamoCompoundActivityDefinition defToUpdate = new DynamoCompoundActivityDefinition();
        defToUpdate.setStudyId("ignored-study");
        defToUpdate.setTaskId("ignored-task");
        defToUpdate.setVersion(1L);
        defToUpdate.setSchemaList(SCHEMA_LIST);
        defToUpdate.setSurveyList(ImmutableList.of());

        // update and validate
        DynamoCompoundActivityDefinition updatedDef = (DynamoCompoundActivityDefinition)
                dao.updateCompoundActivityDefinition(TestConstants.TEST_STUDY, taskId, defToUpdate);
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
        defToCreate2.setTaskId(taskId2);
        defToCreate2.setSchemaList(SCHEMA_LIST);
        defToCreate2.setSurveyList(SURVEY_LIST);
        dao.createCompoundActivityDefinition(TestConstants.TEST_STUDY, defToCreate2);

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

    @Test(expected = ConcurrentModificationException.class)
    public void createConcurrentModification() {
        // create def
        DynamoCompoundActivityDefinition def = new DynamoCompoundActivityDefinition();
        def.setTaskId(taskId);
        def.setSchemaList(SCHEMA_LIST);
        def.setSurveyList(SURVEY_LIST);
        dao.createCompoundActivityDefinition(TestConstants.TEST_STUDY, def);

        // create it again, will throw
        dao.createCompoundActivityDefinition(TestConstants.TEST_STUDY, def);
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
        def.setTaskId(taskId);
        def.setSchemaList(SCHEMA_LIST);
        def.setSurveyList(SURVEY_LIST);

        // update non-existent def, will throw
        dao.updateCompoundActivityDefinition(TestConstants.TEST_STUDY, taskId, def);
    }

    @Test(expected = ConcurrentModificationException.class)
    public void updateConcurrentModification() {
        // create def
        DynamoCompoundActivityDefinition def = new DynamoCompoundActivityDefinition();
        def.setTaskId(taskId);
        def.setSchemaList(SCHEMA_LIST);
        def.setSurveyList(SURVEY_LIST);
        dao.createCompoundActivityDefinition(TestConstants.TEST_STUDY, def);

        // update def to bump to version 2
        DynamoCompoundActivityDefinition goodUpdate = new DynamoCompoundActivityDefinition();
        goodUpdate.setTaskId(taskId);
        goodUpdate.setSchemaList(SCHEMA_LIST);
        goodUpdate.setSurveyList(SURVEY_LIST);
        goodUpdate.setVersion(1L);
        dao.updateCompoundActivityDefinition(TestConstants.TEST_STUDY, taskId, goodUpdate);

        // attempt to update version 1 again, this will throw - Note we need to create a new def instance object
        // because DDB Mapper modifies arguments
        DynamoCompoundActivityDefinition conflictUpdate = new DynamoCompoundActivityDefinition();
        conflictUpdate.setTaskId(taskId);
        conflictUpdate.setSchemaList(SCHEMA_LIST);
        conflictUpdate.setSurveyList(SURVEY_LIST);
        conflictUpdate.setVersion(1L);
        dao.updateCompoundActivityDefinition(TestConstants.TEST_STUDY, taskId, conflictUpdate);
    }
}
