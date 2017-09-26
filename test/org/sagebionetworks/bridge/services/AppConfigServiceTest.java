package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.AppConfigDao;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.OperatingSystem;
import org.sagebionetworks.bridge.models.appconfig.AppConfig;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.upload.UploadSchema;

import com.newrelic.agent.deps.com.google.common.collect.Lists;

@RunWith(MockitoJUnitRunner.class)
public class AppConfigServiceTest {
    
    private static final List<AppConfig>  RESULTS = Lists.newArrayList();
    private static final String  GUID = BridgeUtils.generateGuid();
    private static final DateTime TIMESTAMP = DateTime.now();
    private static final int DEFAULT_SCHEMA_REVISION = 3;
    private static final DateTime DEFAULT_SURVEY_TIMESTAMP = DateTime.now().minusDays(1);
    private static final long EARLIER_TIMESTAMP = DateTime.now().minusDays(1).getMillis();
    private static final long LATER_TIMESTAMP = DateTime.now().getMillis();
    
    @Mock
    private AppConfigDao mockDao;
    
    @Mock
    private StudyService mockStudyService;
    
    @Mock
    private SurveyService mockSurveyService;
    
    @Mock
    private UploadSchemaService mockSchemaService;
    
    @Captor
    private ArgumentCaptor<AppConfig> appConfigCaptor;
    
    @Spy
    private AppConfigService service;

    private Study study;
    
    @Before
    public void before() {
        service.setAppConfigDao(mockDao);
        service.setStudyService(mockStudyService);
        service.setSchemaService(mockSchemaService);
        service.setSurveyService(mockSurveyService);
        
        when(service.getCurrentTimestamp()).thenReturn(TIMESTAMP.getMillis());
        when(service.getGUID()).thenReturn(GUID);
        
        AppConfig savedAppConfig = AppConfig.create();
        savedAppConfig.setLabel("AppConfig");
        savedAppConfig.setGuid(GUID);
        savedAppConfig.setStudyId(TEST_STUDY.getIdentifier());
        savedAppConfig.setCriteria(Criteria.create());
        savedAppConfig.setCreatedOn(TIMESTAMP.getMillis());
        savedAppConfig.setModifiedOn(TIMESTAMP.getMillis());
        when(mockDao.getAppConfig(TEST_STUDY, GUID)).thenReturn(savedAppConfig);
        when(mockDao.updateAppConfig(any())).thenReturn(savedAppConfig);
        
        Survey defaultSurvey = Survey.create();
        defaultSurvey.setGuid("defaultSurvey");
        defaultSurvey.setCreatedOn(DEFAULT_SURVEY_TIMESTAMP.getMillis());
        defaultSurvey.setIdentifier("defaultIdentifier");
        List<Survey> surveys = Lists.newArrayList(defaultSurvey);
        
        UploadSchema defaultSchema = UploadSchema.create();
        defaultSchema.setSchemaId("defaultSchema");
        defaultSchema.setRevision(DEFAULT_SCHEMA_REVISION);
        List<UploadSchema> schemas = Lists.newArrayList(defaultSchema);
        
        // set up services to create default AppConfig.
        when(mockSurveyService.getAllSurveysMostRecentlyPublishedVersion(TEST_STUDY)).thenReturn(surveys);
        when(mockSchemaService.getUploadSchemasForStudy(TEST_STUDY)).thenReturn(schemas);
        when(mockSchemaService.getLatestUploadSchemaRevisionForAppVersion(eq(TEST_STUDY),
                eq(defaultSchema.getSchemaId()), any())).thenReturn(defaultSchema);
        
        study = Study.create();
        study.setIdentifier(TEST_STUDY.getIdentifier());
    }
    
    @After
    public void after() {
        RESULTS.clear();
    }
    
    private AppConfig setupConfigsForUser() {
        Criteria criteria1 = Criteria.create();
        criteria1.setMinAppVersion(OperatingSystem.ANDROID, 0);
        criteria1.setMaxAppVersion(OperatingSystem.ANDROID, 6);
        
        AppConfig appConfig1 = AppConfig.create();
        appConfig1.setLabel("AppConfig1");
        appConfig1.setCriteria(criteria1);
        appConfig1.setCreatedOn(LATER_TIMESTAMP);
        RESULTS.add(appConfig1);
        
        Criteria criteria2 = Criteria.create();
        criteria2.setMinAppVersion(OperatingSystem.ANDROID, 6);
        criteria2.setMaxAppVersion(OperatingSystem.ANDROID, 20);
        
        AppConfig appConfig2 = AppConfig.create();
        appConfig2.setLabel("AppConfig2");
        appConfig2.setCriteria(criteria2);
        appConfig2.setCreatedOn(EARLIER_TIMESTAMP);
        RESULTS.add(appConfig2);
        
        when(mockDao.getAppConfigs(TEST_STUDY)).thenReturn(RESULTS);
        return appConfig2;
    }
    
    private AppConfig setupAppConfig() {
        AppConfig config = AppConfig.create();
        config.setLabel("AppConfig");
        config.setCriteria(Criteria.create());
        return config;
    }
    
