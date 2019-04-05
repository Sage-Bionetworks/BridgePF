package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestUtils.assertResult;
import static org.sagebionetworks.bridge.TestUtils.getResponsePayload;

import java.util.List;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.cache.ViewCache;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.appconfig.AppConfig;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.AppConfigService;
import org.sagebionetworks.bridge.services.StudyService;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import play.mvc.Result;

@RunWith(MockitoJUnitRunner.class)
public class AppConfigControllerTest {
    
    private static final String TEST_UA = "Asthma/26 (Unknown iPhone; iPhone OS/9.1) BridgeSDK/4";
    private static final String TEST_LANG = "en-US,en;q=0.9";
    private static final String GUID = "guid";
    private static final CacheKey CACHE_KEY = CacheKey.appConfigList(TestConstants.TEST_STUDY);
    
    @Spy
    private AppConfigController controller;
    
    @Mock
    private AppConfigService mockService;
    
    @Mock
    private StudyService mockStudyService;
    
    @Mock
    private CacheProvider mockCacheProvider;
    
    @Mock
    private ViewCache viewCache;
    
    @Captor
    private ArgumentCaptor<CriteriaContext> contextCaptor;
    
    private Study study;
    
    private AppConfig appConfig;
    
    private UserSession session;
    
    @Before
    public void before() {
        controller.setAppConfigService(mockService);
        controller.setStudyService(mockStudyService);
        controller.setCacheProvider(mockCacheProvider);
        
        // With mock dependencies, the view cache just doesn't work (no cache hits), and tests that aren't
        // specifically verifying caching behavior pass.
        viewCache = new ViewCache();
        viewCache.setCacheProvider(mockCacheProvider);
        viewCache.setObjectMapper(BridgeObjectMapper.get());
        viewCache.setCachePeriod(100);
        controller.setViewCache(viewCache);
        
        appConfig = AppConfig.create();
        appConfig.setGuid(BridgeUtils.generateGuid());
        
        study = Study.create();
        study.setIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);
        
