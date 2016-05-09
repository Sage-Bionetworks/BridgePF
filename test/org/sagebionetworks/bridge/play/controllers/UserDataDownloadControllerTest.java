package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import play.mvc.Result;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.models.DateRange;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.services.UserDataDownloadService;

public class UserDataDownloadControllerTest {
    @Test
    public void test() throws Exception {
        // test strategy is to make sure args get pulled from the controller context and passed into the inner service

        // mock session
        StudyIdentifier studyIdentifier = new StudyIdentifierImpl("test-study");

        UserSession mockSession = new UserSession(null);
        mockSession.setStudyIdentifier(studyIdentifier);

        // mock request JSON
        String dateRangeJsonText = "{\n" +
                "   \"startDate\":\"2015-08-15\",\n" +
                "   \"endDate\":\"2015-08-19\"\n" +
                "}";
        TestUtils.mockPlayContextWithJson(dateRangeJsonText);

        // mock service
        UserDataDownloadService mockService = mock(UserDataDownloadService.class);

        // spy controller
        UserDataDownloadController controller = spy(new UserDataDownloadController());
        controller.setUserDataDownloadService(mockService);
        doReturn(mockSession).when(controller).getAuthenticatedAndConsentedSession();

        // execute and validate
        Result result = controller.requestUserData();
        assertEquals(202, result.status());

        // validate args sent to mock service
        ArgumentCaptor<DateRange> dateRangeCaptor = ArgumentCaptor.forClass(DateRange.class);
        verify(mockService).requestUserData(eq(studyIdentifier), any(User.class), dateRangeCaptor.capture());

        DateRange dateRange = dateRangeCaptor.getValue();
        assertEquals("2015-08-15", dateRange.getStartDate().toString());
        assertEquals("2015-08-19", dateRange.getEndDate().toString());
    }
}