    @Test
    public void getAppConfigs() {
        when(mockDao.getAppConfigs(TEST_STUDY)).thenReturn(RESULTS);
        
        List<AppConfig> results = service.getAppConfigs(TEST_STUDY);
        assertEquals(RESULTS, results);
        
        verify(mockDao).getAppConfigs(TEST_STUDY);
    }
    
    @Test
    public void getAppConfig() {
        AppConfig returnValue = service.getAppConfig(TEST_STUDY, GUID);
        assertNotNull(returnValue);
        
        verify(mockDao).getAppConfig(TEST_STUDY, GUID);
    }
    
    @Test
    public void getAppConfigForUser() {
        CriteriaContext context = new CriteriaContext.Builder()
                .withClientInfo(ClientInfo.fromUserAgentCache("app/7 (Motorola Flip-Phone; Android/14) BridgeJavaSDK/10"))
                .withStudyIdentifier(TEST_STUDY).build();
        
        AppConfig appConfig2 = setupConfigsForUser();
        
        AppConfig match = service.getAppConfigForUser(context);
        assertEquals(appConfig2, match);
    }

    @Test
    public void getAppConfigForUserReturnsDefaultAppConfig() {
        CriteriaContext context = new CriteriaContext.Builder()
                .withClientInfo(ClientInfo.fromUserAgentCache("app/21 (Motorola Flip-Phone; Android/14) BridgeJavaSDK/10"))
                .withStudyIdentifier(TEST_STUDY).build();
        
        setupConfigsForUser();
        AppConfig defaultAppConfig = service.getAppConfigForUser(context);
        assertEquals((Integer)DEFAULT_SCHEMA_REVISION, defaultAppConfig.getSchemaReferences().get(0).getRevision());
        assertEquals(DEFAULT_SURVEY_TIMESTAMP.getMillis(),
                defaultAppConfig.getSurveyReferences().get(0).getCreatedOn().getMillis());
    }

    @Test
    public void getAppConfigForUserReturnsOldestVersion() {
        CriteriaContext context = new CriteriaContext.Builder()
                .withClientInfo(ClientInfo.fromUserAgentCache("iPhone/6 (Motorola Flip-Phone; Android/14) BridgeJavaSDK/10"))
                .withStudyIdentifier(TEST_STUDY).build();
        
        setupConfigsForUser();
        AppConfig appConfig = service.getAppConfigForUser(context);
        assertEquals(EARLIER_TIMESTAMP, appConfig.getCreatedOn());
    }
    
    @Test
    public void createAppConfig() {
        when(mockStudyService.getStudy(TEST_STUDY)).thenReturn(study);
        
        AppConfig newConfig = setupAppConfig();
        
        AppConfig returnValue = service.createAppConfig(TEST_STUDY, newConfig);
        assertEquals(TIMESTAMP.getMillis(), returnValue.getCreatedOn());
        assertEquals(TIMESTAMP.getMillis(), returnValue.getModifiedOn());
        assertEquals(GUID, returnValue.getGuid());
        
        verify(mockDao).createAppConfig(appConfigCaptor.capture());
        
        AppConfig captured = appConfigCaptor.getValue();
        assertEquals(TIMESTAMP.getMillis(), captured.getCreatedOn());
        assertEquals(TIMESTAMP.getMillis(), captured.getModifiedOn());
        assertEquals(GUID, captured.getGuid());
    }
    
    @Test
    public void updateAppConfig() {
        when(mockStudyService.getStudy(TEST_STUDY)).thenReturn(study);

        AppConfig oldConfig = setupAppConfig();
        oldConfig.setCreatedOn(0);
        oldConfig.setModifiedOn(0);
        oldConfig.setGuid(GUID);
        AppConfig returnValue = service.updateAppConfig(TEST_STUDY, oldConfig);
        
        assertEquals(TIMESTAMP.getMillis(), returnValue.getCreatedOn());
        assertEquals(TIMESTAMP.getMillis(), returnValue.getModifiedOn());
        
        verify(mockDao).updateAppConfig(appConfigCaptor.capture());
        assertEquals(oldConfig, appConfigCaptor.getValue());

        assertEquals(returnValue, oldConfig);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void createAppConfigValidates() {
        when(mockStudyService.getStudy(TEST_STUDY)).thenReturn(study);
        
        service.createAppConfig(TEST_STUDY, AppConfig.create());
    }
    
    @Test(expected = InvalidEntityException.class)
    public void updateAppConfigValidates() {
        when(mockStudyService.getStudy(TEST_STUDY)).thenReturn(study);
        
        AppConfig oldConfig = setupAppConfig();
        service.updateAppConfig(TEST_STUDY, oldConfig);
    }
    
    @Test
    public void deleteAppConfig() {
        service.deleteAppConfig(TEST_STUDY,  GUID);
        
        verify(mockDao).deleteAppConfig(TEST_STUDY, GUID);
    }
    
    @Test
    public void deleteAllAppConfigs() {
        service.deleteAllAppConfigs(TEST_STUDY);
        
        verify(mockDao).deleteAllAppConfigs(TEST_STUDY);
    }

}
