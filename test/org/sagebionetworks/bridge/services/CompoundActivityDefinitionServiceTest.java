package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.CompoundActivityDefinitionDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.ConstraintViolationException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.CompoundActivity;
import org.sagebionetworks.bridge.models.schedules.CompoundActivityDefinition;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.SchemaReference;
import org.sagebionetworks.bridge.models.schedules.SurveyReference;

public class CompoundActivityDefinitionServiceTest {
    private static final List<SchemaReference> SCHEMA_LIST = ImmutableList.of(new SchemaReference("test-schema",
            null));
    private static final List<SurveyReference> SURVEY_LIST = ImmutableList.of(new SurveyReference("test-survey",
            "test-survey-guid", null));
    private static final String TASK_ID = "test-task";

    private SchedulePlanService schedulePlanService;
    private CompoundActivityDefinitionDao dao;
    private CompoundActivityDefinitionService service;

    @Before
    public void setup() {
        dao = mock(CompoundActivityDefinitionDao.class);
        schedulePlanService = mock(SchedulePlanService.class);
        service = new CompoundActivityDefinitionService();
        service.setSchedulePlanService(schedulePlanService);
        service.setCompoundActivityDefDao(dao);
    }

    // CREATE

    @Test
    public void create() {
        // mock dao
        CompoundActivityDefinition daoResult = makeValidDef();
        ArgumentCaptor<CompoundActivityDefinition> daoInputCaptor = ArgumentCaptor.forClass(
                CompoundActivityDefinition.class);
        when(dao.createCompoundActivityDefinition(daoInputCaptor.capture())).thenReturn(daoResult);

        // execute
        CompoundActivityDefinition serviceInput = makeValidDef();
        CompoundActivityDefinition serviceResult = service.createCompoundActivityDefinition(TestConstants.TEST_STUDY,
                serviceInput);

        // validate dao input - It's the same as the service input, but we also set the study ID.
        CompoundActivityDefinition daoInput = daoInputCaptor.getValue();
        assertSame(serviceInput, daoInput);
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, daoInput.getStudyId());

