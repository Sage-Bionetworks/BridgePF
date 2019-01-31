package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
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
import org.mockito.junit.MockitoJUnitRunner;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dao.SurveyDao;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;
import org.sagebionetworks.bridge.dynamodb.DynamoSurvey;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.ConstraintViolationException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.PublishedSurveyException;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.CompoundActivity;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.SimpleScheduleStrategy;
import org.sagebionetworks.bridge.models.schedules.SurveyReference;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyInfoScreen;
import org.sagebionetworks.bridge.validators.SurveyPublishValidator;

@RunWith(MockitoJUnitRunner.class)
public class SurveyServiceMockTest {

    private static final StudyIdentifier OTHER_STUDY = new StudyIdentifierImpl("other-study");
    private static final String SCHEDULE_PLAN_GUID = "schedulePlanGuid";
    private static final String SURVEY_GUID = "surveyGuid";
    private static final DateTime SURVEY_CREATED_ON = DateTime.parse("2017-02-08T20:07:57.179Z");
    private static final GuidCreatedOnVersionHolder SURVEY_KEYS = new GuidCreatedOnVersionHolderImpl(SURVEY_GUID, 1337);

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
        Survey survey = Survey.create();
        survey.setStudyIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);
        when(mockSurveyDao.getSurvey(any(), eq(false))).thenReturn(survey);
        
        service.getSurvey(TestConstants.TEST_STUDY, SURVEY_KEYS, false, true);
        
        verify(mockSurveyDao).getSurvey(SURVEY_KEYS, false);
    }
    
    @Test
    public void getSurveyMostRecentlyPublishedWithoutElements() {
        Survey survey = Survey.create();
        survey.setStudyIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);
        when(mockSurveyDao.getSurveyMostRecentlyPublishedVersion(TEST_STUDY, SURVEY_GUID, false))
                .thenReturn(survey);
        
        service.getSurveyMostRecentlyPublishedVersion(TEST_STUDY, SURVEY_GUID, false);
        
        verify(mockSurveyDao).getSurveyMostRecentlyPublishedVersion(TEST_STUDY, SURVEY_GUID, false);
    }
    
    @Test
    public void publishSurvey() {
        // test inputs and outputs
        Survey survey = new DynamoSurvey();
        survey.setStudyIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);
        
        // mock DAO
        when(mockSurveyDao.getSurvey(SURVEY_KEYS, true)).thenReturn(survey);
        when(mockSurveyDao.publishSurvey(TEST_STUDY, survey, true)).thenReturn(survey);

        // mock publish validator
        when(mockSurveyPublishValidator.supports(any())).thenReturn(true);

        // execute and validate
        Survey retval = service.publishSurvey(TEST_STUDY, SURVEY_KEYS, true);
        assertSame(survey, retval);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void publishSurveyDeleted() {
        
        Survey survey = new DynamoSurvey();
        survey.setDeleted(true);
        when(mockSurveyDao.getSurvey(SURVEY_KEYS, true)).thenReturn(survey);
        
        service.publishSurvey(TEST_STUDY, SURVEY_KEYS, true);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void publishSurveyDoesNotExist() {
        when(mockSurveyDao.getSurvey(SURVEY_KEYS, true)).thenReturn(null);
        
        service.publishSurvey(TEST_STUDY, SURVEY_KEYS, true);
    }
    
    @Test
    public void successfulDelete() {
        List<SchedulePlan> plans = createSchedulePlanListWithSurveyReference(false);
        
        Activity oldActivity = getActivityList(plans).get(0);
        Activity activity = new Activity.Builder().withActivity(oldActivity)
                .withSurvey("Survey", "otherGuid", SURVEY_CREATED_ON).build();
        getActivityList(plans).set(0, activity);
        
        Survey survey = createSurvey();
        doReturn(survey).when(mockSurveyDao).getSurvey(any(), anyBoolean());
        
        service.deleteSurvey(TestConstants.TEST_STUDY, survey);

        // verify query args
        verify(mockSharedModuleMetadataService).queryAllMetadata(eq(false), eq(false), queryCaptor.capture(),
                paramsCaptor.capture(), eq(null), eq(true));

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
        
        doReturn(plans).when(mockSchedulePlanService).getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, TEST_STUDY, true);
        Survey survey = createSurvey();
        when(mockSurveyDao.getSurvey(any(), eq(false))).thenReturn(survey);
        
        service.deleteSurveyPermanently(TEST_STUDY, survey);

        // verify query args
        verify(mockSharedModuleMetadataService).queryAllMetadata(eq(false), eq(false), queryCaptor.capture(),
                paramsCaptor.capture(), eq(null), eq(true));

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
                any(), anyBoolean())).thenReturn(ImmutableList.of(makeValidMetadata()));

        Survey survey = createSurvey();
        
        doReturn(survey).when(mockSurveyDao).getSurvey(any(), anyBoolean());
        service.deleteSurvey(TestConstants.TEST_STUDY, survey);
    }

    @Test(expected = BadRequestException.class)
    public void physicallyDeleteSurveyNotEmptySharedModules() {
        when(mockSharedModuleMetadataService.queryAllMetadata(anyBoolean(), anyBoolean(), anyString(), any(),
                any(), anyBoolean())).thenReturn(ImmutableList.of(makeValidMetadata()));

        Survey survey = createSurvey();
        when(mockSurveyDao.getSurvey(any(), eq(false))).thenReturn(survey);
        
        service.deleteSurveyPermanently(TEST_STUDY, survey);
    }

    @Test
    public void deleteSurveySucceedsOnPublishedSurvey() {
        Survey survey = createSurvey();
        survey.setPublished(true);
        doReturn(survey).when(mockSurveyDao).getSurvey(any(), anyBoolean());
        
        service.deleteSurvey(TestConstants.TEST_STUDY, survey);
        verify(mockSurveyDao).deleteSurvey(survey);
    }
    
    @Test
    public void deleteSurveyFailsOnLogicallyDeletedSurvey() {
        Survey survey = createSurvey();
        survey.setPublished(false);
        survey.setDeleted(true);
        doReturn(survey).when(mockSurveyDao).getSurvey(any(), anyBoolean());
        
        try {
            service.deleteSurvey(TestConstants.TEST_STUDY, survey);
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
            verify(mockSurveyDao, never()).deleteSurvey(any());
        }
    }
    
    @Test
    public void deleteSurveyFailsOnMissingSurvey() {
        doReturn(null).when(mockSurveyDao).getSurvey(any(), anyBoolean());
        try {
            // Submitted a survey, it doesn't actually match anything in the system.
            service.deleteSurvey(TestConstants.TEST_STUDY, Survey.create());
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
        when(mockSurveyDao.getSurvey(any(), eq(false))).thenReturn(survey);
        
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
        doReturn(Lists.newArrayList(unpubSurvey, survey)).when(mockSurveyDao).getSurveyAllVersions(TEST_STUDY, SURVEY_GUID, false);
        when(mockSurveyDao.getSurvey(any(), eq(false))).thenReturn(survey);
        
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
        when(mockSurveyDao.getSurvey(any(), eq(false))).thenReturn(survey);
        doReturn(Lists.newArrayList(survey)).when(mockSurveyDao).getSurveyAllVersions(TEST_STUDY, SURVEY_GUID, false);
        
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
        Survey survey1 = createSurvey();
        survey1.setPublished(true);
        survey1.setStudyIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);
        Survey survey2 = createSurvey();
        survey2.setPublished(true);
        survey2.setStudyIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);
        when(mockSurveyDao.getSurvey(any(), eq(false))).thenReturn(survey1);
        doReturn(Lists.newArrayList(survey1, survey2)).when(mockSurveyDao).getSurveyAllVersions(TEST_STUDY, SURVEY_GUID, false);
        
        //Does not throw an exception
        service.deleteSurveyPermanently(TEST_STUDY, survey1);
    }
    
    @Test
    public void deleteSurveyPermanentlyNotConstrainedByScheduleWithMultiplePublishedSurveys() {
        // Two published surveys in the list, no exception thrown
        Survey survey = createSurvey();
        when(mockSurveyDao.getSurvey(any(), eq(false))).thenReturn(survey);
        
        service.deleteSurveyPermanently(TEST_STUDY, survey);
    }
    
    @Test
    public void deleteSurveyPermanentlyConstrainedByCompoundSchedule() {
        List<SchedulePlan> plans = createSchedulePlanListWithCompoundActivity(false);
        doReturn(plans).when(mockSchedulePlanService).getSchedulePlans(ClientInfo.UNKNOWN_CLIENT, TEST_STUDY, true);
        Survey survey = createSurvey();
        
        when(mockSurveyDao.getSurvey(any(), eq(false))).thenReturn(survey);
        
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
        doReturn(Lists.newArrayList(unpubSurvey, survey)).when(mockSurveyDao).getSurveyAllVersions(TEST_STUDY, SURVEY_GUID, false);
        when(mockSurveyDao.getSurvey(any(), eq(false))).thenReturn(survey);
        
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
        // Two published surveys in the list, no exception thrown
        Survey survey = createSurvey();
        when(mockSurveyDao.getSurvey(any(), eq(false))).thenReturn(survey);
        
        service.deleteSurveyPermanently(TEST_STUDY, survey);
    }   
    
    @Test(expected = EntityNotFoundException.class)
    public void updateSurveyFailsOnDeletedSurvey() throws Exception {
        Survey existing = Survey.create();
        existing.setDeleted(true);
        
        when(mockSurveyDao.getSurvey(any(), anyBoolean())).thenReturn(existing);
        
        Survey update = Survey.create();
        update.setDeleted(true);
        
        service.updateSurvey(TestConstants.TEST_STUDY, update);
    }
    
    @Test(expected=EntityNotFoundException.class)
    public void updateSurveyFailsOnMissingSurvey() throws Exception {
        when(mockSurveyDao.getSurvey(any(), anyBoolean())).thenReturn(null);
        
        Survey update = Survey.create();
        update.setDeleted(false);
        
        service.updateSurvey(TestConstants.TEST_STUDY, update);
    }
    
    @Test(expected = PublishedSurveyException.class)
    public void updateSurveyAlreadyPublishedThrowsException() {
        Survey existing = Survey.create();
        existing.setStudyIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);
        existing.setDeleted(false);
        existing.setPublished(true);
        
        when(mockSurveyDao.getSurvey(any(), eq(false))).thenReturn(existing);
        
        // Not undeleting... should throw exception
        Survey update = Survey.create();
        update.setStudyIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);
        update.getElements().add(SurveyInfoScreen.create());
        service.updateSurvey(TestConstants.TEST_STUDY, update);
    }
    
    @Test
    public void updateSurveyUndeletePublishedOK() {
        Survey existing = Survey.create();
        existing.setDeleted(true);
        existing.setPublished(true);
        existing.setStudyIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);
        
        when(mockSurveyDao.getSurvey(any(), eq(false))).thenReturn(existing);
        when(mockSurveyDao.getSurvey(any(), eq(true))).thenReturn(existing);
        
        Survey update = Survey.create();
        update.setStudyIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);
        update.setDeleted(false);
        
        service.updateSurvey(TestConstants.TEST_STUDY, update);
        
        verify(mockSurveyDao).updateSurvey(surveyCaptor.capture());
        assertFalse(surveyCaptor.getValue().isDeleted());
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void versionSurveyFailsOnDeletedSurvey() throws Exception {
        Survey survey = Survey.create();
        survey.setDeleted(true);
        
        when(mockSurveyDao.getSurvey(any(), eq(false))).thenReturn(survey);
        
        service.versionSurvey(TestConstants.TEST_STUDY, SURVEY_KEYS);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void versionSurveyFailsOnMissingSurvey() throws Exception {
        when(mockSurveyDao.getSurvey(any(), eq(false))).thenReturn(null);
        
        service.versionSurvey(TestConstants.TEST_STUDY, SURVEY_KEYS);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void deleteSurveyPermanentlyFailsOnMissingSurvey() {
        when(mockSurveyDao.getSurvey(any(), eq(false))).thenReturn(null);
        
        service.deleteSurveyPermanently(TEST_STUDY, SURVEY_KEYS);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void getSurveyAllVersionsThrowsException() {
        service.getSurveyAllVersions(TestConstants.TEST_STUDY, "GUID", true);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void getSurveyInOtherStudyThrowsException() {
        Survey survey = Survey.create();
        survey.setStudyIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);
        when(mockSurveyDao.getSurvey(SURVEY_KEYS, false)).thenReturn(survey);
        
        service.getSurvey(OTHER_STUDY, SURVEY_KEYS, false, true);
    }

    @Test
    public void getSurveyInOtherStudyReturnsNull() {
        Survey survey = Survey.create();
        survey.setStudyIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);
        when(mockSurveyDao.getSurvey(SURVEY_KEYS, false)).thenReturn(survey);
        
        assertNull(service.getSurvey(OTHER_STUDY, SURVEY_KEYS, false, false));
        
        // This was called, but method returned null because study ID didn't match
        verify(mockSurveyDao).getSurvey(SURVEY_KEYS, false);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void getSurveyThrowsException() {
        service.getSurvey(TestConstants.TEST_STUDY, SURVEY_KEYS, false, true);
    }

    @Test
    public void getSurveyReturnsNull() {
        assertNull(service.getSurvey(TestConstants.TEST_STUDY, SURVEY_KEYS, false, false));
        
        // This was called, but method returned null because study ID didn't match
        verify(mockSurveyDao).getSurvey(SURVEY_KEYS, false);
    }

    @Test
    public void updateSurveyInOtherStudy() {
        Survey survey = Survey.create();
        survey.setStudyIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);
        when(mockSurveyDao.getSurvey((GuidCreatedOnVersionHolder)survey, false)).thenReturn(survey);
        
        try {
            service.updateSurvey(OTHER_STUDY, survey);   
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
        }
        verify(mockSurveyDao, never()).updateSurvey(any());
    }

    @Test
    public void publishSurveyInOtherStudy() {
        Survey survey = Survey.create();
        survey.setStudyIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);
        when(mockSurveyDao.getSurvey((GuidCreatedOnVersionHolder)survey, true)).thenReturn(survey);
        
        try {
            service.publishSurvey(OTHER_STUDY, survey, true);   
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
        }
        verify(mockSurveyDao, never()).publishSurvey(any(), any(), anyBoolean());
    }

    @Test
    public void versionSurveyInOtherStudy() {
        Survey survey = Survey.create();
        survey.setStudyIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);
        when(mockSurveyDao.getSurvey(SURVEY_KEYS, false)).thenReturn(survey);
        
        try {
            service.versionSurvey(OTHER_STUDY, SURVEY_KEYS);   
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
        }
        verify(mockSurveyDao, never()).versionSurvey(SURVEY_KEYS);
    }

    @Test
    public void deleteSurveyInOtherStudy() {
        Survey survey = Survey.create();
        survey.setStudyIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);
        when(mockSurveyDao.getSurvey(SURVEY_KEYS, true)).thenReturn(survey);
        
        try {
            service.deleteSurvey(OTHER_STUDY, SURVEY_KEYS);
            fail("Should have thrown an exception");
        } catch(EntityNotFoundException e) {
        }
        verify(mockSurveyDao, never()).deleteSurvey(any());
    }
    
    @Test
    public void deleteSurveyPermanentlyInOtherStudyThrowsException() {
        Survey survey = Survey.create();
        survey.setStudyIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);
        when(mockSurveyDao.getSurvey(SURVEY_KEYS, false)).thenReturn(survey);
        
        try {
            // Does not have admin role, and so will throw an exception
            service.deleteSurveyPermanently(OTHER_STUDY, SURVEY_KEYS);
            fail("Should have thrown an exception");
        } catch(EntityNotFoundException e) {
        }
        verify(mockSurveyDao, never()).deleteSurveyPermanently(any());
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void getSurveyMostRecentlyPublishedVersionInOtherStudy() {
        Survey survey = Survey.create();
        survey.setStudyIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);
        when(mockSurveyDao.getSurveyMostRecentlyPublishedVersion(OTHER_STUDY, SURVEY_GUID, true)).thenReturn(survey);
        
        service.getSurveyMostRecentlyPublishedVersion(OTHER_STUDY, SURVEY_GUID, true);
    }
    
    // This is the same call as the preceding test, but it does not fail because we do not provide
    // a studyId. Checking that the survey is in the same study is skipped.
    @Test
    public void callWithoutStudyIdDoesNotCheckMembershipInStudy() {
        Survey survey = Survey.create();
        survey.setStudyIdentifier(OTHER_STUDY.getIdentifier());
        when(mockSurveyDao.getSurvey(SURVEY_KEYS, true)).thenReturn(survey);
        
        service.getSurvey(null, SURVEY_KEYS, true, true);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void getSurveyMostRecentlyPublishedVersionMissingSurvey() {
        service.getSurveyMostRecentlyPublishedVersion(OTHER_STUDY, SURVEY_GUID, true);
    }

    @Test(expected = EntityNotFoundException.class)
    public void getSurveyMostRecentVersionInOtherStudy() {
        Survey survey = Survey.create();
        survey.setStudyIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);
        when(mockSurveyDao.getSurveyMostRecentVersion(OTHER_STUDY, SURVEY_GUID)).thenReturn(survey);
        
        service.getSurveyMostRecentVersion(OTHER_STUDY, SURVEY_GUID);    
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void getSurveyMostRecentVersionMissingSurvey() {
        service.getSurveyMostRecentVersion(OTHER_STUDY, SURVEY_GUID);    
    }
    
    private List<Activity> getActivityList(List<SchedulePlan> plans) {
        return ((SimpleScheduleStrategy) plans.get(0).getStrategy()).getSchedule().getActivities();
    }

    private Survey createSurvey() {
        Survey survey = new DynamoSurvey();
        survey.setStudyIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);
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
