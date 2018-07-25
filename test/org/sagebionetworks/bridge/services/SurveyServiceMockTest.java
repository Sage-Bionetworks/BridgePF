package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anySetOf;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.services.SharedModuleMetadataServiceTest.makeValidMetadata;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.dao.SurveyDao;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;
import org.sagebionetworks.bridge.dynamodb.DynamoSurvey;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.ConstraintViolationException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.CompoundActivity;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.SimpleScheduleStrategy;
import org.sagebionetworks.bridge.models.schedules.SurveyReference;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.validators.SurveyPublishValidator;

@RunWith(MockitoJUnitRunner.class)
public class SurveyServiceMockTest {

    private static final String SCHEDULE_PLAN_GUID = "schedulePlanGuid";
    private static final String SURVEY_GUID = "surveyGuid";
    private static final DateTime SURVEY_CREATED_ON = DateTime.parse("2017-02-08T20:07:57.179Z");

    @Mock
    SurveyPublishValidator mockSurveyPublishValidator;

    @Mock
    SurveyDao mockSurveyDao;
    
    @Mock
    SchedulePlanService mockSchedulePlanService;

    @Mock
    SharedModuleMetadataService mockSharedModuleMetadataService;
    
    @Captor
    ArgumentCaptor<GuidCreatedOnVersionHolder> keysCaptor;
    
    @Captor
    ArgumentCaptor<Survey> surveyCaptor;
    
    @Captor
    ArgumentCaptor<String> queryCaptor;
    
    @Captor
    ArgumentCaptor<Map<String,Object>> paramsCaptor;
    
    SurveyService service;
    
    @Before
    public void before() {
        service = new SurveyService();
        service.setSurveyDao(mockSurveyDao);
        service.setSchedulePlanService(mockSchedulePlanService);
        service.setSharedModuleMetadataService(mockSharedModuleMetadataService);
        service.setPublishValidator(mockSurveyPublishValidator);
    }
    
    @Test
    public void getSurveyWithoutElements() {
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl("test-guid", 1337);
        
        service.getSurvey(keys, false);
        
        verify(mockSurveyDao).getSurvey(keys, false, true);
    }
    
    @Test
    public void getSurveyWithoutException() {
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl("test-guid", 1337);
        
        service.getSurvey(keys, true, false);
        
        verify(mockSurveyDao).getSurvey(keys, true, false);
    }
    
    @Test
    public void getSurveyMostRecentlyPublishedWithoutElements() {
        service.getSurveyMostRecentlyPublishedVersion(TEST_STUDY, SURVEY_GUID, false);
        
        verify(mockSurveyDao).getSurveyMostRecentlyPublishedVersion(TEST_STUDY, SURVEY_GUID, false);
    }
    
    @Test
    public void publishSurvey() {
        // test inputs and outputs
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(SURVEY_GUID, 1337);
        Survey survey = new DynamoSurvey();

        // mock DAO
        when(mockSurveyDao.getSurvey(keys, true)).thenReturn(survey);
        when(mockSurveyDao.publishSurvey(TEST_STUDY, survey, true)).thenReturn(survey);

        // mock publish validator
        when(mockSurveyPublishValidator.supports(any())).thenReturn(true);

        // execute and validate
        Survey retval = service.publishSurvey(TEST_STUDY, keys, true);
        assertSame(survey, retval);
    }
    
    @Test
    public void successfulDelete() {
        List<SchedulePlan> plans = createSchedulePlanListWithSurveyReference(false);
        
        Activity oldActivity = getActivityList(plans).get(0);
        Activity activity = new Activity.Builder().withActivity(oldActivity)
                .withSurvey("Survey", "otherGuid", SURVEY_CREATED_ON).build();
        getActivityList(plans).set(0, activity);
        
        doReturn(plans).when(mockSchedulePlanService).getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, TEST_STUDY, false);
        Survey survey = createSurvey();
        doReturn(survey).when(mockSurveyDao).getSurvey(any(), anyBoolean());
        
        service.deleteSurvey(survey);

        // verify query args
        verify(mockSharedModuleMetadataService).queryAllMetadata(eq(false), eq(false), queryCaptor.capture(),
                paramsCaptor.capture(), eq(null));

        String queryStr = queryCaptor.getValue();
        assertEquals("surveyGuid=:surveyGuid AND surveyCreatedOn=:surveyCreatedOn", queryStr);

        assertEquals(survey.getGuid(), paramsCaptor.getValue().get("surveyGuid"));
        assertEquals(survey.getCreatedOn(), paramsCaptor.getValue().get("surveyCreatedOn"));        
        
