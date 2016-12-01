package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.WORKER;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.cache.ViewCache;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.dynamodb.DynamoSurvey;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.TestSurvey;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.services.SurveyService;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import play.mvc.Result;
import play.test.Helpers;

/**
 * We know this controller works given the integration tests. Here I'm interested in finding a way 
 * to test cross-study security that won't take another hour in test time to run. Creating a second study, 
 * etc. through integration tests is very slow.
 */
public class SurveyControllerTest {

    private static final TypeReference<ResourceList<Survey>> TYPE_REF_SURVEY_LIST =
            new TypeReference<ResourceList<Survey>>(){};

    private static final boolean CONSENTED = true;
    private static final boolean UNCONSENTED = false;
            
    private static final StudyIdentifier API_STUDY_ID = new StudyIdentifierImpl("api");
    
    private static final StudyIdentifier SECONDSTUDY_STUDY_ID = new StudyIdentifierImpl("secondstudy");
    
    private static final String SURVEY_GUID = "bbb";
    
    private static final DateTime CREATED_ON = DateTime.now();
    
    private static final GuidCreatedOnVersionHolder KEYS = new GuidCreatedOnVersionHolderImpl(SURVEY_GUID, CREATED_ON.getMillis());
            
    private SurveyController controller;
    
    private SurveyService service;
    
    private StudyService studyService;
    
    private ViewCache viewCache;
    
    private Map<String,String> cacheMap;
    
    private UserSession session;
    