        // Validate that the service result is the same as the dao result.
        assertSame(daoResult, serviceResult);
    }

    @Test
    public void createInvalidDef() {
        // make invalid def by having it have no task ID
        CompoundActivityDefinition def = makeValidDef();
        def.setTaskId(null);

        // execute, will throw
        try {
            service.createCompoundActivityDefinition(TestConstants.TEST_STUDY, def);
            fail("expected exception");
        } catch (InvalidEntityException ex) {
            // expected exception
        }

        // verify dao is never called
        verifyZeroInteractions(dao);
    }

    // DELETE

    @Test
    public void delete() {
        // note: delete has no return value

        // execute
        dao.deleteCompoundActivityDefinition(TestConstants.TEST_STUDY, TASK_ID);

        // verify dao
        verify(dao).deleteCompoundActivityDefinition(TestConstants.TEST_STUDY, TASK_ID);
    }

    @Test
    public void deleteNullTaskId() {
        deleteBadRequest(null);
    }

    @Test
    public void deleteEmptyTaskId() {
        deleteBadRequest("");
    }

    @Test
    public void deleteBlankTaskId() {
        deleteBadRequest("   ");
    }
    
    @Test
    public void deleteWithConstraintViolation() {
        SchedulePlan plan = TestUtils.getSimpleSchedulePlan(TestConstants.TEST_STUDY);
        CompoundActivity compoundActivity = new CompoundActivity.Builder()
                .withTaskIdentifier(TASK_ID).build();
        Activity newActivity = new Activity.Builder()
                .withCompoundActivity(compoundActivity).build();
        plan.getStrategy().getAllPossibleSchedules().get(0).getActivities().set(0, newActivity);
        when(schedulePlanService.getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, TestConstants.TEST_STUDY, true))
                .thenReturn(Lists.newArrayList(plan));
        
        // Now, a schedule plan exists that references this task ID. It cannot be deleted.
        try {
            service.deleteCompoundActivityDefinition(TestConstants.TEST_STUDY, TASK_ID);
            fail("Shoud have thrown exception");
        } catch(ConstraintViolationException e) {
            assertEquals("GGG", e.getReferrerKeys().get("guid"));
            assertEquals("SchedulePlan", e.getReferrerKeys().get("type"));
            assertEquals(TASK_ID, e.getEntityKeys().get("taskId"));
            assertEquals("CompoundActivityDefinition", e.getEntityKeys().get("type"));
        }
    }

    private void deleteBadRequest(String taskId) {
        // execute, will throw
        try {
            service.deleteCompoundActivityDefinition(TestConstants.TEST_STUDY, taskId);
            fail("expected exception");
        } catch (BadRequestException ex) {
            assertEquals("taskId must be specified", ex.getMessage());
        }

        // verify dao is never called
        verifyZeroInteractions(dao);
    }

    // DELETE ALL

    @Test
    public void deleteAll() {
        // execute
        service.deleteAllCompoundActivityDefinitionsInStudy(TestConstants.TEST_STUDY);

        // verify dao
        verify(dao).deleteAllCompoundActivityDefinitionsInStudy(TestConstants.TEST_STUDY);
    }

    // LIST

    @Test
    public void list() {
        // mock dao
        List<CompoundActivityDefinition> daoResultList = ImmutableList.of(makeValidDef());
        when(dao.getAllCompoundActivityDefinitionsInStudy(TestConstants.TEST_STUDY)).thenReturn(daoResultList);

        // execute
        List<CompoundActivityDefinition> serviceResultList = service.getAllCompoundActivityDefinitionsInStudy(
                TestConstants.TEST_STUDY);

        // Validate that the service result is the same as the dao result.
        assertSame(daoResultList, serviceResultList);
    }

    // GET

    @Test
    public void get() {
        // mock dao
        CompoundActivityDefinition daoResult = makeValidDef();
        when(dao.getCompoundActivityDefinition(TestConstants.TEST_STUDY, TASK_ID)).thenReturn(daoResult);

        // execute
        CompoundActivityDefinition serviceResult = service.getCompoundActivityDefinition(TestConstants.TEST_STUDY,
                TASK_ID);

        // Validate that the service result is the same as the dao result.
        assertSame(daoResult, serviceResult);
    }

    @Test
    public void getNullTaskId() {
        getBadRequest(null);
    }

    @Test
    public void getEmptyTaskId() {
        getBadRequest("");
    }

    @Test
    public void getBlankTaskId() {
        getBadRequest("   ");
    }

    private void getBadRequest(String taskId) {
        // execute, will throw
        try {
            service.getCompoundActivityDefinition(TestConstants.TEST_STUDY, taskId);
            fail("expected exception");
        } catch (BadRequestException ex) {
            assertEquals("taskId must be specified", ex.getMessage());
        }

        // verify dao is never called
        verifyZeroInteractions(dao);
    }

    // UPDATE

    @Test
    public void update() {
        // mock dao
        CompoundActivityDefinition daoResult = makeValidDef();
        ArgumentCaptor<CompoundActivityDefinition> daoInputCaptor = ArgumentCaptor.forClass(
                CompoundActivityDefinition.class);
        when(dao.updateCompoundActivityDefinition(daoInputCaptor.capture())).thenReturn(daoResult);

        // execute
        CompoundActivityDefinition serviceInput = makeValidDef();
        CompoundActivityDefinition serviceResult = service.updateCompoundActivityDefinition(TestConstants.TEST_STUDY,
                TASK_ID, serviceInput);

        // validate dao input - It's the same as the service input, but we also set the study ID.
        CompoundActivityDefinition daoInput = daoInputCaptor.getValue();
        assertSame(serviceInput, daoInput);
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, daoInput.getStudyId());
        assertEquals(TASK_ID, daoInput.getTaskId());

        // Validate that the service result is the same as the dao result.
        assertSame(daoResult, serviceResult);
    }

    @Test
    public void updateInvalidDef() {
        // make invalid def by having it have no schemas or surveys.
        CompoundActivityDefinition def = makeValidDef();
        def.setSchemaList(ImmutableList.of());
        def.setSurveyList(ImmutableList.of());

        // execute, will throw
        try {
            service.updateCompoundActivityDefinition(TestConstants.TEST_STUDY, TASK_ID, def);
            fail("expected exception");
        } catch (InvalidEntityException ex) {
            // expected exception
        }

        // verify dao is never called
        verifyZeroInteractions(dao);
    }

    @Test
    public void updateNullTaskId() {
        updateBadRequest(null);
    }

    @Test
    public void updateEmptyTaskId() {
        updateBadRequest("");
    }

    @Test
    public void updateBlankTaskId() {
        updateBadRequest("   ");
    }

    private void updateBadRequest(String taskId) {
        // execute, will throw
        try {
            service.updateCompoundActivityDefinition(TestConstants.TEST_STUDY, taskId, makeValidDef());
            fail("expected exception");
        } catch (BadRequestException ex) {
            assertEquals("taskId must be specified", ex.getMessage());
        }

        // verify dao is never called
        verifyZeroInteractions(dao);
    }

    private static CompoundActivityDefinition makeValidDef() {
        CompoundActivityDefinition def = CompoundActivityDefinition.create();
        def.setTaskId(TASK_ID);
        def.setSchemaList(SCHEMA_LIST);
        def.setSurveyList(SURVEY_LIST);
        return def;
    }
}
