package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.TaskDao;
import org.sagebionetworks.bridge.dao.UserConsentDao;
import org.sagebionetworks.bridge.dynamodb.DynamoSurvey;
import org.sagebionetworks.bridge.dynamodb.DynamoSurveyResponse;
import org.sagebionetworks.bridge.dynamodb.DynamoTask;
import org.sagebionetworks.bridge.dynamodb.DynamoTaskDao;
import org.sagebionetworks.bridge.dynamodb.DynamoUserConsent2;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.UserConsent;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.models.schedules.Task;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyResponse;
import org.sagebionetworks.bridge.models.surveys.SurveyResponseView;

import com.google.common.collect.Maps;

public class TaskServiceTest {

    private static final DateTime ENROLLMENT = DateTime.parse("2015-04-10T10:40:34.000-07:00");
    
    private static final StudyIdentifier STUDY_IDENTIFIER = new StudyIdentifierImpl("foo");
    
    private static final String HEALTH_CODE = "BBB";
    
    private TaskService service;
    
    private SchedulePlanService schedulePlanService;
    
    private UserConsentDao userConsentDao;
    
    private User user;
    
    private TaskDao taskDao;
    
    private DateTime endsOn;
    
    @SuppressWarnings("unchecked")
    @Before
    public void before() {
        user = new User();
        user.setStudyKey(STUDY_IDENTIFIER.getIdentifier());
        user.setHealthCode(HEALTH_CODE);
        
        endsOn = DateTime.now().plusDays(2);
        
        service = new TaskService();
        
        schedulePlanService = mock(SchedulePlanService.class);
        when(schedulePlanService.getSchedulePlans(STUDY_IDENTIFIER)).thenReturn(TestUtils.getSchedulePlans());
        
        UserConsent consent = mock(DynamoUserConsent2.class);
        when(consent.getSignedOn()).thenReturn(ENROLLMENT.getMillis()); 
        
        userConsentDao = mock(UserConsentDao.class);
        when(userConsentDao.getUserConsent(HEALTH_CODE, STUDY_IDENTIFIER)).thenReturn(consent);
        
        Map<String,DateTime> map = Maps.newHashMap();
        TaskEventService taskEventService = mock(TaskEventService.class);
        when(taskEventService.getTaskEventMap(anyString())).thenReturn(map);
        
        ScheduleContext context = createScheduleContest(endsOn);
        List<Task> tasks = TestUtils.runSchedulerForTasks(user, context);

        taskDao = mock(DynamoTaskDao.class);
        when(taskDao.getTasks(HEALTH_CODE, context)).thenReturn(tasks);
        when(taskDao.taskRunHasNotOccurred(anyString(), anyString())).thenReturn(true);

        Survey survey = new DynamoSurvey();
        survey.setGuid("guid");
        survey.setIdentifier("identifier");
        survey.setCreatedOn(20000L);
        SurveyService surveyService = mock(SurveyService.class);
        when(surveyService.getSurveyMostRecentlyPublishedVersion(any(StudyIdentifier.class), anyString())).thenReturn(survey);
        
        SurveyResponse response = new DynamoSurveyResponse();
        response.setHealthCode("healthCode");
        response.setIdentifier("identifier");
        
        SurveyResponseView surveyResponse = new SurveyResponseView(response, survey);
        SurveyResponseService surveyResponseService = mock(SurveyResponseService.class);
        when(surveyResponseService.createSurveyResponse(
            any(GuidCreatedOnVersionHolder.class), anyString(), any(List.class), anyString())).thenReturn(surveyResponse);
        
        service.setSchedulePlanService(schedulePlanService);
        service.setUserConsentDao(userConsentDao);
        service.setSurveyService(surveyService);
        service.setSurveyResponseService(surveyResponseService);
        service.setTaskDao(taskDao);
        service.setTaskEventService(taskEventService);
    }
   
    private ScheduleContext createScheduleContest(DateTime endsOn) {
        Map<String,DateTime> events = Maps.newHashMap();
        events.put("enrollment", ENROLLMENT);
        
        return new ScheduleContext(DateTimeZone.UTC, endsOn, events, null);
    }
    
    @Test(expected = BadRequestException.class)
    public void rejectsEndsOnBeforeNow() {
        service.getTasks(user, new ScheduleContext(DateTimeZone.UTC, DateTime.now().minusSeconds(1), null, null));
    }
    
    @Test(expected = BadRequestException.class)
    public void rejectsEndsOnTooFarInFuture() {
        service.getTasks(user, new ScheduleContext(DateTimeZone.UTC, 
            DateTime.now().plusDays(TaskService.MAX_EXPIRES_ON_DAYS).plusSeconds(1), null, null));
    }

    @Test(expected = BadRequestException.class)
    public void rejectsListOfTasksWithNullElement() {
        ScheduleContext context = createScheduleContest(endsOn);
        List<Task> tasks = TestUtils.runSchedulerForTasks(user, context);
        tasks.set(0, (DynamoTask)null);
        
        service.updateTasks("AAA", tasks);
    }
    
    @Test(expected = BadRequestException.class)
    public void rejectsListOfTasksWithTaskThatLacksGUID() {
        ScheduleContext context = createScheduleContest(endsOn);
        List<Task> tasks = TestUtils.runSchedulerForTasks(user, context);
        tasks.get(0).setGuid(null);
        
        service.updateTasks("AAA", tasks);
    }
    
    @Test
    public void updateTasksWorks() {
        ScheduleContext context = createScheduleContest(endsOn);
        List<Task> tasks = TestUtils.runSchedulerForTasks(user, context);
        
        service.updateTasks("BBB", tasks);
        verify(taskDao).updateTasks("BBB", tasks);
        verifyNoMoreInteractions(taskDao);
    }
    
    @Test
    public void deleteTasksDeletes() {
        service.deleteTasks("BBB");
        
        verify(taskDao).deleteTasks("BBB");
        verifyNoMoreInteractions(taskDao);
    }

    @SuppressWarnings({"unchecked","rawtypes","deprecation"})
    @Test
    public void changePublishedAndAbsoluteSurveyActivity() {
        service.getTasks(user, new ScheduleContext(DateTimeZone.UTC, endsOn.plusDays(2), null, null));

        ArgumentCaptor<List> argument = ArgumentCaptor.forClass(List.class);
        verify(taskDao).saveTasks(anyString(), argument.capture());

        boolean foundTask3 = false;
        for (Task task : (List<Task>)argument.getValue()) {
            // ignoring tapTest
            if (!"tapTest".equals(task.getActivity().getRef())) {
                String ref = task.getActivity().getSurveyResponse().getHref();
                assertTrue("Found task with survey response ref", ref.contains("/v3/surveyresponses/identifier"));        
            } else {
                foundTask3 = true;
            }
        }
        assertTrue("Found task with tapTest ref", foundTask3);
    }
    
}