    @Before
    public void before() {
        // Finish mocking this in each test?
        service = mock(SurveyService.class);
        
        // Dummy this out so it works and we can forget about it as a dependency
        cacheMap = Maps.newHashMap();
        viewCache = new ViewCache();
        CacheProvider provider = mock(CacheProvider.class);
        when(provider.getString(anyString())).thenAnswer(new Answer<String>() {
            @Override
            public String answer(InvocationOnMock invocation) throws Throwable {
                String key = invocation.getArgumentAt(0, String.class);
                return cacheMap.get(key);
            }
        });
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                String key = invocation.getArgumentAt(0, String.class);
                String value = invocation.getArgumentAt(1, String.class);
                cacheMap.put(key, value);
                return null;
            }
        }).when(provider).setString(anyString(), anyString(), anyInt());
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                String key = invocation.getArgumentAt(0, String.class);
                cacheMap.remove(key);
                return null;
            }
        }).when(provider).removeString(anyString());
        viewCache.setCacheProvider(provider);
        
        studyService = mock(StudyService.class);
        DynamoStudy study = new DynamoStudy();
        doReturn(study).when(studyService).getStudy(any(StudyIdentifier.class));
        
        controller = spy(new SurveyController());
        controller.setSurveyService(service);
        controller.setViewCache(viewCache);
        controller.setStudyService(studyService);
    }
    
    private void setupContext(StudyIdentifier studyIdentifier, Roles role, boolean hasConsented, Object object) throws Exception {
        // Setup the context, with our without JSON in the POST body
        if (object == null) {
            TestUtils.mockPlayContext();
        } else {
            String json = BridgeObjectMapper.get().writeValueAsString(object);
            TestUtils.mockPlayContextWithJson(json);
        }
        
        // Create a participant (with a role, if given)
        StudyParticipant.Builder builder = new StudyParticipant.Builder().withHealthCode("BBB");
        if (role != null) {
            builder.withRoles(Sets.newHashSet(role)).build();
        }
        StudyParticipant participant = builder.build();

        // Set up a session that is returned as if the user is already signed in.
        session = new UserSession(participant);
        session.setStudyIdentifier(studyIdentifier);
        session.setAuthenticated(true);
        doReturn(session).when(controller).getSessionIfItExists();
        
        // ... and setup session to report user consented, if needed.
        if (hasConsented) {
            Map<SubpopulationGuid, ConsentStatus> consentStatuses = TestUtils
                    .toMap(new ConsentStatus.Builder().withName("Name").withConsented(true)
                            .withGuid(SubpopulationGuid.create("guid")).withSignedMostRecentConsent(true).build());
            session.setConsentStatuses(consentStatuses);
        }
    }
    
    @Test
    public void verifyViewCacheIsWorking() throws Exception {
        setupContext(API_STUDY_ID, DEVELOPER, CONSENTED, null);
        when(service.getSurveyMostRecentlyPublishedVersion(any(StudyIdentifier.class), anyString())).thenReturn(getSurvey(false));
        
        controller.getSurveyMostRecentlyPublishedVersionForUser(SURVEY_GUID);
        controller.getSurveyMostRecentlyPublishedVersionForUser(SURVEY_GUID);
        
        verify(service, times(1)).getSurveyMostRecentlyPublishedVersion(API_STUDY_ID, SURVEY_GUID);
        verifyNoMoreInteractions(service);
    }

    @Test
    public void getAllSurveysMostRecentVersion() throws Exception {
        setupContext(API_STUDY_ID, DEVELOPER, UNCONSENTED, null);
        when(service.getAllSurveysMostRecentVersion(any(StudyIdentifier.class))).thenReturn(getSurveys(3, false));
        
        controller.getAllSurveysMostRecentVersion();
        
        verify(service).getAllSurveysMostRecentVersion(API_STUDY_ID);
        verifyNoMoreInteractions(service);
    }
    
    @Test
    public void cannotGetAllSurveysMostRecentVersionInOtherStudy() throws Exception {
        setupContext(SECONDSTUDY_STUDY_ID, DEVELOPER, UNCONSENTED, null);
        when(service.getAllSurveysMostRecentVersion(any(StudyIdentifier.class))).thenReturn(getSurveys(3, false));

        try {
            controller.getAllSurveysMostRecentVersion();
            fail("Should have thrown exception");
        } catch(UnauthorizedException e) {
            verify(service).getAllSurveysMostRecentVersion(SECONDSTUDY_STUDY_ID);
            verifyNoMoreInteractions(service);
        }
    }

    @Test
    public void getAllSurveysMostRecentlyPublishedVersion() throws Exception {
        setupContext(API_STUDY_ID, DEVELOPER, UNCONSENTED, null);
        when(service.getAllSurveysMostRecentlyPublishedVersion(API_STUDY_ID)).thenReturn(getSurveys(2, false));
        
        controller.getAllSurveysMostRecentlyPublishedVersion();
        
        verify(service).getAllSurveysMostRecentlyPublishedVersion(API_STUDY_ID);
        verifyNoMoreInteractions(service);
    }

    @Test
    public void getAllSurveysMostRecentlyPublishedVersionForStudy() throws Exception {
        setupContext(SECONDSTUDY_STUDY_ID, WORKER, UNCONSENTED, null);

        // make surveys
        List<Survey> surveyList = getSurveys(2, false);
        surveyList.get(0).setGuid("survey-0");
        surveyList.get(1).setGuid("survey-1");
        when(service.getAllSurveysMostRecentlyPublishedVersion(TestConstants.TEST_STUDY)).thenReturn(surveyList);

        // execute and validate
        Result result = controller.getAllSurveysMostRecentlyPublishedVersionForStudy(API_STUDY_ID.getIdentifier());
        String resultStr = Helpers.contentAsString(result);
        ResourceList<Survey> resultSurveyResourceList = BridgeObjectMapper.get().readValue(resultStr,
                TYPE_REF_SURVEY_LIST);
        List<Survey> resultSurveyList = resultSurveyResourceList.getItems();
        assertEquals(2, resultSurveyList.size());
        assertEquals("survey-0", resultSurveyList.get(0).getGuid());
        assertEquals("survey-1", resultSurveyList.get(1).getGuid());
    }

    @Test
    public void cannotGetAllSurveysMostRecentlyPublishedVersionInOtherStudy() throws Exception {
        setupContext(SECONDSTUDY_STUDY_ID, DEVELOPER, UNCONSENTED, null);
        when(service.getAllSurveysMostRecentlyPublishedVersion(SECONDSTUDY_STUDY_ID)).thenReturn(getSurveys(2, false));

        try {
            controller.getAllSurveysMostRecentlyPublishedVersion();
            fail("Should have thrown exception");
        } catch(UnauthorizedException e) {
            verify(service).getAllSurveysMostRecentlyPublishedVersion(SECONDSTUDY_STUDY_ID);
            verifyNoMoreInteractions(service);
        }
    }
    
    @Test
    public void getSurveyForUser() throws Exception {
        setupContext(API_STUDY_ID, DEVELOPER, UNCONSENTED, null);
        when(service.getSurvey(KEYS)).thenReturn(getSurvey(false));
        
        controller.getSurvey(SURVEY_GUID, CREATED_ON.toString());
        
        verify(service).getSurvey(KEYS);
        verifyNoMoreInteractions(service);
    }

    @Test
    public void cannotGetSurveyForUserInOtherStudy() throws Exception {
        setupContext(SECONDSTUDY_STUDY_ID, DEVELOPER, CONSENTED, null);
        when(service.getSurvey(KEYS)).thenReturn(getSurvey(false));

        try {
            controller.getSurveyForUser(SURVEY_GUID, CREATED_ON.toString());
            fail("Should have thrown exception");
        } catch(UnauthorizedException e) {
            verify(service).getSurvey(KEYS);
            verifyNoMoreInteractions(service);
        }
    }
    
    @Test
    public void getSurveyMostRecentlyPublishedVersionForUser() throws Exception {
        setupContext(API_STUDY_ID, DEVELOPER, CONSENTED, null);
        when(service.getSurveyMostRecentlyPublishedVersion(API_STUDY_ID, SURVEY_GUID)).thenReturn(getSurvey(false));
        
        controller.getSurveyMostRecentlyPublishedVersionForUser(SURVEY_GUID);
        
        verify(service).getSurveyMostRecentlyPublishedVersion(API_STUDY_ID, SURVEY_GUID);
        verifyNoMoreInteractions(service);
    }
    
    @Test
    public void cannotGetSurveyMostRecentlyPublishedVersionForUserFromOtherStudy() throws Exception {
        setupContext(SECONDSTUDY_STUDY_ID, DEVELOPER, CONSENTED, null);
        when(service.getSurveyMostRecentlyPublishedVersion(SECONDSTUDY_STUDY_ID, SURVEY_GUID)).thenReturn(getSurvey(false));
        
        try {
            controller.getSurveyMostRecentlyPublishedVersionForUser(SURVEY_GUID);
            fail("Should have thrown exception");
        } catch(UnauthorizedException e) {
            verify(service).getSurveyMostRecentlyPublishedVersion(SECONDSTUDY_STUDY_ID, SURVEY_GUID);
            verifyNoMoreInteractions(service);
        }
    }
    
    @Test
    public void getSurvey() throws Exception {
        setupContext(API_STUDY_ID, DEVELOPER, CONSENTED, null);
        
        when(service.getSurvey(KEYS)).thenReturn(getSurvey(false));
        
        controller.getSurvey(SURVEY_GUID, CREATED_ON.toString());
        
        verify(service).getSurvey(KEYS);
        verifyNoMoreInteractions(service);
    }
    
    @Test
    public void cannotGetSurveyFromOtherStudy() throws Exception {
        setupContext(SECONDSTUDY_STUDY_ID, DEVELOPER, UNCONSENTED, null);
        when(service.getSurvey(KEYS)).thenReturn(getSurvey(false));
        
        try {
            controller.getSurvey(SURVEY_GUID, CREATED_ON.toString());
            fail("Should have thrown exception");
        } catch(UnauthorizedException e) {
            verify(service).getSurvey(KEYS);
            verifyNoMoreInteractions(service);
        }
    }

    @Test
    public void getSurveyForWorker() throws Exception {
        setupContext(SECONDSTUDY_STUDY_ID, WORKER, UNCONSENTED, null);
        
        // make survey
        Survey survey = getSurvey(false);
        survey.setGuid("test-survey");
        when(service.getSurvey(KEYS)).thenReturn(survey);

        // execute and validate
        Result result = controller.getSurvey(SURVEY_GUID, CREATED_ON.toString());
        String resultStr = Helpers.contentAsString(result);
        Survey resultSurvey = BridgeObjectMapper.get().readValue(resultStr, Survey.class);
        assertEquals("test-survey", resultSurvey.getGuid());
    }

    @Test
    public void getSurveyMostRecentVersion() throws Exception {
        setupContext(API_STUDY_ID, DEVELOPER, UNCONSENTED, null);
        when(service.getSurveyMostRecentVersion(API_STUDY_ID, SURVEY_GUID)).thenReturn(getSurvey(false));
        
        controller.getSurveyMostRecentVersion(SURVEY_GUID);
        
        verify(service).getSurveyMostRecentVersion(API_STUDY_ID, SURVEY_GUID);
        verifyNoMoreInteractions(service);
    }

    @Test
    public void cannotGetSurveyMostRecentVersionFromOtherStudy() throws Exception {
        setupContext(SECONDSTUDY_STUDY_ID, DEVELOPER, UNCONSENTED, null);
        
        // It's not possible to query for surveys in another study... the study comes
        // from the session. But if that somehow returned a survey in another study, 
        // it would still throw an exception.
        when(service.getSurveyMostRecentVersion(API_STUDY_ID, SURVEY_GUID)).thenReturn(getSurvey(false));
        
        try {
            controller.getSurveyMostRecentVersion(SURVEY_GUID);
            fail("Should have thrown exception");
        } catch(UnauthorizedException e) {
            verify(service).getSurveyMostRecentVersion(SECONDSTUDY_STUDY_ID, SURVEY_GUID);
            verifyNoMoreInteractions(service);
        }
    }
    
    @Test
    public void getSurveyMostRecentlyPublishedVersion() throws Exception {
        setupContext(API_STUDY_ID, DEVELOPER, UNCONSENTED, null);
        when(service.getSurveyMostRecentlyPublishedVersion(API_STUDY_ID, SURVEY_GUID)).thenReturn(getSurvey(false));
        
        controller.getSurveyMostRecentlyPublishedVersion(SURVEY_GUID);
        
        verify(service).getSurveyMostRecentlyPublishedVersion(API_STUDY_ID, SURVEY_GUID);
        verifyNoMoreInteractions(service);
    }
    
    @Test
    public void cannotGetSurveyMostRecentlyPublishedVersionFromOtherStudy() throws Exception {
        setupContext(SECONDSTUDY_STUDY_ID, DEVELOPER, UNCONSENTED, null);
        when(service.getSurveyMostRecentlyPublishedVersion(SECONDSTUDY_STUDY_ID, SURVEY_GUID)).thenReturn(getSurvey(false));
        
        try {
            controller.getSurveyMostRecentlyPublishedVersion(SURVEY_GUID);
            fail("Should have thrown exception");
        } catch(UnauthorizedException e) {
            verify(service).getSurveyMostRecentlyPublishedVersion(SECONDSTUDY_STUDY_ID, SURVEY_GUID);
            verifyNoMoreInteractions(service);
        }
    }
    
    @Test
    public void deleteSurveyAllowedForDeveloper() throws Exception {
        setupContext(API_STUDY_ID, DEVELOPER, UNCONSENTED, null);
        
        Survey survey = getSurvey(false);
        when(service.getSurvey(KEYS)).thenReturn(survey);
        
        controller.deleteSurvey(SURVEY_GUID, CREATED_ON.toString(), "false");

        verify(service).getSurvey(KEYS);
        verify(service).deleteSurvey(survey);
        verifyNoMoreInteractions(service);
    }
    
    @Test
    public void physicalDeleteOfSurveyNotAllowedForDeveloper() throws Exception {
        setupContext(API_STUDY_ID, DEVELOPER, UNCONSENTED, null);
        
        Survey survey = getSurvey(false);
        when(service.getSurvey(KEYS)).thenReturn(survey);
        
        controller.deleteSurvey(SURVEY_GUID, CREATED_ON.toString(), "true");
        
        verify(service).getSurvey(KEYS);
        verify(service).deleteSurvey(survey);
        verifyNoMoreInteractions(service);
    }
    
    @Test
    public void physicalDeleteAllowedForAdmin() throws Exception {
        setupContext(API_STUDY_ID, ADMIN, UNCONSENTED, null);
        
        Survey survey = getSurvey(false);
        when(service.getSurvey(KEYS)).thenReturn(survey);
        
        controller.deleteSurvey(SURVEY_GUID, CREATED_ON.toString(), "true");
        
        verify(service).getSurvey(KEYS);
        verify(service).deleteSurveyPermanently(survey);
        verifyNoMoreInteractions(service);
    }
    
    @Test
    public void deleteBlockedForAdminThatDoesntAskForPhysicalDelete() throws Exception {
        setupContext(API_STUDY_ID, ADMIN, UNCONSENTED, null);
        
        Survey survey = getSurvey(false);
        when(service.getSurvey(KEYS)).thenReturn(survey);
        
        try {
            controller.deleteSurvey(SURVEY_GUID, CREATED_ON.toString(), "false");
            fail("This should have thrown an exception");
        } catch(UnauthorizedException e) {
            verify(service).getSurvey(KEYS);
            verifyNoMoreInteractions(service);
        }
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void deleteSurveyThrowsGoodExceptionIfSurveyDoesntExist() throws Exception {
        setupContext(API_STUDY_ID, DEVELOPER, UNCONSENTED, null);
        
        when(service.getSurvey(KEYS)).thenThrow(new EntityNotFoundException(Survey.class));
        
        controller.deleteSurvey(SURVEY_GUID, CREATED_ON.toString(), "false");
    }
    
    @Test
    public void cannotDeleteSurveyFromOtherStudy() throws Exception {
        setupContext(SECONDSTUDY_STUDY_ID, DEVELOPER, UNCONSENTED, null);
        
        Survey survey = getSurvey(false);
        when(service.getSurvey(KEYS)).thenReturn(survey);
        
        try {
            controller.deleteSurvey(SURVEY_GUID, CREATED_ON.toString(), "false");
            fail("Should have thrown exception");
        } catch(UnauthorizedException e) {
            verify(service).getSurvey(KEYS);
            verifyNoMoreInteractions(service);
        }
    }
    
    @Test
    public void getSurveyAllVersions() throws Exception {
        setupContext(API_STUDY_ID, DEVELOPER, UNCONSENTED, null);
        
        when(service.getSurveyAllVersions(API_STUDY_ID, SURVEY_GUID)).thenReturn(getSurveys(3, false));
        
        controller.getSurveyAllVersions(SURVEY_GUID);
        
        verify(service).getSurveyAllVersions(API_STUDY_ID, SURVEY_GUID);
        verifyNoMoreInteractions(service);
    }
    
    @Test
    public void cannotGetSurveyAllVersionsFromOtherStudy() throws Exception {
        setupContext(SECONDSTUDY_STUDY_ID, DEVELOPER, UNCONSENTED, null);
        
        when(service.getSurveyAllVersions(SECONDSTUDY_STUDY_ID, SURVEY_GUID)).thenReturn(getSurveys(3, false));
        
        try {
            controller.getSurveyAllVersions(SURVEY_GUID);
            fail("Exception should have been thrown");
        } catch(UnauthorizedException e){
            verify(service).getSurveyAllVersions(SECONDSTUDY_STUDY_ID, SURVEY_GUID);
            verifyNoMoreInteractions(service);
        }
    }
    
    @Test
    public void createSurvey() throws Exception {
        Survey survey = getSurvey(true);
        setupContext(API_STUDY_ID, DEVELOPER, UNCONSENTED, survey);
        
        survey.setGuid(BridgeUtils.generateGuid());
        survey.setVersion(1L);
        survey.setCreatedOn(DateTime.now().getMillis());
        when(service.createSurvey(any(Survey.class))).thenReturn(survey);
        
        controller.createSurvey();

        verify(service).createSurvey(any(Survey.class));
        verifyNoMoreInteractions(service);
    }
    
    // There's no such thing as not being able to create a study from another study. If
    // you create a survey, it's in your study.
    
    @Test
    public void versionSurvey() throws Exception {
        Survey survey = getSurvey(false);
        setupContext(API_STUDY_ID, DEVELOPER, UNCONSENTED, survey);
        
        when(service.getSurvey(KEYS)).thenReturn(survey);
        when(service.versionSurvey(any(Survey.class))).thenReturn(survey);
        
        controller.versionSurvey(SURVEY_GUID, CREATED_ON.toString());

        verify(service).getSurvey(KEYS);
        verify(service).versionSurvey(survey);
        verifyNoMoreInteractions(service);
    }
    
    @Test
    public void cannotVersionSurveyInOtherStudy() throws Exception {
        setupContext(SECONDSTUDY_STUDY_ID, DEVELOPER, UNCONSENTED, null);
        
        Survey survey = getSurvey(false);
        when(service.getSurvey(KEYS)).thenReturn(survey);
        when(service.versionSurvey(any(Survey.class))).thenReturn(survey);
        
        try {
            controller.versionSurvey(SURVEY_GUID, CREATED_ON.toString());
            fail("Should have thrown exception");
        } catch(UnauthorizedException e) {
            verify(service).getSurvey(KEYS);
            verifyNoMoreInteractions(service);
        }
    }
    
    @Test
    public void updateSurvey() throws Exception {
        Survey survey = getSurvey(false);
        setupContext(API_STUDY_ID, DEVELOPER, UNCONSENTED, survey);
        
        when(service.getSurvey(KEYS)).thenReturn(survey);
        when(service.updateSurvey(any(Survey.class))).thenReturn(survey);
        
        controller.updateSurvey(SURVEY_GUID, CREATED_ON.toString());
        
        verify(service).getSurvey(KEYS);
        verify(service).updateSurvey(any(Survey.class));
        verifyNoMoreInteractions(service);
    }
    
    @Test
    public void cannotUpdateSurveyFromOtheStudy() throws Exception {
        Survey survey = getSurvey(false);
        setupContext(SECONDSTUDY_STUDY_ID, DEVELOPER, UNCONSENTED, survey);
        
        when(service.getSurvey(KEYS)).thenReturn(survey);
        when(service.updateSurvey(any(Survey.class))).thenReturn(survey);
        
        try {
            controller.updateSurvey(SURVEY_GUID, CREATED_ON.toString());
            fail("Should have thrown exception");
        } catch(UnauthorizedException e) {
            verify(service).getSurvey(KEYS);
            verifyNoMoreInteractions(service);
        }
    }
    
    @Test
    public void publishSurvey() throws Exception {
        setupContext(API_STUDY_ID, DEVELOPER, UNCONSENTED, null);
        
        Survey survey = getSurvey(false);
        when(service.getSurvey(KEYS)).thenReturn(survey);
        when(service.publishSurvey(eq(TEST_STUDY), any(Survey.class), anyBoolean())).thenReturn(survey);

        controller.publishSurvey(SURVEY_GUID, CREATED_ON.toString(), null);
        
        verify(service).getSurvey(KEYS);
        verify(service).publishSurvey(TEST_STUDY, survey, false);
        verifyNoMoreInteractions(service);
    }

    @Test
    public void publishSurveyNewSchemaRev() throws Exception {
        setupContext(API_STUDY_ID, DEVELOPER, UNCONSENTED, null);

        Survey survey = getSurvey(false);
        when(service.getSurvey(KEYS)).thenReturn(survey);
        when(service.publishSurvey(eq(TEST_STUDY), any(Survey.class), anyBoolean())).thenReturn(survey);

        controller.publishSurvey(SURVEY_GUID, CREATED_ON.toString(), "true");

        verify(service).getSurvey(KEYS);
        verify(service).publishSurvey(TEST_STUDY, survey, true);
        verifyNoMoreInteractions(service);
    }

    @Test
    public void cannotPublishSurveyInOtherSurvey() throws Exception {
        setupContext(SECONDSTUDY_STUDY_ID, DEVELOPER, UNCONSENTED, null);

        Survey survey = getSurvey(false);
        when(service.getSurvey(KEYS)).thenReturn(survey);
        when(service.publishSurvey(eq(TEST_STUDY), any(Survey.class), anyBoolean())).thenReturn(survey);
        
        try {
            controller.publishSurvey(SURVEY_GUID, CREATED_ON.toString(), null);
            fail("Exception not thrown.");
        } catch(UnauthorizedException e) {
            verify(service).getSurvey(KEYS);
            verifyNoMoreInteractions(service);
        }
    }
    
    @Test
    public void cannotDeleteSurveyInOtherStudy() throws Exception {
        setupContext(SECONDSTUDY_STUDY_ID, DEVELOPER, UNCONSENTED, null);
        
        Survey survey = getSurvey(false);
        when(service.getSurvey(KEYS)).thenReturn(survey);
        
        try {
            controller.deleteSurvey(SURVEY_GUID, CREATED_ON.toString(), "false");
            fail("Exception should have been thrown.");
        } catch(UnauthorizedException e) {
            verify(service).getSurvey(KEYS);
            verifyNoMoreInteractions(service);
        }
    }

    @Test
    public void adminRejectedAsUnauthorized() throws Exception {
        setupContext(API_STUDY_ID, ADMIN, UNCONSENTED, null);
        
        Survey survey = getSurvey(false);
        when(service.getSurvey(KEYS)).thenReturn(survey);
        
        try {
            controller.getSurvey(SURVEY_GUID, CREATED_ON.toString());
            fail("Exception should have been thrown.");
        } catch(UnauthorizedException e) {
            verifyNoMoreInteractions(service);
        }
    }
    
    @Test
    public void studyParticipantRejectedAsNotConsented() throws Exception {
        setupContext(API_STUDY_ID, null, UNCONSENTED, null);

        Survey survey = getSurvey(false);
        when(service.getSurvey(KEYS)).thenReturn(survey);
        
        try {
            controller.getSurvey(SURVEY_GUID, CREATED_ON.toString());
            fail("Exception should have been thrown.");
        } catch(ConsentRequiredException e) {
            verifyNoMoreInteractions(service);
        }
    }    
    
    // In these tests, the first user successfully caches the survey, then the second request (outside the target study)
    // tries to retrieve it. This is not allowable, and throws an unauthorized exception (not a 404, though that would 
    // be another viable way to handle it).
    
    @Test
    public void cannotGetCachedSurveyByUserOfOtherStudy() throws Exception {
        setupContext(API_STUDY_ID, null, CONSENTED, null);
        when(service.getSurvey(KEYS)).thenReturn(getSurvey(false));
        
        controller.getSurveyForUser(SURVEY_GUID, CREATED_ON.toString());
        
        try {
            setupContext(SECONDSTUDY_STUDY_ID, null, CONSENTED, null);
            controller.getSurveyForUser(SURVEY_GUID, CREATED_ON.toString());
            fail("Should have thrown exception");
        } catch(UnauthorizedException e) {
            verify(service, times(2)).getSurvey(KEYS);
        }
    }

    @Test
    public void cannotGetCachedSurveyMostRecentlyPublishedVersionForUserFromOtherStudy() throws Exception {
        setupContext(API_STUDY_ID, null, CONSENTED, null);
        when(service.getSurveyMostRecentlyPublishedVersion(API_STUDY_ID, SURVEY_GUID)).thenReturn(getSurvey(false));
        
        controller.getSurveyMostRecentlyPublishedVersionForUser(SURVEY_GUID);
        
        try {
            setupContext(SECONDSTUDY_STUDY_ID, null, CONSENTED, null);
            controller.getSurveyMostRecentlyPublishedVersionForUser(SURVEY_GUID);
            fail("Should have thrown exception");
        } catch(UnauthorizedException e) {
            verify(service).getSurveyMostRecentlyPublishedVersion(API_STUDY_ID, SURVEY_GUID);
            verify(service).getSurveyMostRecentlyPublishedVersion(SECONDSTUDY_STUDY_ID, SURVEY_GUID);
        }
    }
    
    @Test
    public void cannotGetCachedSurveyFromOtherStudy() throws Exception {
        setupContext(API_STUDY_ID, DEVELOPER, UNCONSENTED, null);
        
        when(service.getSurvey(KEYS)).thenReturn(getSurvey(false));
        controller.getSurvey(SURVEY_GUID, CREATED_ON.toString());
        
        try {
            setupContext(SECONDSTUDY_STUDY_ID, DEVELOPER, UNCONSENTED, null);
            controller.getSurvey(SURVEY_GUID, CREATED_ON.toString());
            fail("Should have thrown exception");
        } catch(UnauthorizedException e) {
            verify(service, times(2)).getSurvey(KEYS);
        }
    }
    
    @Test
    public void cannotGetCachedSurveyMostRecentVersionFromOtherStudy() throws Exception {
        setupContext(API_STUDY_ID, DEVELOPER, UNCONSENTED, null);
        
        when(service.getSurveyMostRecentVersion(API_STUDY_ID, SURVEY_GUID)).thenReturn(getSurvey(false));
        when(service.getSurveyMostRecentVersion(API_STUDY_ID, SURVEY_GUID)).thenReturn(getSurvey(false));
        controller.getSurveyMostRecentVersion(SURVEY_GUID);
        
        try {
            setupContext(SECONDSTUDY_STUDY_ID, DEVELOPER, UNCONSENTED, null);
            controller.getSurveyMostRecentVersion(SURVEY_GUID);
            fail("Should have thrown exception");
        } catch(UnauthorizedException e) {
            verify(service).getSurveyMostRecentVersion(API_STUDY_ID, SURVEY_GUID);
            verify(service).getSurveyMostRecentVersion(SECONDSTUDY_STUDY_ID, SURVEY_GUID);
        }
    }
    
    @Test
    public void cannotGetCachedSurveyMostRecentlyPublishedVersionFromOtherStudy() throws Exception {
        setupContext(API_STUDY_ID, DEVELOPER, UNCONSENTED, null);
        
        when(service.getSurveyMostRecentlyPublishedVersion(API_STUDY_ID, SURVEY_GUID)).thenReturn(getSurvey(false));
        controller.getSurveyMostRecentlyPublishedVersion(SURVEY_GUID);
        
        try {
            setupContext(SECONDSTUDY_STUDY_ID, DEVELOPER, UNCONSENTED, null);
            controller.getSurveyMostRecentlyPublishedVersion(SURVEY_GUID);
            fail("Should have thrown exception");
        } catch (UnauthorizedException e) {
            verify(service).getSurveyMostRecentlyPublishedVersion(API_STUDY_ID, SURVEY_GUID);
            verify(service).getSurveyMostRecentlyPublishedVersion(SECONDSTUDY_STUDY_ID, SURVEY_GUID);
        }
    }

    @Test
    public void deleteSurveyInvalidatesCache() throws Exception {
        assertCacheIsCleared((guid, dateString) -> controller.deleteSurvey(guid, dateString, "false"));
    }
    
    @Test
    public void versionSurveyInvalidatesCache() throws Exception {
        assertCacheIsCleared((guid, dateString) -> controller.versionSurvey(guid, dateString));
    }
    
    @Test
    public void updateSurveyInvalidatesCache() throws Exception {
        assertCacheIsCleared((guid, dateString) -> controller.updateSurvey(guid, dateString));
    }
    
    @Test
    public void publishSurveyInvalidatesCache() throws Exception {
        assertCacheIsCleared((guid, dateString) -> controller.publishSurvey(guid, dateString, null));
    }
    
    @FunctionalInterface
    public interface ExecuteSurvey {
        public void execute(String guid, String dateString) throws Exception;    
    }
   
    private void assertCacheIsCleared(ExecuteSurvey executeSurvey) throws Exception {
        // Setup the cache to return content and verify the cache returns content
        Survey survey = new DynamoSurvey();
        survey.setStudyIdentifier("api");
        survey.setGuid(SURVEY_GUID);
        survey.setCreatedOn(CREATED_ON.getMillis());
        
        setupContext(API_STUDY_ID, DEVELOPER, false, survey);
        
        viewCache.getView(viewCache.getCacheKey(
                Survey.class, SURVEY_GUID, CREATED_ON.toString(), "api"), () -> { return survey; });
        
        // Verify this call hits the cache not the service
        controller.getSurvey(SURVEY_GUID, CREATED_ON.toString());
        verifyNoMoreInteractions(service);

        // Now mock the service because the *next* call (publish/delete/etc) will require it. The 
        // calls under test do not reference the cache, they clear it.
        when(service.getSurvey(any())).thenReturn(survey);
        when(service.publishSurvey(any(), any(), anyBoolean())).thenReturn(survey);
        when(service.versionSurvey(any())).thenReturn(survey);
        when(service.updateSurvey(any())).thenReturn(survey);
        
        // execute the test method, this should delete the cache
        executeSurvey.execute(SURVEY_GUID, CREATED_ON.toString());
        verify(service).getSurvey(any());
        
        // This call now hits the service, not the cache, for a total of two calls to the service
        controller.getSurvey(SURVEY_GUID, CREATED_ON.toString());
        verify(service, times(2)).getSurvey(any());
    }
    
    private Survey getSurvey(boolean makeNew) {
        Survey survey = new TestSurvey(SurveyControllerTest.class, makeNew);
        survey.setName(TestUtils.randomName(SurveyControllerTest.class));
        return survey;
    }
    
    private List<Survey> getSurveys(int count, boolean makeNew) {
        List<Survey> lists = Lists.newArrayList();
        for (int i=0; i < count; i++) {
            lists.add(getSurvey(makeNew));
        }
        return lists;
    }

}
