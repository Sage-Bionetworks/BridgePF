package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;

import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.ScheduledActivityDao;
import org.sagebionetworks.bridge.dao.UserConsentDao;
import org.sagebionetworks.bridge.dynamodb.DynamoSurvey;
import org.sagebionetworks.bridge.dynamodb.DynamoSurveyResponse;
import org.sagebionetworks.bridge.dynamodb.DynamoScheduledActivity;
import org.sagebionetworks.bridge.dynamodb.DynamoScheduledActivityDao;
import org.sagebionetworks.bridge.dynamodb.DynamoUserConsent3;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.UserConsent;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyResponse;
import org.sagebionetworks.bridge.models.surveys.SurveyResponseView;
import org.sagebionetworks.bridge.validators.ScheduleContextValidator;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class ScheduledActivityServiceMockTest {

    private static final DateTime ENROLLMENT = DateTime.parse("2015-04-10T10:40:34.000-07:00");
    
    private static final String HEALTH_CODE = "BBB";
    
    private ScheduledActivityService service;
    
    private SchedulePlanService schedulePlanService;
    
    private UserConsentDao userConsentDao;
    
    private User user;
    
    private ScheduledActivityDao activityDao;
    
    private ActivityEventService activityEventService;
    
    private DateTime endsOn;
    
    @SuppressWarnings("unchecked")
    @Before
    public void before() {
        user = new User();
        user.setStudyKey(TEST_STUDY.getIdentifier());
        user.setHealthCode(HEALTH_CODE);
        
        endsOn = DateTime.now().plusDays(2);
        
        service = new ScheduledActivityService();
        
        schedulePlanService = mock(SchedulePlanService.class);
        when(schedulePlanService.getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, TEST_STUDY)).thenReturn(TestUtils.getSchedulePlans(TEST_STUDY));

        // Each subpopulation pulls a consent with different signOn dates, we want to use the lowest one.
        Subpopulation subpop1 = Subpopulation.create();
        subpop1.setGuid("guid1");
        Subpopulation subpop2 = Subpopulation.create();
        subpop2.setGuid("guid2");
        
        SubpopulationService subpopService = mock(SubpopulationService.class);
        when(subpopService.getSubpopulations(any())).thenReturn(Lists.newArrayList(subpop1, subpop2));
        
        // If this enrollment date (Long.MAX_VALUE) is used in the changePublishedAndAbsoluteSurveyActivity()
        // test, it breaks Joda Time. So success of that test verifies we're taking the lower of the 
        // to signOn dates
        UserConsent consent1 = mock(DynamoUserConsent3.class);
        when(consent1.getSignedOn()).thenReturn(Long.MAX_VALUE);
        
        UserConsent consent2 = mock(DynamoUserConsent3.class);
        when(consent2.getSignedOn()).thenReturn(ENROLLMENT.getMillis());
        
        userConsentDao = mock(UserConsentDao.class);
        when(userConsentDao.getActiveUserConsent(HEALTH_CODE, subpop1)).thenReturn(consent1);
        when(userConsentDao.getActiveUserConsent(HEALTH_CODE, subpop2)).thenReturn(consent2);
        
        Map<String,DateTime> map = Maps.newHashMap();
        activityEventService = mock(ActivityEventService.class);
        when(activityEventService.getActivityEventMap(anyString())).thenReturn(map);
        
        ScheduleContext context = createScheduleContext(endsOn);
        List<ScheduledActivity> scheduledActivities = TestUtils.runSchedulerForActivities(user, context);
        
        activityDao = mock(DynamoScheduledActivityDao.class);
        when(activityDao.getActivity(anyString(), anyString())).thenAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            DynamoScheduledActivity schActivity = new DynamoScheduledActivity();
            schActivity.setHealthCode((String)args[0]);
            schActivity.setGuid((String)args[1]);
            return schActivity;
        });
        when(activityDao.getActivities(context)).thenReturn(scheduledActivities);
        when(activityDao.activityRunHasNotOccurred(anyString(), anyString())).thenReturn(true);
        
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
        service.setScheduledActivityDao(activityDao);
        service.setActivityEventService(activityEventService);
        service.setSubpopulationService(subpopService);
    }
    
    @Test(expected = BadRequestException.class)
    public void rejectsEndsOnBeforeNow() {
        service.getScheduledActivities(user, new ScheduleContext.Builder()
            .withStudyIdentifier(TEST_STUDY)
            .withTimeZone(DateTimeZone.UTC).withEndsOn(DateTime.now().minusSeconds(1)).build());
    }
    
    @Test(expected = BadRequestException.class)
    public void rejectsEndsOnTooFarInFuture() {
        service.getScheduledActivities(user, new ScheduleContext.Builder()
            .withStudyIdentifier(TEST_STUDY)
            .withTimeZone(DateTimeZone.UTC)
            .withEndsOn(DateTime.now().plusDays(ScheduleContextValidator.MAX_EXPIRES_ON_DAYS).plusSeconds(1)).build());
    }

    @Test(expected = BadRequestException.class)
    public void rejectsListOfActivitiesWithNullElement() {
        ScheduleContext context = createScheduleContext(endsOn);
        List<ScheduledActivity> scheduledActivities = TestUtils.runSchedulerForActivities(user, context);
        scheduledActivities.set(0, (DynamoScheduledActivity)null);
        
        service.updateScheduledActivities("AAA", scheduledActivities);
    }
    
    @Test(expected = BadRequestException.class)
    public void rejectsListOfActivitiesWithTaskThatLacksGUID() {
        ScheduleContext context = createScheduleContext(endsOn);
        List<ScheduledActivity> scheduledActivities = TestUtils.runSchedulerForActivities(user, context);
        scheduledActivities.get(0).setGuid(null);
        
        service.updateScheduledActivities("AAA", scheduledActivities);
    }
    
    @SuppressWarnings({"unchecked","rawtypes"})
    @Test
    public void updateActivitiesWorks() {
        ScheduleContext context = createScheduleContext(endsOn);
        List<ScheduledActivity> scheduledActivities = TestUtils.runSchedulerForActivities(user, context);
        
        // 4-5 activities (depending on time of day), finish two, these should publish events, 
        // the third with a startedOn timestamp will be saved, so 3 activities sent to the DAO
        assertTrue(scheduledActivities.size() >= 3);
        scheduledActivities.get(0).setStartedOn(DateTime.now().getMillis());
        scheduledActivities.get(1).setFinishedOn(DateTime.now().getMillis());
        scheduledActivities.get(2).setFinishedOn(DateTime.now().getMillis());
        
        ArgumentCaptor<List> updateCapture = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<ScheduledActivity> publishCapture = ArgumentCaptor.forClass(ScheduledActivity.class);
        
        service.updateScheduledActivities("BBB", scheduledActivities);
        
        verify(activityDao).updateActivities(anyString(), updateCapture.capture());
        // Three activities have timestamp updates and need to be persisted
        verify(activityDao, times(3)).getActivity(anyString(), anyString());
        // Two activities have been finished and generate activity finished events
        verify(activityEventService, times(2)).publishActivityFinishedEvent(publishCapture.capture());
        
        List<DynamoScheduledActivity> dbActivities = (List<DynamoScheduledActivity>)updateCapture.getValue();
        assertEquals(3, dbActivities.size());
        // Correct saved activities
        assertEquals(scheduledActivities.get(0).getGuid(), dbActivities.get(0).getGuid());
        assertEquals(scheduledActivities.get(1).getGuid(), dbActivities.get(1).getGuid());
        assertEquals(scheduledActivities.get(2).getGuid(), dbActivities.get(2).getGuid());
        
        // Correct published activities
        ScheduledActivity publishedActivity1 = publishCapture.getAllValues().get(0);
        assertEquals(scheduledActivities.get(1).getGuid(), publishedActivity1.getGuid());
        ScheduledActivity publishedActivity2 = publishCapture.getAllValues().get(1);
        assertEquals(scheduledActivities.get(2).getGuid(), publishedActivity2.getGuid());
        
    }
    
    @Test(expected = BridgeServiceException.class)
    public void activityListWithNullsRejected() {
        ScheduleContext context = createScheduleContext(endsOn);
        List<ScheduledActivity> activities = TestUtils.runSchedulerForActivities(user, context);
        activities.set(0, null);
        
        service.updateScheduledActivities("BBB", activities);
    }
    
    @Test(expected = BridgeServiceException.class)
    public void activityListWithNullGuidRejected() {
        ScheduleContext context = createScheduleContext(endsOn);
        List<ScheduledActivity> activities = TestUtils.runSchedulerForActivities(user, context);
        activities.get(1).setGuid(null);
        
        service.updateScheduledActivities("BBB", activities);
    }
    
    @Test
    public void deleteActivitiesDoesDelete() {
        service.deleteActivitiesForUser("BBB");
        
        verify(activityDao).deleteActivitiesForUser("BBB");
        verifyNoMoreInteractions(activityDao);
    }

    @SuppressWarnings({"unchecked","rawtypes"})
    @Test
    public void changePublishedAndAbsoluteSurveyActivity() {
        service.getScheduledActivities(user, new ScheduleContext.Builder()
            .withStudyIdentifier(TEST_STUDY)
            .withClientInfo(ClientInfo.UNKNOWN_CLIENT)
            .withTimeZone(DateTimeZone.UTC)
            .withEndsOn(endsOn.plusDays(2))
            .withHealthCode(HEALTH_CODE).build());

        ArgumentCaptor<List> argument = ArgumentCaptor.forClass(List.class);
        verify(activityDao).saveActivities(argument.capture());
        
        boolean foundActivity3 = false;
        for (ScheduledActivity schActivity : (List<ScheduledActivity>)argument.getValue()) {
            // ignoring tapTest
            Activity act = schActivity.getActivity();
            if (act.getTask() != null && !"tapTest".equals(act.getTask().getIdentifier())) {
                String ref = act.getSurveyResponse().getHref();
                assertTrue("Found activity with survey response ref", ref.contains("/v3/surveyresponses/identifier"));        
            } else {
                foundActivity3 = true;
            }
        }
        assertTrue("Found activity with tapTest ref", foundActivity3);
    }
    
    @Test
    public void deleteScheduledActivitiesForUser() {
        service.deleteActivitiesForUser("AAA");
        verify(activityDao).deleteActivitiesForUser("AAA");
    }
    
    @Test
    public void deleteScheduledActivitiesForSchedulePlan() {
        service.deleteActivitiesForSchedulePlan("BBB");
        verify(activityDao).deleteActivitiesForSchedulePlan("BBB");
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void deleteActivitiesForUserRejectsBadValue() {
        service.deleteActivitiesForUser(null);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void deleteActivitiesForSchedulePlanRejectsBadValue() {
        service.deleteActivitiesForUser("  ");
    }    
    
    private ScheduleContext createScheduleContext(DateTime endsOn) {
        Map<String,DateTime> events = Maps.newHashMap();
        events.put("enrollment", ENROLLMENT);
        
        return new ScheduleContext.Builder().withStudyIdentifier(TEST_STUDY).withTimeZone(DateTimeZone.UTC)
            .withEndsOn(endsOn).withHealthCode(HEALTH_CODE).withEvents(events).build();
    }
    
}
