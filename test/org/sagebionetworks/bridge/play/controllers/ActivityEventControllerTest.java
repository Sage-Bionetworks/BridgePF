package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import play.mvc.Result;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoActivityEvent;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.activities.ActivityEvent;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.ActivityEventService;
import org.sagebionetworks.bridge.services.StudyService;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;

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
        TestUtils.assertDatesWithTimeZoneEqual(EVENT_TIMESTAMP, eventTime);
    }
    
    @Test
    public void getUsersActivityEvents() throws Exception {
        DynamoActivityEvent event = new DynamoActivityEvent();
        event.setHealthCode(HEALTH_CODE);
        event.setEventId("someId");
        event.setTimestamp(EVENT_TIMESTAMP.getMillis());
        event.setAnswerValue("someValue");
        List<ActivityEvent> list = ImmutableList.of(event);
        
        when(mockActivityEventService.getActivityEventList(HEALTH_CODE)).thenReturn(list);
        
        Result result = controller.getSelfActivityEvents();
        TestUtils.assertResult(result, 200);
        
        JsonNode node = TestUtils.getJson(result);
        
        JsonNode object = node.get("items").get(0);
        assertEquals("someValue", object.get("answerValue").textValue());
        assertEquals(EVENT_TIMESTAMP.withZone(DateTimeZone.UTC).toString(), object.get("timestamp").textValue());
        assertEquals("someId", object.get("eventId").textValue());
        assertEquals("ActivityEvent", object.get("type").textValue());
        assertEquals(4, node.size());
    }
}