        verify(mockSurveyDao).deleteSurvey(surveyCaptor.capture());
        assertEquals(survey, surveyCaptor.getValue());
    }
    
    @Test
    public void successfulDeletePermanently() {
        List<SchedulePlan> plans = createSchedulePlanListWithSurveyReference(false);
        
        Activity oldActivity = getActivityList(plans).get(0);
        Activity activity = new Activity.Builder().withActivity(oldActivity)
                .withSurvey("Survey", "otherGuid", SURVEY_CREATED_ON).build();
        getActivityList(plans).set(0, activity);
        
        doReturn(plans).when(mockSchedulePlanService).getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, TEST_STUDY, false);
        Survey survey = createSurvey();
        
        service.deleteSurveyPermanently(TEST_STUDY, survey);

        // verify query args
        verify(mockSharedModuleMetadataService).queryAllMetadata(eq(false), eq(false), queryCaptor.capture(),
                paramsCaptor.capture(), eq(null));

        String queryStr = queryCaptor.getValue();
        assertEquals("surveyGuid=:surveyGuid AND surveyCreatedOn=:surveyCreatedOn", queryStr);
        assertEquals(survey.getGuid(), paramsCaptor.getValue().get("surveyGuid"));
        assertEquals(survey.getCreatedOn(), paramsCaptor.getValue().get("surveyCreatedOn"));
        
        verify(mockSurveyDao).deleteSurveyPermanently(keysCaptor.capture());
        assertEquals(survey, keysCaptor.getValue());
    }

    @Test(expected = BadRequestException.class)
    public void logicallyDeleteSurveyNotEmptySharedModules() {
        when(mockSharedModuleMetadataService.queryAllMetadata(anyBoolean(), anyBoolean(), anyString(), any(),
                anySetOf(String.class))).thenReturn(ImmutableList.of(makeValidMetadata()));

        Survey survey = createSurvey();
        doReturn(survey).when(mockSurveyDao).getSurvey(any(), anyBoolean());
        service.deleteSurvey(survey);
    }

    @Test(expected = BadRequestException.class)
    public void physicallyDeleteSurveyNotEmptySharedModules() {
        when(mockSharedModuleMetadataService.queryAllMetadata(anyBoolean(), anyBoolean(), anyString(), any(),
                anySetOf(String.class))).thenReturn(ImmutableList.of(makeValidMetadata()));

        doReturn(ImmutableList.of()).when(mockSchedulePlanService).getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, TEST_STUDY, false);
        Survey survey = createSurvey();

        service.deleteSurveyPermanently(TEST_STUDY, survey);
    }

    @Test
    public void deleteSurveySucceedsOnPublishedSurvey() {
        Survey survey = createSurvey();
        survey.setPublished(true);
        doReturn(survey).when(mockSurveyDao).getSurvey(any(), anyBoolean());
        
        service.deleteSurvey(survey);
        verify(mockSurveyDao).deleteSurvey(survey);
    }
    
    @Test
    public void deleteSurveyFailsOnLogicallyDeletedSurvey() {
        Survey survey = createSurvey();
        survey.setPublished(false);
        survey.setDeleted(true);
        doReturn(survey).when(mockSurveyDao).getSurvey(any(), anyBoolean());
        
        try {
            service.deleteSurvey(survey);
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
            verify(mockSurveyDao, never()).deleteSurvey(any());
        }
    }
    
    @Test
    public void deleteSurveyPermanentlyConstrainedBySchedule() {
        List<SchedulePlan> plans = createSchedulePlanListWithSurveyReference(false);
        doReturn(plans).when(mockSchedulePlanService).getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, TEST_STUDY, true);
        Survey survey = createSurvey();
        
        try {
            service.deleteSurveyPermanently(TEST_STUDY, survey);
            fail("Should have thrown exception");
        } catch(ConstraintViolationException e) {
            assertTrue(e.getMessage().contains("Cannot delete survey: it is referenced by a schedule plan that is still accessible through the API"));
            assertEquals(SCHEDULE_PLAN_GUID, e.getReferrerKeys().get("guid"));
            assertEquals("SchedulePlan", e.getReferrerKeys().get("type"));
            assertEquals(SURVEY_GUID, e.getEntityKeys().get("guid"));
            assertEquals(SURVEY_CREATED_ON.toString(), e.getEntityKeys().get("createdOn"));
            assertEquals("Survey", e.getEntityKeys().get("type"));
        }
    }
    
    @Test
    public void deleteSurveyPermanentlyConstrainedByScheduleWithPublishedSurvey() {
        List<SchedulePlan> plans = createSchedulePlanListWithSurveyReference(true);
        doReturn(plans).when(mockSchedulePlanService).getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, TEST_STUDY, true);
        
        // One published survey, should throw exception
        Survey survey = createSurvey();
        survey.setPublished(true);
        Survey unpubSurvey = createSurvey();
        unpubSurvey.setPublished(false);
        doReturn(Lists.newArrayList(unpubSurvey, survey)).when(mockSurveyDao).getSurveyAllVersions(TEST_STUDY, SURVEY_GUID, true);
        
        try {
            service.deleteSurveyPermanently(TEST_STUDY, survey);
            fail("Should have thrown exception");
        } catch(ConstraintViolationException e) {
            assertTrue(e.getMessage().contains("Cannot delete survey: it is referenced by a schedule plan that is still accessible through the API"));
            assertEquals(SCHEDULE_PLAN_GUID, e.getReferrerKeys().get("guid"));
            assertEquals("SchedulePlan", e.getReferrerKeys().get("type"));
            assertEquals(SURVEY_GUID, e.getEntityKeys().get("guid"));
            assertEquals(SURVEY_CREATED_ON.toString(), e.getEntityKeys().get("createdOn"));
            assertEquals("Survey", e.getEntityKeys().get("type"));
        }
    }

    @Test
    public void deleteSurveyPermanentlyWithNoOlderPublishedVersionsConstrainedByScheduleWithPublishedSurvey() {
        List<SchedulePlan> plans = createSchedulePlanListWithSurveyReference(true);
        doReturn(plans).when(mockSchedulePlanService).getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, TEST_STUDY, true);
        
        // One published survey, should throw exception
        Survey survey = createSurvey();
        survey.setPublished(true);
        // There is no older published version so this should also throw
        doReturn(Lists.newArrayList(survey)).when(mockSurveyDao).getSurveyAllVersions(TEST_STUDY, SURVEY_GUID, true);
        
        try {
            service.deleteSurveyPermanently(TEST_STUDY, survey);
            fail("Should have thrown exception");
        } catch(ConstraintViolationException e) {
            assertTrue(e.getMessage().contains("Cannot delete survey: it is referenced by a schedule plan that is still accessible through the API"));
            assertEquals(SCHEDULE_PLAN_GUID, e.getReferrerKeys().get("guid"));
            assertEquals("SchedulePlan", e.getReferrerKeys().get("type"));
            assertEquals(SURVEY_GUID, e.getEntityKeys().get("guid"));
            assertEquals(SURVEY_CREATED_ON.toString(), e.getEntityKeys().get("createdOn"));
            assertEquals("Survey", e.getEntityKeys().get("type"));
        }
    }
    
    @Test
    public void deleteSurveyPermanentlyWithOlderPublishedVersionOK() {
        List<SchedulePlan> plans = createSchedulePlanListWithSurveyReference(true);
        doReturn(plans).when(mockSchedulePlanService).getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, TEST_STUDY, true);
        
        // One published survey, should throw exception
        Survey survey = createSurvey();
        survey.setPublished(true);
        
        Survey olderPublished = createSurvey();
        olderPublished.setPublished(true);
        doReturn(Lists.newArrayList(survey, olderPublished)).when(mockSurveyDao).getSurveyAllVersions(TEST_STUDY, SURVEY_GUID, false);
        
        //Does not throw an exception
        service.deleteSurveyPermanently(TEST_STUDY, survey);
    }
    
    @Test
    public void deleteSurveyPermanentlyNotConstrainedByScheduleWithMultiplePublishedSurveys() {
        List<SchedulePlan> plans = createSchedulePlanListWithSurveyReference(true);
        doReturn(plans).when(mockSchedulePlanService).getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, TEST_STUDY, false);
        
        // Two published surveys in the list, no exception thrown
        Survey survey = createSurvey();
        doReturn(Lists.newArrayList(survey, survey)).when(mockSurveyDao).getSurveyAllVersions(TEST_STUDY, SURVEY_GUID, false);
        
        service.deleteSurveyPermanently(TEST_STUDY, survey);
    }
    
    @Test
    public void deleteSurveyPermanentlyConstrainedByCompoundSchedule() {
        List<SchedulePlan> plans = createSchedulePlanListWithCompoundActivity(false);
        doReturn(plans).when(mockSchedulePlanService).getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, TEST_STUDY, true);
        Survey survey = createSurvey();
        
        try {
            service.deleteSurveyPermanently(TEST_STUDY, survey);
            fail("Should have thrown exception");
        } catch(ConstraintViolationException e) {
            assertTrue(e.getMessage().contains("Cannot delete survey: it is referenced by a schedule plan that is still accessible through the API"));
            assertEquals(SCHEDULE_PLAN_GUID, e.getReferrerKeys().get("guid"));
            assertEquals("SchedulePlan", e.getReferrerKeys().get("type"));
            assertEquals(SURVEY_GUID, e.getEntityKeys().get("guid"));
            assertEquals(SURVEY_CREATED_ON.toString(), e.getEntityKeys().get("createdOn"));
            assertEquals("Survey", e.getEntityKeys().get("type"));
        }
    }
    
    @Test
    public void deleteSurveyPermanentlyConstrainedByCompoundScheduleWithPublishedSurvey() {
        List<SchedulePlan> plans = createSchedulePlanListWithCompoundActivity(true);
        doReturn(plans).when(mockSchedulePlanService).getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, TEST_STUDY, true);
        
        // One published survey, should throw exception
        Survey survey = createSurvey();
        survey.setPublished(true);
        Survey unpubSurvey = createSurvey();
        unpubSurvey.setPublished(false);
        doReturn(Lists.newArrayList(unpubSurvey, survey)).when(mockSurveyDao).getSurveyAllVersions(TEST_STUDY, SURVEY_GUID, true);
        
        try {
            service.deleteSurveyPermanently(TEST_STUDY, survey);
            fail("Should have thrown exception");
        } catch(ConstraintViolationException e) {
            assertTrue(e.getMessage().contains("Cannot delete survey: it is referenced by a schedule plan that is still accessible through the API"));
            assertEquals(SCHEDULE_PLAN_GUID, e.getReferrerKeys().get("guid"));
            assertEquals("SchedulePlan", e.getReferrerKeys().get("type"));
            assertEquals(SURVEY_GUID, e.getEntityKeys().get("guid"));
            assertEquals(SURVEY_CREATED_ON.toString(), e.getEntityKeys().get("createdOn"));
            assertEquals("Survey", e.getEntityKeys().get("type"));
        }
    }
    
    @Test
    public void deleteSurveyPermanentlyNotConstrainedByCompoundScheduleWithMultiplePublishedSurveys() {
        List<SchedulePlan> plans = createSchedulePlanListWithCompoundActivity(true);
        doReturn(plans).when(mockSchedulePlanService).getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, TEST_STUDY, false);
        
        // Two published surveys in the list, no exception thrown
        Survey survey = createSurvey();
        doReturn(Lists.newArrayList(survey, survey)).when(mockSurveyDao).getSurveyAllVersions(TEST_STUDY, SURVEY_GUID, false);
        
        service.deleteSurveyPermanently(TEST_STUDY, survey);
    }    
    
    private List<Activity> getActivityList(List<SchedulePlan> plans) {
        return ((SimpleScheduleStrategy) plans.get(0).getStrategy()).getSchedule().getActivities();
    }

    private Survey createSurvey() {
        Survey survey = new DynamoSurvey();
        survey.setGuid(SURVEY_GUID);
        survey.setCreatedOn(SURVEY_CREATED_ON.getMillis());
        survey.setPublished(false);
        return survey;
    }

    private List<SchedulePlan> createSchedulePlanListWithSurveyReference(boolean publishedSurveyRef) {
        return createSchedulePlan(publishedSurveyRef, (surveyReference) -> {
            return new Activity.Builder().withSurvey(surveyReference).build();
        });
    }

    private List<SchedulePlan> createSchedulePlanListWithCompoundActivity(boolean publishedSurveyRef) {
        return createSchedulePlan(publishedSurveyRef, (surveyReference) -> {
            CompoundActivity compoundActivity = new CompoundActivity.Builder()
                    .withSurveyList(Lists.newArrayList(surveyReference)).build();
            return new Activity.Builder().withCompoundActivity(compoundActivity).build();
        });
    }
    
    private List<SchedulePlan> createSchedulePlan(boolean publishedSurveyRef, Function<SurveyReference,Activity> supplier) {
        SurveyReference surveyReference = (publishedSurveyRef) ? 
                new SurveyReference("Survey", SURVEY_GUID, null) : 
                new SurveyReference("Survey", SURVEY_GUID, SURVEY_CREATED_ON);
        SchedulePlan plan = new DynamoSchedulePlan();
        plan.setGuid(SCHEDULE_PLAN_GUID);
        Schedule schedule = new Schedule();
        SimpleScheduleStrategy strategy = new SimpleScheduleStrategy();
        Activity activity = supplier.apply(surveyReference);
        schedule.addActivity(activity);
        strategy.setSchedule(schedule);
        plan.setStrategy(strategy);
        return Lists.newArrayList(plan);
    }
}
