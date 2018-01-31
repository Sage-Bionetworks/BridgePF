package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

import play.mvc.Result;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.DateRange;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.services.UserDataDownloadService;

@RunWith(MockitoJUnitRunner.class)
public class UserDataDownloadControllerTest {
    private static final String START_DATE = "2015-08-15";
    private static final String END_DATE = "2015-08-19";
    private static final StudyIdentifier STUDY_ID = new StudyIdentifierImpl("test-study");
    private static final String USER_ID = "test-user-id";
    private static final String EMAIL = "email@email.com";

    @Mock
    UserSession mockSession;
    
    @Mock
    UserDataDownloadService mockService;
    
    @Spy
    UserDataDownloadController controller;
    
    @Captor
    ArgumentCaptor<DateRange> dateRangeCaptor;
    
    @Before
    public void before() throws Exception {
        controller.setUserDataDownloadService(mockService);
        doReturn(mockSession).when(controller).getAuthenticatedAndConsentedSession();
        doReturn("dummy-request-id").when(controller).getRequestId();
        doReturn(STUDY_ID).when(mockSession).getStudyIdentifier();
    }
    
    @Test
    public void test() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder().withId(USER_ID).withEmail(EMAIL)
                .withEmailVerified(Boolean.TRUE).build();
        doReturn(participant).when(mockSession).getParticipant();
        mockWithJson();

        // execute and validate
        Result result = controller.requestUserData(null, null);
        TestUtils.assertResult(result, 202);

        verify(mockService).requestUserData(eq(STUDY_ID), eq(USER_ID), dateRangeCaptor.capture());
        
        // validate args sent to mock service
        DateRange dateRange = dateRangeCaptor.getValue();
        assertEquals("2015-08-15", dateRange.getStartDate().toString());
        assertEquals("2015-08-19", dateRange.getEndDate().toString());
    }
    
    @Test
    public void testQueryParameters() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder().withId(USER_ID).withEmail(EMAIL)
                .withEmailVerified(Boolean.TRUE).build();
        doReturn(participant).when(mockSession).getParticipant();
        
        Result result = controller.requestUserData("2015-08-15", "2015-08-19");
        TestUtils.assertResult(result, 202);

        verify(mockService).requestUserData(eq(STUDY_ID), eq(USER_ID), dateRangeCaptor.capture());
        
        // validate args sent to mock service
        DateRange dateRange = dateRangeCaptor.getValue();
        assertEquals("2015-08-15", dateRange.getStartDate().toString());
        assertEquals("2015-08-19", dateRange.getEndDate().toString());
    }
    
    @Test(expected = BadRequestException.class)
    public void throwExceptionIfAccountHasNoEmail() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder().withId(USER_ID).build();
        doReturn(participant).when(mockSession).getParticipant();
        mockWithJson();

        controller.requestUserData(null, null);
    }
    
    @Test(expected = BadRequestException.class)
    public void throwExceptionIfAccountEmailUnverified() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder().withId(USER_ID)
                .withEmail(EMAIL).withEmailVerified(Boolean.FALSE).build();
        doReturn(participant).when(mockSession).getParticipant();
        mockWithJson();

        controller.requestUserData(null, null);
    }

    private void mockWithJson() throws Exception {
        // This isn't in the before(), because we don't want to mock the JSON body for the query params test.
        String dateRangeJsonText = "{\n" +
                "   \"startDate\":\"" + START_DATE + "\",\n" +
                "   \"endDate\":\"" + END_DATE + "\"\n" +
                "}";
        TestUtils.mockPlayContextWithJson(dateRangeJsonText);
    }
}
