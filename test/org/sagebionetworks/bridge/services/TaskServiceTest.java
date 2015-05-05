package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.joda.time.DateTime;
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
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserConsent;
import org.sagebionetworks.bridge.models.schedules.Task;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyResponse;
import org.sagebionetworks.bridge.models.surveys.SurveyResponseView;

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
        endsOn = DateTime.now().plusDays(2);
        user = new User();
        user.setStudyKey(STUDY_IDENTIFIER.getIdentifier());
        user.setHealthCode(HEALTH_CODE);
        
        service = new TaskService();
        
        schedulePlanService = mock(SchedulePlanService.class);
        when(schedulePlanService.getSchedulePlans(STUDY_IDENTIFIER)).thenReturn(TestUtils.getSchedulePlans());
        
        UserConsent consent = mock(DynamoUserConsent2.class);
        when(consent.getSignedOn()).thenReturn(ENROLLMENT.getMillis()); 
        
        userConsentDao = mock(UserConsentDao.class);
        when(userConsentDao.getUserConsent(HEALTH_CODE, STUDY_IDENTIFIER)).thenReturn(consent);
        
        List<Task> tasks = TestUtils.runSchedulerForTasks(user, endsOn);

        taskDao = mock(DynamoTaskDao.class);
        when(taskDao.getTasks(HEALTH_CODE, endsOn)).thenReturn(tasks);
        when(taskDao.taskRunHasNotOccurred(anyString(), anyString())).thenReturn(true);

        Survey survey = new DynamoSurvey();
        survey.setGuid("guid");
        survey.setCreatedOn(20000L);
        SurveyService surveyService = mock(SurveyService.class);
        when(surveyService.getSurveyMostRecentlyPublishedVersion(any(StudyIdentifier.class), anyString())).thenReturn(survey);
        
        SurveyResponse response = new DynamoSurveyResponse();
        response.setHealthCode("healthCode");
        response.setIdentifier("identifier");
        
        SurveyResponseView surveyResponse = new SurveyResponseView(response, survey);
        SurveyResponseService surveyResponseService = mock(SurveyResponseService.class);
        when(surveyResponseService.createSurveyResponse(
            any(GuidCreatedOnVersionHolder.class), anyString(), any(List.class))).thenReturn(surveyResponse);
        
        service.setSchedulePlanService(schedulePlanService);
        service.setUserConsentDao(userConsentDao);
        service.setSurveyService(surveyService);
        service.setSurveyResponseService(surveyResponseService);
        service.setTaskDao(taskDao);
    }
   
    @Test(expected = BadRequestException.class)
    public void rejectsEndsOnBeforeNow() {
        service.getTasks(user, DateTime.now().minusSeconds(1));
    }
    
    @Test(expected = BadRequestException.class)
    public void rejectsEndsOnTooFarInFuture() {
        service.getTasks(user, DateTime.now().plusDays(TaskService.MAX_EXPIRES_ON_DAYS).plusSeconds(1));
    }

    @Test(expected = BadRequestException.class)
    public void rejectsListOfTasksWithNullElement() {
        List<Task> tasks = TestUtils.runSchedulerForTasks(user, endsOn);
        tasks.set(0, (DynamoTask)null);
        
        service.updateTasks("AAA", tasks);
    }
    
    @Test(expected = BadRequestException.class)
    public void rejectsListOfTasksWithTaskThatLacksGUID() {
        List<Task> tasks = TestUtils.runSchedulerForTasks(user, endsOn);
        tasks.get(0).setGuid(null);
        
        service.updateTasks("AAA", tasks);
    }
    
    @Test
    public void updateTasksWorks() {
        List<Task> tasks = TestUtils.runSchedulerForTasks(user, endsOn);
        
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

    @SuppressWarnings({"unchecked","rawtypes"})
    @Test
    public void changePublishedAndAbsoluteSurveyActivity() {
        service.getTasks(user, endsOn.plusDays(2));
        
        ArgumentCaptor<List> argument = ArgumentCaptor.forClass(List.class);
        verify(taskDao).saveTasks(anyString(), argument.capture());

        boolean foundTask3 = false;
        for (Task task : (List<Task>)argument.getValue()) {
            // ignoring task3
            String ref = task.getActivity().getRef();
            if (!"task:task3".equals(ref)) {
                assertTrue("Found task with survey response ref", ref.contains("/surveyresponses/identifier"));        
            } else {
                foundTask3 = true;
            }
        }
        assertTrue("Found task with task:task3 ref", foundTask3);
    }
    
}
