package org.sagebionetworks.bridge.play.controllers;

import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import play.mvc.Result;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.ActivityEventService;
import org.sagebionetworks.bridge.services.StudyService;

public class ActivityEventControllerTest {
    private static final Study DUMMY_STUDY = Study.create();
    private static final String HEALTH_CODE = "my-health-code";
    private static final String EVENT_KEY = "my-event";
    private static final String EVENT_TIMESTAMP_STRING = "2018-04-04T16:43:11.357-0700";
    private static final DateTime EVENT_TIMESTAMP = DateTime.parse(EVENT_TIMESTAMP_STRING);

    private ActivityEventController controller;
    private ActivityEventService mockActivityEventService;

    @Before
    public void setup() {
        // Mock services
        mockActivityEventService = mock(ActivityEventService.class);

        StudyService mockStudyService = mock(StudyService.class);
        when(mockStudyService.getStudy(TestConstants.TEST_STUDY)).thenReturn(DUMMY_STUDY);

        controller = spy(new ActivityEventController(mockActivityEventService));
        controller.setStudyService(mockStudyService);

        // Mock session
        UserSession mockSession = mock(UserSession.class);
        when(mockSession.getStudyIdentifier()).thenReturn(TestConstants.TEST_STUDY);
        when(mockSession.getHealthCode()).thenReturn(HEALTH_CODE);
        doReturn(mockSession).when(controller).getAuthenticatedAndConsentedSession();
    }

    @Test
    public void createCustomActivityEvent() throws Exception {
        // Mock request JSON
        String jsonText = "{\n" +
                "   \"eventKey\":\"" + EVENT_KEY + "\",\n" +
                "   \"timestamp\":\"" + EVENT_TIMESTAMP_STRING + "\"\n" +
                "}";
        TestUtils.mockPlayContextWithJson(jsonText);

        // Execute
        Result result = controller.createCustomActivityEvent();
        TestUtils.assertResult(result, 201, "Event recorded");

        // Validate back-end call
        ArgumentCaptor<DateTime> eventTimeCaptor = ArgumentCaptor.forClass(DateTime.class);
        verify(mockActivityEventService).publishCustomEvent(same(DUMMY_STUDY), eq(HEALTH_CODE), eq(EVENT_KEY),
                eventTimeCaptor.capture());

        DateTime eventTime = eventTimeCaptor.getValue();
        System.out.println("eventTime=" + eventTime);
        TestUtils.assertDatesWithTimeZoneEqual(EVENT_TIMESTAMP, eventTime);
    }
}
