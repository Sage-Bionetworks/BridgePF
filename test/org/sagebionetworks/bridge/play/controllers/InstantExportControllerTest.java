package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import play.mvc.Result;

import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.services.InstantExportService;

@RunWith(MockitoJUnitRunner.class)
public class InstantExportControllerTest {
    private StudyIdentifier studyIdentifier;
    private InstantExportController controller;

    @Mock
    private InstantExportService mockService;

    @Before
    public void before() throws Exception {
        studyIdentifier = new StudyIdentifierImpl("test-study");
        UserSession userSession = new UserSession();
        userSession.setAuthenticated(true);
        userSession.setStudyIdentifier(studyIdentifier);
        controller = spy(new InstantExportController());
        controller.setInstantExportService(mockService);
        doReturn(userSession).when(controller).getAuthenticatedSession(DEVELOPER, RESEARCHER);
    }

    @Test
    public void test() throws Exception {
        // execute and validate
        Result result = controller.requestInstantExport();
        assertEquals(202, result.status());

        verify(mockService).export(eq(studyIdentifier));
    }
}
