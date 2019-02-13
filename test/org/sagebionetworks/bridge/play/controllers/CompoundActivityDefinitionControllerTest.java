package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import play.mvc.Result;
import play.test.Helpers;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.schedules.CompoundActivityDefinition;
import org.sagebionetworks.bridge.services.CompoundActivityDefinitionService;
import org.sagebionetworks.bridge.services.StudyService;

public class CompoundActivityDefinitionControllerTest {
    private static final String TASK_ID = "test-task";

    private CompoundActivityDefinitionController controller;
    private CompoundActivityDefinitionService defService;
    private StudyService studyService;

    @Before
    public void setup() {
        // mock session
        UserSession mockSession = new UserSession();
        mockSession.setStudyIdentifier(TestConstants.TEST_STUDY);

        // mock study service
        studyService = mock(StudyService.class);

        // mock def service
        defService = mock(CompoundActivityDefinitionService.class);

        // spy controller
        controller = spy(new CompoundActivityDefinitionController());
        doReturn(mockSession).when(controller).getAuthenticatedSession(any());
        controller.setCompoundActivityDefService(defService);
        controller.setStudyService(studyService);
    }

    @After
    public void verifyDeveloperPermissionsUsed() {
        verify(controller).getAuthenticatedSession(Roles.DEVELOPER);
    }

    @After
    public void verifyStudyServiceUnused() {
        verifyZeroInteractions(studyService);
    }

    @Test
    public void create() throws Exception{
        // make input def - Mark it with task ID for tracing. No other params matter.
        CompoundActivityDefinition controllerInput = CompoundActivityDefinition.create();
        controllerInput.setTaskId(TASK_ID);

        // Set it as the mock JSON input.
        TestUtils.mockPlay().withBody(controllerInput).mock();

        // mock service - Service output should have both task ID and study ID so we can test that study ID is filtered
        // out
        CompoundActivityDefinition serviceOutput = CompoundActivityDefinition.create();
        serviceOutput.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        serviceOutput.setTaskId(TASK_ID);

        ArgumentCaptor<CompoundActivityDefinition> serviceInputCaptor = ArgumentCaptor.forClass(
                CompoundActivityDefinition.class);
        when(defService.createCompoundActivityDefinition(eq(TestConstants.TEST_STUDY), serviceInputCaptor.capture()))
                .thenReturn(serviceOutput);

        // execute and validate
        Result result = controller.createCompoundActivityDefinition();
        TestUtils.assertResult(result, 201);
        CompoundActivityDefinition controllerOutput = getDefFromResult(result);
        assertEquals(TASK_ID, controllerOutput.getTaskId());
        assertNull(controllerOutput.getStudyId());

        // validate service input
        CompoundActivityDefinition serviceInput = serviceInputCaptor.getValue();
        assertEquals(TASK_ID, serviceInput.getTaskId());
    }

    @Test
    public void delete() throws Exception {
        // execute and validate
        Result result = controller.deleteCompoundActivityDefinition(TASK_ID);
        TestUtils.assertResult(result, 200);
        String resultJsonText = Helpers.contentAsString(result);
        JsonNode resultJsonNode = BridgeObjectMapper.get().readTree(resultJsonText);
        assertEquals("Compound activity definition has been deleted.", resultJsonNode.get("message").textValue());

        // verify call through to the service
        verify(defService).deleteCompoundActivityDefinition(TestConstants.TEST_STUDY, TASK_ID);
    }

    @Test
    public void list() throws Exception {
        // mock service
        CompoundActivityDefinition serviceOutput = CompoundActivityDefinition.create();
        serviceOutput.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        serviceOutput.setTaskId(TASK_ID);

        when(defService.getAllCompoundActivityDefinitionsInStudy(TestConstants.TEST_STUDY)).thenReturn(
                ImmutableList.of(serviceOutput));

        // execute and validate
        Result result = controller.getAllCompoundActivityDefinitionsInStudy();
        TestUtils.assertResult(result, 200);

        String controllerOutputJsonText = Helpers.contentAsString(result);
        ResourceList<CompoundActivityDefinition> controllerOutputResourceList = BridgeObjectMapper.get().readValue(
                controllerOutputJsonText, new TypeReference<ResourceList<CompoundActivityDefinition>>() {});
        List<CompoundActivityDefinition> controllerOutputList = controllerOutputResourceList.getItems();
        assertEquals(1, controllerOutputList.size());

        CompoundActivityDefinition controllerOutput = controllerOutputList.get(0);
        assertEquals(TASK_ID, controllerOutput.getTaskId());
        assertNull(controllerOutput.getStudyId());
    }

    @Test
    public void get() throws Exception {
        // mock service
        CompoundActivityDefinition serviceOutput = CompoundActivityDefinition.create();
        serviceOutput.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        serviceOutput.setTaskId(TASK_ID);

        when(defService.getCompoundActivityDefinition(TestConstants.TEST_STUDY, TASK_ID)).thenReturn(serviceOutput);

        // execute and validate
        Result result = controller.getCompoundActivityDefinition(TASK_ID);
        TestUtils.assertResult(result, 200);
        CompoundActivityDefinition controllerOutput = getDefFromResult(result);
        assertEquals(TASK_ID, controllerOutput.getTaskId());
        assertNull(controllerOutput.getStudyId());
    }

    @Test
    public void update() throws Exception {
        // make input def
        CompoundActivityDefinition controllerInput = CompoundActivityDefinition.create();
        controllerInput.setTaskId(TASK_ID);

        // Set it as the mock JSON input.
        TestUtils.mockPlay().withBody(controllerInput).mock();

        // mock service
        CompoundActivityDefinition serviceOutput = CompoundActivityDefinition.create();
        serviceOutput.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        serviceOutput.setTaskId(TASK_ID);

        ArgumentCaptor<CompoundActivityDefinition> serviceInputCaptor = ArgumentCaptor.forClass(
                CompoundActivityDefinition.class);
        when(defService.updateCompoundActivityDefinition(eq(TestConstants.TEST_STUDY), eq(TASK_ID),
                serviceInputCaptor.capture())).thenReturn(serviceOutput);

        // execute and validate
        Result result = controller.updateCompoundActivityDefinition(TASK_ID);
        TestUtils.assertResult(result, 200);
        CompoundActivityDefinition controllerOutput = getDefFromResult(result);
        assertEquals(TASK_ID, controllerOutput.getTaskId());
        assertNull(controllerOutput.getStudyId());

        // validate service input
        CompoundActivityDefinition serviceInput = serviceInputCaptor.getValue();
        assertEquals(TASK_ID, serviceInput.getTaskId());
    }

    private static CompoundActivityDefinition getDefFromResult(Result result) throws Exception {
        String jsonText = Helpers.contentAsString(result);
        CompoundActivityDefinition def = BridgeObjectMapper.get().readValue(jsonText,
                CompoundActivityDefinition.class);
        return def;
    }
}
