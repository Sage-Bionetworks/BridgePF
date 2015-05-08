package controllers;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.cache.ViewCache;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.TestSurvey;
import org.sagebionetworks.bridge.services.SurveyService;

import play.mvc.Http;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * We know this controller works given the integration tests. Here I'm interested in finding a way 
 * to test cross-study security that won't take another hour in test time to run. Creating a second study, 
 * etc. through integration tests is very slow.
 */
public class SurveyControllerTest {

    private SurveyController controller;
    
    private SurveyService service;
    
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
        }).when(provider).setString(anyString(), anyString());
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                String key = invocation.getArgumentAt(0, String.class);
                cacheMap.remove(key);
                return null;
            }
        }).when(provider).removeString(anyString());
        viewCache.setCacheProvider(provider);
        
        controller = spy(new SurveyController());
        controller.setSurveyService(service);
        controller.setViewCache(viewCache);
        
        setUserSession("api");
    }
    
    private void setContext() throws Exception {
        Http.Context context = TestUtils.mockPlayContext();
        Http.Context.current.set(context);
    }
    
    private void setContext(Object object) throws Exception {
        String json = BridgeObjectMapper.get().writeValueAsString(object);
        Http.Context context = TestUtils.mockPlayContextWithJson(json);
        Http.Context.current.set(context);
    }
    
    private void setUserSession(String studyIdentifier) {
        // The mock user is initially
        session = new UserSession();
        User user = new User();
        user.setHealthCode("BBB");
        user.setStudyKey(studyIdentifier);
        user.setRoles(Sets.newHashSet("api_researcher"));
        session.setUser(user);
        session.setStudyIdentifier(new StudyIdentifierImpl(studyIdentifier));
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
        doReturn(session).when(controller).getAuthenticatedSession();
        doReturn(session).when(controller).getAuthenticatedResearcherSession();
    }
    
    @Test
    public void getAllSurveysMostRecentVersion2() throws Exception {
        setContext();
        when(service.getAllSurveysMostRecentVersion(any(StudyIdentifier.class))).thenReturn(getSurveys(3, false));
        
        controller.getAllSurveysMostRecentVersion2();
        
        verify(service).getAllSurveysMostRecentVersion(any(StudyIdentifier.class));
        verifyNoMoreInteractions(service);
    }
    
    @Test
    public void cannotGetSurveysInOtherStudy() throws Exception {
        setContext();
        when(service.getAllSurveysMostRecentVersion(any(StudyIdentifier.class))).thenReturn(getSurveys(3, false));
        setUserSession("secondstudy");

        try {
            controller.getAllSurveysMostRecentVersion2();
            fail("Should have thrown exception");
        } catch(UnauthorizedException e) {
            verify(service).getAllSurveysMostRecentVersion(any(StudyIdentifier.class));
            verifyNoMoreInteractions(service);
        }
    }
    
    @Test
    public void getAllSurveysMostRecentlyPublishedVersion() throws Exception {
        setContext();
        when(service.getAllSurveysMostRecentlyPublishedVersion(any(StudyIdentifier.class))).thenReturn(getSurveys(2, false));
        
        controller.getAllSurveysMostRecentlyPublishedVersion();
        
        verify(service).getAllSurveysMostRecentlyPublishedVersion(any(StudyIdentifier.class));
        verifyNoMoreInteractions(service);
    }
    
    @Test
    public void cannotGetAllSurveysMostRecentlyPublishedVersionInOtherStudy() throws Exception {
        setContext();
        when(service.getAllSurveysMostRecentlyPublishedVersion(any(StudyIdentifier.class))).thenReturn(getSurveys(2, false));
        setUserSession("secondstudy");

        try {
            controller.getAllSurveysMostRecentlyPublishedVersion();
            fail("Should have thrown exception");
        } catch(UnauthorizedException e) {
            verify(service).getAllSurveysMostRecentlyPublishedVersion(any(StudyIdentifier.class));
            verifyNoMoreInteractions(service);
        }
    }
    
    @Test
    public void getSurveyForUser() throws Exception {
        setContext();
        when(service.getSurvey(any(GuidCreatedOnVersionHolder.class))).thenReturn(getSurvey(false));
        
        long datetime = DateTime.now().getMillis();
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl("bbb", datetime);
        
        controller.getSurvey("bbb", new DateTime(datetime).toString());
        
        verify(service).getSurvey(eq(keys));
        verifyNoMoreInteractions(service);
    }

    @Test
    public void cannotGetSurveyForUserInOtherStudy() throws Exception {
        setContext();
        when(service.getSurvey(any(GuidCreatedOnVersionHolder.class))).thenReturn(getSurvey(false));
        setUserSession("secondstudy");

        long datetime = DateTime.now().getMillis();
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl("bbb", datetime);
        try {
            controller.getSurvey("bbb", new DateTime(datetime).toString());
            fail("Should have thrown exception");
        } catch(UnauthorizedException e) {
            verify(service).getSurvey(eq(keys));
            verifyNoMoreInteractions(service);
        }
    }
    
    @Test
    public void getSurveyMostRecentlyPublishedVersionForUser() throws Exception {
        setContext();
        when(service.getSurveyMostRecentlyPublishedVersion(any(StudyIdentifier.class), anyString())).thenReturn(getSurvey(false));
        
        StudyIdentifier studyId = new StudyIdentifierImpl("api");
        controller.getSurveyMostRecentlyPublishedVersionForUser("bbb");
        
        verify(service).getSurveyMostRecentlyPublishedVersion(eq(studyId), eq("bbb"));
        verifyNoMoreInteractions(service);
    }
    
    @Test
    public void cannotGetSurveyMostRecentlyPublishedVersionForUserFromOtherStudy() throws Exception {
        setContext();
        when(service.getSurveyMostRecentlyPublishedVersion(any(StudyIdentifier.class), anyString())).thenReturn(getSurvey(false));
        setUserSession("secondstudy");
        
        StudyIdentifier studyId = new StudyIdentifierImpl("secondstudy");
        try {
            controller.getSurveyMostRecentlyPublishedVersionForUser("bbb");
            fail("Should have thrown exception");
        } catch(UnauthorizedException e) {
            verify(service).getSurveyMostRecentlyPublishedVersion(eq(studyId), eq("bbb"));
            verifyNoMoreInteractions(service);
        }
    }
    
    @Test
    public void getSurvey() throws Exception {
        String guid = "BBB";
        String dateString = DateTime.now().toString();
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(guid, DateTime.parse(dateString).getMillis());
        
        setContext();
        when(service.getSurvey(eq(keys))).thenReturn(getSurvey(false));
        
        controller.getSurvey(guid, dateString);
        
        verify(service).getSurvey(eq(keys));
        verifyNoMoreInteractions(service);
    }
    
    @Test
    public void cannotGetSurveyFromOtherStudy() throws Exception {
        String guid = "BBB";
        String dateString = DateTime.now().toString();
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(guid, DateTime.parse(dateString).getMillis());
        
        setContext();
        when(service.getSurvey(eq(keys))).thenReturn(getSurvey(false));
        setUserSession("secondstudy");
        
        try {
            controller.getSurvey(guid, dateString);
            fail("Should have thrown exception");
        } catch(UnauthorizedException e) {
            verify(service).getSurvey(eq(keys));
            verifyNoMoreInteractions(service);
        }
    }
    
    @Test
    public void getSurveyMostRecentVersion() throws Exception {
        StudyIdentifier studyId = new StudyIdentifierImpl("api");
        String guid = "BBB";
        setContext();
        when(service.getSurveyMostRecentVersion(eq(studyId), eq(guid))).thenReturn(getSurvey(false));
        
        controller.getSurveyMostRecentVersion(guid);
        
        verify(service).getSurveyMostRecentVersion(eq(studyId), eq(guid));
        verifyNoMoreInteractions(service);
    }

    @Test
    public void cannotGetSurveyMostRecentVersionFromOtherStudy() throws Exception {
        // It's not possible to query for surveys in another study... the study comes
        // from the session. But if that somehow returned a survey in another study, 
        // it would still throw an exception.
        StudyIdentifier studyId = new StudyIdentifierImpl("secondstudy");
        String guid = "BBB";
        setContext();
        when(service.getSurveyMostRecentVersion(eq(studyId), eq(guid))).thenReturn(getSurvey(false));
        setUserSession("secondstudy");
        
        try {
            controller.getSurveyMostRecentVersion(guid);
            fail("Should have thrown exception");
        } catch(UnauthorizedException e) {
            verify(service).getSurveyMostRecentVersion(eq(studyId), eq(guid));
            verifyNoMoreInteractions(service);
        }
    }
    
    @Test
    public void getSurveyMostRecentlyPublishedVersion() throws Exception {
        StudyIdentifier studyId = new StudyIdentifierImpl("api");
        String guid = "BBB";
        setContext();
        when(service.getSurveyMostRecentlyPublishedVersion(eq(studyId), eq(guid))).thenReturn(getSurvey(false));
        
        controller.getSurveyMostRecentlyPublishedVersion(guid);
        
        verify(service).getSurveyMostRecentlyPublishedVersion(eq(studyId), eq(guid));
        verifyNoMoreInteractions(service);
    }
    
    @Test
    public void cannotGetSurveyMostRecentlyPublishedVersionFromOtherStudy() throws Exception {
        StudyIdentifier studyId = new StudyIdentifierImpl("secondstudy");
        String guid = "BBB";
        setContext();
        when(service.getSurveyMostRecentlyPublishedVersion(eq(studyId), eq(guid))).thenReturn(getSurvey(false));
        setUserSession("secondstudy");
        
        try {
            controller.getSurveyMostRecentlyPublishedVersion(guid);
            fail("Should have thrown exception");
        } catch(UnauthorizedException e) {
            verify(service).getSurveyMostRecentlyPublishedVersion(eq(studyId), eq(guid));
            verifyNoMoreInteractions(service);
        }
    }
    
    @Test
    public void getMostRecentPublishedSurveyVersionByIdentifier() throws Exception {
        StudyIdentifier studyId = new StudyIdentifierImpl("api");
        String identifier = BridgeUtils.generateGuid();
        setContext();
        when(service.getSurveyMostRecentlyPublishedVersionByIdentifier(eq(studyId), eq(identifier))).thenReturn(getSurvey(false));
        
        controller.getMostRecentPublishedSurveyVersionByIdentifier(identifier);
        
        verify(service).getSurveyMostRecentlyPublishedVersionByIdentifier(eq(studyId), eq(identifier));
        verifyNoMoreInteractions(service);
    }
    
    @Test
    public void cannotGetMostRecentPublishedSurveyVersionByIdentifierFromOtherStudy() throws Exception {
        StudyIdentifier studyId = new StudyIdentifierImpl("secondstudy");
        String identifier = BridgeUtils.generateGuid();
        setContext();
        when(service.getSurveyMostRecentlyPublishedVersionByIdentifier(eq(studyId), eq(identifier))).thenReturn(getSurvey(false));
        setUserSession("secondstudy");
        
        try {
            controller.getMostRecentPublishedSurveyVersionByIdentifier(identifier);
            fail("Should have thrown exception");
        } catch(UnauthorizedException e) {
            verify(service).getSurveyMostRecentlyPublishedVersionByIdentifier(eq(studyId), eq(identifier));
            verifyNoMoreInteractions(service);
        }
    }
    
    @Test
    public void deleteSurvey() throws Exception {
        StudyIdentifier studyId = new StudyIdentifierImpl("api");
        String guid = BridgeUtils.generateGuid();
        DateTime date = DateTime.now();
        Survey survey = getSurvey(false);
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(guid, date.getMillis());
        when(service.getSurvey(eq(keys))).thenReturn(survey);
        setContext();
        
        controller.deleteSurvey(guid, date.toString());

        verify(service).getSurvey(eq(keys));
        verify(service).deleteSurvey(eq(studyId), eq(survey));
        verifyNoMoreInteractions(service);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void deleteSurveyThrowsGoodExceptionIfSurveyDoesntExist() throws Exception {
        String guid = BridgeUtils.generateGuid();
        DateTime date = DateTime.now();
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(guid, date.getMillis());
        when(service.getSurvey(eq(keys))).thenThrow(new EntityNotFoundException(Survey.class));
        setContext();
        
        controller.deleteSurvey(guid, date.toString());
    }
    
    @Test
    public void cannotDeleteSurveyFromOtherStudy() throws Exception {
        String guid = BridgeUtils.generateGuid();
        DateTime date = DateTime.now();
        Survey survey = getSurvey(false);
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(guid, date.getMillis());
        when(service.getSurvey(eq(keys))).thenReturn(survey);
        setContext();
        setUserSession("secondstudy");
        
        try {
            controller.deleteSurvey(guid, date.toString());
            fail("Should have thrown exception");
        } catch(UnauthorizedException e) {
            verify(service).getSurvey(eq(keys));
            verifyNoMoreInteractions(service);
        }
    }
    
    @Test
    public void getSurveyAllVersions() throws Exception {
        StudyIdentifier studyId = new StudyIdentifierImpl("api");
        String guid = BridgeUtils.generateGuid();
        when(service.getSurveyAllVersions(eq(studyId), eq(guid))).thenReturn(getSurveys(3, false));
        setContext();
        
        controller.getSurveyAllVersions(guid);
        
        verify(service).getSurveyAllVersions(eq(studyId), eq(guid));
        verifyNoMoreInteractions(service);
    }
    
    @Test
    public void cannotGetSurveyAllVersionsFromOtherStudy() throws Exception {
        StudyIdentifier studyId = new StudyIdentifierImpl("secondstudy");
        String guid = BridgeUtils.generateGuid();
        when(service.getSurveyAllVersions(eq(studyId), eq(guid))).thenReturn(getSurveys(3, false));
        setContext();
        setUserSession("secondstudy");
        
        try {
            controller.getSurveyAllVersions(guid);
            fail("Exception should have been thrown");
        } catch(UnauthorizedException e){
            verify(service).getSurveyAllVersions(eq(studyId), eq(guid));
            verifyNoMoreInteractions(service);
        }
    }
    
    @Test
    public void createSurvey() throws Exception {
        Survey survey = getSurvey(true);
        setContext(survey);
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
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(survey.getGuid(), survey.getCreatedOn());
        when(service.getSurvey(eq(keys))).thenReturn(survey);
        when(service.versionSurvey(any(Survey.class))).thenReturn(survey);
        
        controller.versionSurvey(survey.getGuid(), new DateTime(survey.getCreatedOn()).toString());

        verify(service).getSurvey(eq(keys));
        verify(service).versionSurvey(eq(survey));
        verifyNoMoreInteractions(service);
    }
    
    @Test
    public void cannotVersionSurveyInOtherStudy() throws Exception {
        Survey survey = getSurvey(false);
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(survey.getGuid(), survey.getCreatedOn());
        when(service.getSurvey(eq(keys))).thenReturn(survey);
        when(service.versionSurvey(any(Survey.class))).thenReturn(survey);
        setUserSession("secondstudy");
        
        try {
            controller.versionSurvey(survey.getGuid(), new DateTime(survey.getCreatedOn()).toString());
            fail("Should have thrown exception");
        } catch(UnauthorizedException e) {
            verify(service).getSurvey(eq(keys));
            verifyNoMoreInteractions(service);
        }
    }
    
    @Test
    public void updateSurvey() throws Exception {
        Survey survey = getSurvey(false);
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(survey.getGuid(), survey.getCreatedOn());
        when(service.getSurvey(eq(keys))).thenReturn(survey);
        when(service.updateSurvey(any(Survey.class))).thenReturn(survey);
        setContext(survey);
        
        controller.updateSurvey(survey.getGuid(), new DateTime(survey.getCreatedOn()).toString());
        
        verify(service).getSurvey(eq(keys));
        verify(service).updateSurvey(any(Survey.class));
        verifyNoMoreInteractions(service);
    }
    
    @Test
    public void cannotUpdateSurveyFromOtheStudy() throws Exception {
        Survey survey = getSurvey(false);
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(survey.getGuid(), survey.getCreatedOn());
        when(service.getSurvey(eq(keys))).thenReturn(survey);
        when(service.updateSurvey(any(Survey.class))).thenReturn(survey);
        setContext(survey);
        setUserSession("secondstudy");
        
        try {
            controller.updateSurvey(survey.getGuid(), new DateTime(survey.getCreatedOn()).toString());
            fail("Should have thrown exception");
        } catch(UnauthorizedException e) {
            verify(service).getSurvey(eq(keys));
            verifyNoMoreInteractions(service);
        }
    }
    
    @Test
    public void publishSurvey() throws Exception {
        Survey survey = getSurvey(false);
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(survey.getGuid(), survey.getCreatedOn());
        setContext();
        when(service.getSurvey(keys)).thenReturn(survey);
        when(service.publishSurvey(any(Survey.class))).thenReturn(survey);
        
        controller.publishSurvey(keys.getGuid(), new DateTime(keys.getCreatedOn()).toString());
        
        verify(service).getSurvey(keys);
        verify(service).publishSurvey(any(Survey.class));
        verifyNoMoreInteractions(service);
    }
    
    @Test
    public void cannotPublishSurveyInOtherSurvey() throws Exception {
        Survey survey = getSurvey(false);
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(survey.getGuid(), survey.getCreatedOn());
        setContext();
        when(service.getSurvey(keys)).thenReturn(survey);
        when(service.publishSurvey(any(Survey.class))).thenReturn(survey);
        setUserSession("secondstudy");
        
        try {
            controller.publishSurvey(keys.getGuid(), new DateTime(keys.getCreatedOn()).toString());
            fail("Exception not thrown.");
        } catch(UnauthorizedException e) {
            verify(service).getSurvey(keys);
            verifyNoMoreInteractions(service);
        }
    }
    
    @Test
    public void closeSurvey() throws Exception {
        Survey survey = getSurvey(false);
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(survey.getGuid(), survey.getCreatedOn());
        setContext();
        when(service.getSurvey(keys)).thenReturn(survey);
        
        controller.closeSurvey(keys.getGuid(), new DateTime(keys.getCreatedOn()).toString());
        
        verify(service).getSurvey(keys);
        verify(service).closeSurvey(any(Survey.class));
        verifyNoMoreInteractions(service);
    }
    
    @Test
    public void cannotCloseSurveyInOtherStudy() throws Exception {
        Survey survey = getSurvey(false);
        GuidCreatedOnVersionHolder keys = new GuidCreatedOnVersionHolderImpl(survey.getGuid(), survey.getCreatedOn());
        setContext();
        when(service.getSurvey(keys)).thenReturn(survey);
        setUserSession("secondstudy");
        
        try {
            controller.closeSurvey(keys.getGuid(), new DateTime(keys.getCreatedOn()).toString());
            fail("Exception should have been thrown.");
        } catch(UnauthorizedException e) {
            verify(service).getSurvey(keys);
            verifyNoMoreInteractions(service);
        }
    }
    
    private Survey getSurvey(boolean makeNew) {
        Survey survey = new TestSurvey(makeNew);
        survey.setName("bloodpressure " + survey.getGuid());
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
