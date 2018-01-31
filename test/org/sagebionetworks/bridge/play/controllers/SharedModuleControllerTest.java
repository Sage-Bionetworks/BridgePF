package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Before;
import org.junit.Test;
import play.mvc.Result;
import play.test.Helpers;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.sharedmodules.SharedModuleImportStatus;
import org.sagebionetworks.bridge.services.SharedModuleService;

public class SharedModuleControllerTest {
    private static final String MODULE_ID = "test-module";
    private static final int MODULE_VERSION = 3;
    private static final String SCHEMA_ID = "test-schema";
    private static final int SCHEMA_REV = 7;

    private SharedModuleController controller;
    private SharedModuleService mockSvc;

    @Before
    public void before() {
        // mock service and controller
        mockSvc = mock(SharedModuleService.class);
        controller = spy(new SharedModuleController());
        controller.setModuleService(mockSvc);

        // mock session
        UserSession mockSession = new UserSession();
        mockSession.setStudyIdentifier(TestConstants.TEST_STUDY);
        doReturn(mockSession).when(controller).getAuthenticatedSession(Roles.DEVELOPER);
    }

    @Test
    public void byIdAndVersion() throws Exception {
        // mock service
        when(mockSvc.importModuleByIdAndVersion(TestConstants.TEST_STUDY, MODULE_ID, MODULE_VERSION)).thenReturn(
                new SharedModuleImportStatus(SCHEMA_ID, SCHEMA_REV));

        // setup, execute, and validate
        Result result = controller.importModuleByIdAndVersion(MODULE_ID, MODULE_VERSION);
        assertStatus(result);

        // validate permissions
        verify(controller).getAuthenticatedSession(Roles.DEVELOPER);
    }

    @Test
    public void latestPublished() throws Exception {
        // mock service
        when(mockSvc.importModuleByIdLatestPublishedVersion(TestConstants.TEST_STUDY, MODULE_ID)).thenReturn(
                new SharedModuleImportStatus(SCHEMA_ID, SCHEMA_REV));

        // setup, execute, and validate
        Result result = controller.importModuleByIdLatestPublishedVersion(MODULE_ID);
        assertStatus(result);

        // validate permissions
        verify(controller).getAuthenticatedSession(Roles.DEVELOPER);
    }

    private static void assertStatus(Result result) throws Exception {
        TestUtils.assertResult(result, 200);
        String jsonText = Helpers.contentAsString(result);
        JsonNode jsonNode = BridgeObjectMapper.get().readTree(jsonText);
        assertEquals("schema", jsonNode.get("moduleType").textValue());
        assertEquals(SCHEMA_ID, jsonNode.get("schemaId").textValue());
        assertEquals(SCHEMA_REV, jsonNode.get("schemaRevision").intValue());
    }
}
