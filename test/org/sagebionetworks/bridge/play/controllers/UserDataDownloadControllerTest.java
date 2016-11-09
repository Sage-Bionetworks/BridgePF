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
import org.sagebionetworks.bridge.models.DateRange;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.services.UserDataDownloadService;

@RunWith(MockitoJUnitRunner.class)
public class UserDataDownloadControllerTest {
    
    private StudyIdentifier studyIdentifier = new StudyIdentifierImpl("test-study");
    
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
        doReturn(studyIdentifier).when(mockSession).getStudyIdentifier();
        doReturn(new StudyParticipant.Builder().withEmail("email@email.com").build()).when(mockSession).getParticipant();

        controller.setUserDataDownloadService(mockService);
        doReturn(mockSession).when(controller).getAuthenticatedAndConsentedSession();
    }
    
    @Test
    public void test() throws Exception {
        // test strategy is to make sure args get pulled from the controller context and passed into the inner service
        // mock request JSON
        String dateRangeJsonText = "{\n" +
                "   \"startDate\":\"2015-08-15\",\n" +
                "   \"endDate\":\"2015-08-19\"\n" +
                "}";
        TestUtils.mockPlayContextWithJson(dateRangeJsonText);

        // execute and validate
        Result result = controller.requestUserData(null, null);
        assertEquals(202, result.status());

        verify(mockService).requestUserData(eq(studyIdentifier), eq("email@email.com"), dateRangeCaptor.capture());
        
        // validate args sent to mock service
        DateRange dateRange = dateRangeCaptor.getValue();
        assertEquals("2015-08-15", dateRange.getStartDate().toString());
        assertEquals("2015-08-19", dateRange.getEndDate().toString());
    }
    
    @Test
    public void testQueryParameters() throws Exception {
        Result result = controller.requestUserData("2015-08-15", "2015-08-19");
        assertEquals(202, result.status());

        verify(mockService).requestUserData(eq(studyIdentifier), eq("email@email.com"), dateRangeCaptor.capture());
        
        // validate args sent to mock service
        DateRange dateRange = dateRangeCaptor.getValue();
        assertEquals("2015-08-15", dateRange.getStartDate().toString());
        assertEquals("2015-08-19", dateRange.getEndDate().toString());
    }
    
}