        session = new UserSession();
        session.setStudyIdentifier(TEST_STUDY);
        session.setParticipant(new StudyParticipant.Builder()
                .withDataGroups(Sets.newHashSet("B","A"))
                .withLanguages(ImmutableList.of("en"))
                .withRoles(Sets.newHashSet(DEVELOPER))
                .withHealthCode("healthCode")
                .build());
    }
    
    @Test
    public void getStudyAppConfig() throws Exception {
        // JSON payload here doesn't matter, it's a get request
        mockContext(TEST_UA, TEST_LANG);
        
        when(mockStudyService.getStudy(TestConstants.TEST_STUDY_IDENTIFIER)).thenReturn(study);
        when(mockService.getAppConfigForUser(contextCaptor.capture(), eq(true))).thenReturn(appConfig);
        
        Result result = controller.getStudyAppConfig("api");
        TestUtils.assertResult(result, 200);
        
        CriteriaContext capturedContext = contextCaptor.getValue();
        assertEquals(TestConstants.TEST_STUDY, capturedContext.getStudyIdentifier());
        assertEquals("Asthma", capturedContext.getClientInfo().getAppName());
        assertEquals(new Integer(26), capturedContext.getClientInfo().getAppVersion());
        assertEquals(ImmutableList.of("en"), capturedContext.getLanguages());
        assertEquals("iPhone OS", capturedContext.getClientInfo().getOsName());        
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void getAppConfigs() throws Exception {
        TestUtils.mockPlay().mock();
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        List<AppConfig> list = Lists.newArrayList(AppConfig.create(), AppConfig.create());
        when(mockService.getAppConfigs(TEST_STUDY, false)).thenReturn(list);
        
        Result result = controller.getAppConfigs("false");
        
        TestUtils.assertResult(result, 200);
        ResourceList<AppConfig> results = getResponsePayload(result, ResourceList.class);
        assertEquals(2, results.getItems().size());
        
        verify(mockService).getAppConfigs(TEST_STUDY, false);
    }
    
    @Test
    public void getAppConfig() throws Exception {
        TestUtils.mockPlay().mock();
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        when(mockService.getAppConfig(TEST_STUDY, GUID)).thenReturn(appConfig);
        
        Result result = controller.getAppConfig(GUID);

        TestUtils.assertResult(result, 200);
        AppConfig resultConfig = getResponsePayload(result, AppConfig.class);
        assertEquals(appConfig.getGuid(), resultConfig.getGuid());
        
        verify(mockService).getAppConfig(TEST_STUDY, GUID);
    }
    
    @Test
    public void createAppConfig() throws Exception {
        TestUtils.mockPlay().withBody(appConfig).mock();
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        when(mockService.createAppConfig(eq(TEST_STUDY), any())).thenReturn(appConfig);
        
        Result result = controller.createAppConfig();
        
        TestUtils.assertResult(result, 201);
        AppConfig resultConfig = getResponsePayload(result, AppConfig.class);
        assertEquals(appConfig.getGuid(), resultConfig.getGuid());
        
        verify(mockService).createAppConfig(eq(TEST_STUDY), any());
    }
    
    @Test
    public void updateAppConfig() throws Exception {
        TestUtils.mockPlay().withBody(appConfig).mock();
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        when(mockService.updateAppConfig(eq(TEST_STUDY), any())).thenReturn(appConfig);
        
        Result result = controller.updateAppConfig(GUID);
        
        TestUtils.assertResult(result, 200);
        AppConfig resultConfig = getResponsePayload(result, AppConfig.class);
        assertEquals(appConfig.getGuid(), resultConfig.getGuid());
        
        verify(mockService).updateAppConfig(eq(TEST_STUDY), any());
    }
    
    @Test
    public void deleteAppConfigDefault() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, ADMIN);

        Result result = controller.deleteAppConfig(GUID, null);
        assertResult(result, 200, "App config deleted.");
        
        verify(mockService).deleteAppConfig(TEST_STUDY, GUID);
    }
    
    @Test
    public void deleteAppConfig() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, ADMIN);
        
        Result result = controller.deleteAppConfig(GUID, "false");
        assertResult(result, 200, "App config deleted.");
        
        verify(mockService).deleteAppConfig(TEST_STUDY, GUID);
    }
    
    @Test
    public void developerCannotPermanentlyDelete() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, ADMIN);
        
        Result result = controller.deleteAppConfig(GUID, "true");
        assertResult(result, 200, "App config deleted.");
        
        verify(mockService).deleteAppConfig(TEST_STUDY, GUID);
    }
    
    @Test
    public void adminCanPermanentlyDelete() throws Exception {
        session.setParticipant(new StudyParticipant.Builder()
                .copyOf(session.getParticipant())
                .withRoles(Sets.newHashSet(DEVELOPER, ADMIN))
                .build());
        TestUtils.mockPlay().withBody(appConfig).mock();        
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, ADMIN);
        
        Result result = controller.deleteAppConfig(GUID, "true");
        assertResult(result, 200, "App config deleted.");
        
        verify(mockService).deleteAppConfigPermanently(TEST_STUDY, GUID);
    }
    
    @Test
    public void getStudyAppConfigAddsToCache() throws Exception {
        TestUtils.mockPlay()
            .withHeader("User-Agent", TEST_UA)
            .withHeader("Accept-Language", TEST_LANG)
            .withBody(appConfig).mock();
        when(mockStudyService.getStudy(TestConstants.TEST_STUDY_IDENTIFIER)).thenReturn(study);
        
        Result result = controller.getStudyAppConfig(TestConstants.TEST_STUDY_IDENTIFIER);
        TestUtils.assertResult(result, 200);
        
        verify(mockCacheProvider).addCacheKeyToSet(CACHE_KEY, "26:iPhone OS:en:api:AppConfig:view");
    }
    
    @Test
    public void createAppConfigDeletesCache() throws Exception {
        TestUtils.mockPlay().withBody(appConfig).mock();
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        when(mockService.createAppConfig(any(), any())).thenReturn(appConfig);
        
        Result result = controller.createAppConfig();
        TestUtils.assertResult(result, 201);
        
        verify(mockCacheProvider).removeSetOfCacheKeys(CACHE_KEY);
    }
    
    @Test
    public void updateAppConfigDeletesCache() throws Exception {
        TestUtils.mockPlay().withBody(appConfig).mock();
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        when(mockService.updateAppConfig(any(), any())).thenReturn(appConfig);
        
        Result result = controller.updateAppConfig("guid");
        TestUtils.assertResult(result, 200);
        
        verify(mockCacheProvider).removeSetOfCacheKeys(CACHE_KEY);
    }
    
    @Test
    public void deleteAppConfigDeletesCache() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, ADMIN);
        
        Result result = controller.deleteAppConfig("guid", null);
        TestUtils.assertResult(result, 200);
        
        verify(mockCacheProvider).removeSetOfCacheKeys(CACHE_KEY);
    }
    
    private void mockContext(String userAgent, String langs) throws Exception {
        TestUtils.mockPlay()
            .withHeader("User-Agent", userAgent)
            .withHeader("Accept-Language", langs)
            .withBody(appConfig).mock();
    }
}
