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
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;

import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoTask;
import org.sagebionetworks.bridge.exceptions.NotAuthenticatedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.models.schedules.Task;
import org.sagebionetworks.bridge.play.controllers.TaskController;
import org.sagebionetworks.bridge.services.TaskService;

import play.mvc.Http;

import com.google.common.collect.Lists;

public class TaskControllerTest {

    private TaskService taskService;
    
    private TaskController controller;
    
    ArgumentCaptor<ScheduleContext> argument = ArgumentCaptor.forClass(ScheduleContext.class);
    
    @Before
    public void before() throws Exception {
        ScheduleContext scheduleContext = new ScheduleContext.Builder()
            .withStudyIdentifier(TEST_STUDY)
            .withTimeZone(DateTimeZone.UTC).build();
        
        DynamoTask task = new DynamoTask();
        task.setTimeZone(DateTimeZone.UTC);
        task.setGuid(BridgeUtils.generateGuid());
        task.setScheduledOn(DateTime.now(DateTimeZone.UTC).minusDays(1));
        task.setActivity(TestConstants.TEST_ACTIVITY);
        task.setRunKey(BridgeUtils.generateTaskRunKey(task, BridgeUtils.generateGuid()));
        List<Task> list = Lists.newArrayList(task);
        
        String json = BridgeObjectMapper.get().writeValueAsString(list);
        Http.Context context = TestUtils.mockPlayContextWithJson(json);
        Http.Context.current.set(context);
        
        UserSession session = new UserSession();
        User user = new User();
        user.setHealthCode("BBB");
        session.setUser(user);
        
        taskService = mock(TaskService.class);
        when(taskService.getTasks(any(User.class), any(ScheduleContext.class))).thenReturn(list);
        
        controller = spy(new TaskController());
        controller.setTaskService(taskService);
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
    }
    
    @Test
    public void getTasksWithUntil() throws Exception {
        // Until value is simply passed along as is to the scheduler.
        DateTime now = DateTime.parse("2011-05-13T12:37:31.985+03:00");
        
        controller.getTasks(now.toString(), null, null);
        verify(taskService).getTasks(any(User.class), argument.capture());
        verifyNoMoreInteractions(taskService);
        assertEquals(now, argument.getValue().getEndsOn());
        assertEquals(now.getZone(), argument.getValue().getZone());
    }
    
    @Test
    public void getTasksWithDaysAheadAndTimeZone() throws Exception {
        // We expect the endsOn value to be three days from now at the end of the day 
        // (set millis to 0 so the values match at the end of the test).
        DateTime expectedEndsOn = DateTime.now()
            .withZone(DateTimeZone.forOffsetHours(3)).plusDays(3)
            .withHourOfDay(23).withMinuteOfHour(59).withSecondOfMinute(59).withMillisOfSecond(0);
        
        controller.getTasks(null, "+03:00", "3");
        verify(taskService).getTasks(any(User.class), argument.capture());
        verifyNoMoreInteractions(taskService);
        assertEquals(expectedEndsOn, argument.getValue().getEndsOn().withMillisOfSecond(0));
        assertEquals(expectedEndsOn.getZone(), argument.getValue().getZone());
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void updateTasks() throws Exception {
        controller.updateTasks();
        verify(taskService).updateTasks(anyString(), any(List.class));
        verifyNoMoreInteractions(taskService);
    }
    
    @Test(expected = NotAuthenticatedException.class)
    public void mustBeAuthenticated() throws Exception {
        controller = new TaskController();
        controller.getTasks(DateTime.now().toString(), null, null);
    }
    
}
