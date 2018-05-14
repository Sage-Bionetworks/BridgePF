package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anySetOf;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.services.SharedModuleMetadataServiceTest.makeValidMetadata;

import java.util.List;
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
import org.sagebionetworks.bridge.hibernate.HqlWhereClause;
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
    public void publishSurvey() {
        // test inputs and outputs
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl("test-guid", 1337);
        Survey survey = new DynamoSurvey();

        // mock DAO
        when(mockSurveyDao.getSurvey(keys)).thenReturn(survey);
        when(mockSurveyDao.publishSurvey(TEST_STUDY, survey, keys, true)).thenReturn(survey);

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
        
        doReturn(plans).when(mockSchedulePlanService).getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, TEST_STUDY);
        Survey survey = createSurvey();
        doReturn(survey).when(mockSurveyDao).getSurvey(any());
        
        service.deleteSurvey(survey);

        // verify query args
        ArgumentCaptor<HqlWhereClause> clauseCaptor = ArgumentCaptor.forClass(HqlWhereClause.class);
        verify(mockSharedModuleMetadataService).queryAllMetadata(eq(false), eq(false), clauseCaptor.capture(),  eq(null));

        HqlWhereClause clause = clauseCaptor.getValue();
        assertEquals("surveyGuid=:surveyGuid AND surveyCreatedOn=:surveyCreatedOn", clause.getClause());
        assertEquals(survey.getGuid(), clause.getParameters().get("surveyGuid"));
        assertEquals(survey.getCreatedOn(), clause.getParameters().get("surveyCreatedOn"));

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
        
        doReturn(plans).when(mockSchedulePlanService).getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, TEST_STUDY);
        Survey survey = createSurvey();
        
        service.deleteSurveyPermanently(TEST_STUDY, survey);

        // verify query args
        ArgumentCaptor<HqlWhereClause> clauseCaptor = ArgumentCaptor.forClass(HqlWhereClause.class);
        verify(mockSharedModuleMetadataService).queryAllMetadata(eq(false), eq(false), clauseCaptor.capture(),  eq(null));

        HqlWhereClause clause = clauseCaptor.getValue();
        assertEquals("surveyGuid=:surveyGuid AND surveyCreatedOn=:surveyCreatedOn", clause.getClause());
        assertEquals(survey.getGuid(), clause.getParameters().get("surveyGuid"));
        assertEquals(survey.getCreatedOn(), clause.getParameters().get("surveyCreatedOn"));

        verify(mockSurveyDao).deleteSurveyPermanently(keysCaptor.capture());
        assertEquals(survey, keysCaptor.getValue());
    }

    @Test(expected = BadRequestException.class)
    public void logicallyDeleteSurveyNotEmptySharedModules() {
        when(mockSharedModuleMetadataService.queryAllMetadata(anyBoolean(), anyBoolean(), any(HqlWhereClause.class),
                anySetOf(String.class))).thenReturn(ImmutableList.of(makeValidMetadata()));

        Survey survey = createSurvey();
        doReturn(survey).when(mockSurveyDao).getSurvey(any());
        service.deleteSurvey(survey);
    }

    @Test(expected = BadRequestException.class)
    public void physicallyDeleteSurveyNotEmptySharedModules() {
        when(mockSharedModuleMetadataService.queryAllMetadata(anyBoolean(), anyBoolean(), any(HqlWhereClause.class),
                anySetOf(String.class))).thenReturn(ImmutableList.of(makeValidMetadata()));

        doReturn(ImmutableList.of()).when(mockSchedulePlanService).getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, TEST_STUDY);
        Survey survey = createSurvey();

        service.deleteSurveyPermanently(TEST_STUDY, survey);
    }

    @Test
    public void deleteSurveySucceedsOnPublishedSurvey() {
        Survey survey = createSurvey();
        survey.setPublished(true);
        doReturn(survey).when(mockSurveyDao).getSurvey(any());
        
        service.deleteSurvey(survey);
        verify(mockSurveyDao).deleteSurvey(survey);
    }
    
    @Test
    public void deleteSurveyFailsOnLogicallyDeletedSurvey() {
        Survey survey = createSurvey();
        survey.setPublished(false);
        survey.setDeleted(true);
        doReturn(survey).when(mockSurveyDao).getSurvey(any());
        
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
        doReturn(plans).when(mockSchedulePlanService).getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, TEST_STUDY);
        Survey survey = createSurvey();
        
        try {
            service.deleteSurveyPermanently(TEST_STUDY, survey);
            fail("Should have thrown exception");
        } catch(ConstraintViolationException e) {
            assertTrue(e.getMessage().contains("Operation not permitted because entity"));
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
        doReturn(plans).when(mockSchedulePlanService).getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, TEST_STUDY);
        
        // One published survey, should throw exception
        Survey survey = createSurvey();
        survey.setPublished(true);
        Survey unpubSurvey = createSurvey();
        unpubSurvey.setPublished(false);
        doReturn(Lists.newArrayList(unpubSurvey, survey)).when(mockSurveyDao).getSurveyAllVersions(TEST_STUDY, SURVEY_GUID);
        
        try {
            service.deleteSurveyPermanently(TEST_STUDY, survey);
            fail("Should have thrown exception");
        } catch(ConstraintViolationException e) {
            assertTrue(e.getMessage().contains("Operation not permitted because entity"));
            assertEquals(SCHEDULE_PLAN_GUID, e.getReferrerKeys().get("guid"));
            assertEquals("SchedulePlan", e.getReferrerKeys().get("type"));
            assertEquals(SURVEY_GUID, e.getEntityKeys().get("guid"));
            assertEquals(SURVEY_CREATED_ON.toString(), e.getEntityKeys().get("createdOn"));
            assertEquals("Survey", e.getEntityKeys().get("type"));
        }
    }

    @Test
    public void deleteSurveyPermanentlyNotConstrainedByScheduleWithMultiplePublishedSurveys() {
        List<SchedulePlan> plans = createSchedulePlanListWithSurveyReference(true);
        doReturn(plans).when(mockSchedulePlanService).getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, TEST_STUDY);
        
        // Two published surveys in the list, no exception thrown
        Survey survey = createSurvey();
        doReturn(Lists.newArrayList(survey, survey)).when(mockSurveyDao).getSurveyAllVersions(TEST_STUDY, SURVEY_GUID);
        
        service.deleteSurveyPermanently(TEST_STUDY, survey);
    }
    
    @Test
    public void deleteSurveyPermanentlyConstrainedByCompoundSchedule() {
        List<SchedulePlan> plans = createSchedulePlanListWithCompoundActivity(false);
        doReturn(plans).when(mockSchedulePlanService).getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, TEST_STUDY);
        Survey survey = createSurvey();
        
        try {
            service.deleteSurveyPermanently(TEST_STUDY, survey);
            fail("Should have thrown exception");
        } catch(ConstraintViolationException e) {
            assertTrue(e.getMessage().contains("Operation not permitted because entity"));
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
        doReturn(plans).when(mockSchedulePlanService).getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, TEST_STUDY);
        
        // One published survey, should throw exception
        Survey survey = createSurvey();
        survey.setPublished(true);
        Survey unpubSurvey = createSurvey();
        unpubSurvey.setPublished(false);
        doReturn(Lists.newArrayList(unpubSurvey, survey)).when(mockSurveyDao).getSurveyAllVersions(TEST_STUDY, SURVEY_GUID);
        
        try {
            service.deleteSurveyPermanently(TEST_STUDY, survey);
            fail("Should have thrown exception");
        } catch(ConstraintViolationException e) {
            assertTrue(e.getMessage().contains("Operation not permitted because entity"));
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
        doReturn(plans).when(mockSchedulePlanService).getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, TEST_STUDY);
        
        // Two published surveys in the list, no exception thrown
        Survey survey = createSurvey();
        doReturn(Lists.newArrayList(survey, survey)).when(mockSurveyDao).getSurveyAllVersions(TEST_STUDY, SURVEY_GUID);
        
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
