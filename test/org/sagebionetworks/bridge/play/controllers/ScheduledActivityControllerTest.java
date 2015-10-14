package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoScheduledActivity;
import org.sagebionetworks.bridge.exceptions.NotAuthenticatedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;
import org.sagebionetworks.bridge.play.controllers.ScheduledActivityController;
import org.sagebionetworks.bridge.services.ScheduledActivityService;

import play.mvc.Http;
import play.mvc.Result;
import play.test.Helpers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.Lists;

public class ScheduledActivityControllerTest {

    private ScheduledActivityService scheduledActivityService;
    
    private ScheduledActivityController controller;
    
    private ClientInfo clientInfo;
    
    ArgumentCaptor<ScheduleContext> argument = ArgumentCaptor.forClass(ScheduleContext.class);
    
    @Before
    public void before() throws Exception {
        DynamoScheduledActivity schActivity = new DynamoScheduledActivity();
        schActivity.setTimeZone(DateTimeZone.UTC);
        schActivity.setGuid(BridgeUtils.generateGuid());
        schActivity.setScheduledOn(DateTime.now(DateTimeZone.UTC).minusDays(1));
        schActivity.setActivity(TestConstants.TEST_3_ACTIVITY);
        schActivity.setRunKey(BridgeUtils.generateScheduledActivityRunKey(schActivity, BridgeUtils.generateGuid()));
        List<ScheduledActivity> list = Lists.newArrayList(schActivity);
        
        String json = BridgeObjectMapper.get().writeValueAsString(list);
        Http.Context context = TestUtils.mockPlayContextWithJson(json);
        Http.Context.current.set(context);
        
        UserSession session = new UserSession();
        User user = new User();
        user.setHealthCode("BBB");
        session.setUser(user);
        
        scheduledActivityService = mock(ScheduledActivityService.class);
        when(scheduledActivityService.getScheduledActivities(any(User.class), any(ScheduleContext.class))).thenReturn(list);
        
        controller = spy(new ScheduledActivityController());
        controller.setScheduledActivityService(scheduledActivityService);
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
        
        clientInfo = ClientInfo.fromUserAgentCache("App Name/4 SDK/2");
        doReturn(clientInfo).when(controller).getClientInfoFromUserAgentHeader();
    }
    
    @SuppressWarnings("deprecation")
    @Test
    public void getScheduledActivitiesAsTasksReturnsCorrectType() throws Exception {
        DateTime now = DateTime.parse("2011-05-13T12:37:31.985+03:00");
        
        Result result = controller.getTasks(now.toString(), null, null);
        String output = Helpers.contentAsString(result);
        
        JsonNode results = BridgeObjectMapper.get().readTree(output);
        ArrayNode items = (ArrayNode)results.get("items");
        for (int i=0; i < items.size(); i++) {
            assertEquals("Task", items.get(i).get("type").asText());
        }
    }
    
    @Test
    public void getScheduledActivitiesAsScheduledActivitiesReturnsCorrectType() throws Exception {
        DateTime now = DateTime.parse("2011-05-13T12:37:31.985+03:00");
        
        Result result = controller.getScheduledActivities(now.toString(), null, null);
        String output = Helpers.contentAsString(result);
        
        JsonNode results = BridgeObjectMapper.get().readTree(output);
        ArrayNode items = (ArrayNode)results.get("items");
        for (int i=0; i < items.size(); i++) {
            assertEquals("ScheduledActivity", items.get(i).get("type").asText());
        }
    }
    
    @Test
    public void getScheduledActivitiesWithUntil() throws Exception {
        // Until value is simply passed along as is to the scheduler.
        DateTime now = DateTime.parse("2011-05-13T12:37:31.985+03:00");
        
        controller.getScheduledActivities(now.toString(), null, null);
        verify(scheduledActivityService).getScheduledActivities(any(User.class), argument.capture());
        verifyNoMoreInteractions(scheduledActivityService);
        assertEquals(now, argument.getValue().getEndsOn());
        assertEquals(now.getZone(), argument.getValue().getZone());
    }
    
    @Test
    public void getScheduledActivitiesWithDaysAheadAndTimeZone() throws Exception {
        // We expect the endsOn value to be three days from now at the end of the day 
        // (set millis to 0 so the values match at the end of the test).
        DateTime expectedEndsOn = DateTime.now()
            .withZone(DateTimeZone.forOffsetHours(3)).plusDays(3)
            .withHourOfDay(23).withMinuteOfHour(59).withSecondOfMinute(59).withMillisOfSecond(0);
        
        controller.getScheduledActivities(null, "+03:00", "3");
        verify(scheduledActivityService).getScheduledActivities(any(User.class), argument.capture());
        verifyNoMoreInteractions(scheduledActivityService);
        assertEquals(expectedEndsOn, argument.getValue().getEndsOn().withMillisOfSecond(0));
        assertEquals(expectedEndsOn.getZone(), argument.getValue().getZone());
        assertEquals(clientInfo, argument.getValue().getClientInfo());
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void updateScheduledActivities() throws Exception {
        controller.updateScheduledActivities();
        verify(scheduledActivityService).updateScheduledActivities(anyString(), any(List.class));
        verifyNoMoreInteractions(scheduledActivityService);
    }
    
    @Test(expected = NotAuthenticatedException.class)
    public void mustBeAuthenticated() throws Exception {
        controller = new ScheduledActivityController();
        controller.getScheduledActivities(DateTime.now().toString(), null, null);
    }
    
}
