package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
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
import org.mockito.junit.MockitoJUnitRunner;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.AppConfigDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;
import org.sagebionetworks.bridge.models.OperatingSystem;
import org.sagebionetworks.bridge.models.appconfig.AppConfig;
import org.sagebionetworks.bridge.models.appconfig.AppConfigElement;
import org.sagebionetworks.bridge.models.schedules.ConfigReference;
import org.sagebionetworks.bridge.models.schedules.SchemaReference;
import org.sagebionetworks.bridge.models.schedules.SurveyReference;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.upload.UploadSchema;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

@RunWith(MockitoJUnitRunner.class)
public class AppConfigServiceTest {
    
    private static final List<AppConfig>  RESULTS = Lists.newArrayList();
    private static final String  GUID = BridgeUtils.generateGuid();
    private static final DateTime TIMESTAMP = DateTime.now();
    private static final long EARLIER_TIMESTAMP = DateTime.now().minusDays(1).getMillis();
    private static final long LATER_TIMESTAMP = DateTime.now().getMillis();
    private static final List<SurveyReference> SURVEY_REF_LIST = ImmutableList
            .of(new SurveyReference(null, "guid", DateTime.now()));
    private static final List<SchemaReference> SCHEMA_REF_LIST = ImmutableList.of(new SchemaReference("id", 3));
    private static final List<ConfigReference> CONFIG_REF_LIST = ImmutableList.of(new ConfigReference("id", 1L));
    private static final GuidCreatedOnVersionHolder SURVEY_KEY = new GuidCreatedOnVersionHolderImpl(SURVEY_REF_LIST.get(0));
    
    @Mock
    private AppConfigDao mockDao;
    
    @Mock
    private StudyService mockStudyService;
    
    @Mock
    private AppConfigElementService mockAppConfigElementService;
    
    @Mock
    private SubstudyService substudyService;
    
    @Mock
    private SurveyService mockSurveyService;
    
    @Mock
    private UploadSchemaService mockSchemaService;
    
    @Mock
    private UploadSchema mockUploadSchema;
    
    @Mock
    private AppConfigElement mockConfigElement;
    
    @Mock
    private Survey mockSurvey;
    
    @Mock
    private ReferenceResolver referenceResolver;
    
    @Captor
    private ArgumentCaptor<AppConfig> appConfigCaptor;
    
    @Captor
    private ArgumentCaptor<SurveyReference> surveyRefCaptor;
    
    @Captor
    private ArgumentCaptor<SchemaReference> schemaRefCaptor;
    
    @Spy
    private AppConfigService service;

    private Study study;
    
    @Before
    public void before() {
        service.setAppConfigDao(mockDao);
        service.setStudyService(mockStudyService);
        service.setSurveyService(mockSurveyService);
        service.setUploadSchemaService(mockSchemaService);
        service.setSubstudyService(substudyService);
        service.setAppConfigElementService(mockAppConfigElementService);
        
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
     
        when(substudyService.getSubstudyIds(TEST_STUDY)).thenReturn(TestConstants.USER_SUBSTUDY_IDS);
        
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
        // Add some references to verify we call the resolver
        appConfig2.setSurveyReferences(SURVEY_REF_LIST);
        appConfig2.setSchemaReferences(SCHEMA_REF_LIST);
        RESULTS.add(appConfig2);
        
        when(mockDao.getAppConfigs(TEST_STUDY, false)).thenReturn(RESULTS);
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
        when(mockDao.getAppConfigs(TEST_STUDY, false)).thenReturn(RESULTS);
        
        List<AppConfig> results = service.getAppConfigs(TEST_STUDY, false);
        assertEquals(RESULTS, results);
        
        verify(mockDao).getAppConfigs(TEST_STUDY, false);
    }
    
    @Test
    public void getAppConfig() {
        AppConfig returnValue = service.getAppConfig(TEST_STUDY, GUID);
        assertNotNull(returnValue);
        
        verify(mockDao).getAppConfig(TEST_STUDY, GUID);
    }
    
    @Test
    public void getAppConfigForUser() {
        Survey survey = Survey.create();
        survey.setIdentifier("theIdentifier");
        survey.setGuid(SURVEY_REF_LIST.get(0).getGuid());
        survey.setCreatedOn(SURVEY_REF_LIST.get(0).getCreatedOn().getMillis());
        when(mockSurveyService.getSurvey(TestConstants.TEST_STUDY, SURVEY_KEY, false, false)).thenReturn(survey);
        
        CriteriaContext context = new CriteriaContext.Builder()
                .withClientInfo(ClientInfo.fromUserAgentCache("app/7 (Motorola Flip-Phone; Android/14) BridgeJavaSDK/10"))
                .withStudyIdentifier(TEST_STUDY).build();
        
        AppConfig appConfig2 = setupConfigsForUser();
        
        AppConfig match = service.getAppConfigForUser(context, true);
        assertEquals(appConfig2, match);
        
        // Verify that we called the resolver on this as well
        assertEquals("theIdentifier", match.getSurveyReferences().get(0).getIdentifier());
    }
    
    @Test
    public void getAppConfigForUserIncludesElements() {
        JsonNode clientData1 = TestUtils.getClientData();
        AppConfigElement element1 = AppConfigElement.create();
        element1.setId("id1");
        element1.setRevision(1L);
        element1.setData(clientData1);
        
        JsonNode clientData2 = TestUtils.getOtherClientData();
        AppConfigElement element2 = AppConfigElement.create();
        element2.setId("id2");
        element2.setRevision(2L);
        element2.setData(clientData2);

        ConfigReference ref1 = new ConfigReference("id1", 1L);
        ConfigReference ref2 = new ConfigReference("id2", 2L);
        List<ConfigReference> refs = ImmutableList.of(ref1, ref2);
        
        when(mockAppConfigElementService.getElementRevision(TEST_STUDY, "id1", 1L)).thenReturn(element1);
        when(mockAppConfigElementService.getElementRevision(TEST_STUDY, "id2", 2L)).thenReturn(element2);
        
        CriteriaContext context = new CriteriaContext.Builder()
                .withClientInfo(ClientInfo.UNKNOWN_CLIENT)
                .withStudyIdentifier(TEST_STUDY).build();
        
        AppConfig appConfig = setupConfigsForUser();
        appConfig.setSurveyReferences(null);
        appConfig.setSchemaReferences(null);
        appConfig.setConfigReferences(refs);
        
        AppConfig match = service.getAppConfigForUser(context, true);
        
        assertEquals(2, match.getConfigElements().size());
        assertEquals(clientData1, match.getConfigElements().get("id1"));
        assertEquals(clientData2, match.getConfigElements().get("id2"));
        
        // The references are still there. They list the versions being used
        assertEquals(2, match.getConfigReferences().size());
        assertEquals("id1", match.getConfigReferences().get(0).getId());
        assertEquals(new Long(1), match.getConfigReferences().get(0).getRevision());
        assertEquals("id2", match.getConfigReferences().get(1).getId());
        assertEquals(new Long(2), match.getConfigReferences().get(1).getRevision());
    }
    
    @Test
    public void getAppConfigForUserReferencingMissingElement() {
        JsonNode clientData2 = TestUtils.getOtherClientData();
        AppConfigElement element2 = AppConfigElement.create();
        element2.setId("id2");
        element2.setRevision(2L);
        element2.setData(clientData2);

        ConfigReference ref1 = new ConfigReference("id1", 1L);
        ConfigReference ref2 = new ConfigReference("id2", 2L);
        List<ConfigReference> refs = ImmutableList.of(ref1, ref2);
        
        when(mockAppConfigElementService.getElementRevision(TEST_STUDY, "id1", 1L))
                .thenThrow(new EntityNotFoundException(AppConfigElement.class));
        when(mockAppConfigElementService.getElementRevision(TEST_STUDY, "id2", 2L)).thenReturn(element2);
        
        CriteriaContext context = new CriteriaContext.Builder()
                .withClientInfo(ClientInfo.UNKNOWN_CLIENT)
                .withStudyIdentifier(TEST_STUDY).build();
        
        AppConfig appConfig = setupConfigsForUser();
        appConfig.setGuid("abc-def");
        appConfig.setConfigReferences(refs);
        
        AppConfig match = service.getAppConfigForUser(context, true);
        
        assertEquals(1, match.getConfigElements().size());
        // id1 is not included but this does not prevent id2 from being included.
        assertNull(match.getConfigElements().get("id1"));
        assertEquals(clientData2, match.getConfigElements().get("id2"));
        
        verify(service).logError("AppConfig[guid=abc-def] references missing AppConfigElement[id=id1, revision=1]");
    }

    @Test
    public void getAppConfigForUserMatchesMultipleAppConfigs() throws Exception {
        CriteriaContext context = new CriteriaContext.Builder()
                .withClientInfo(ClientInfo.UNKNOWN_CLIENT)
                .withStudyIdentifier(TEST_STUDY).build();
        
        AppConfig appConfig1 = AppConfig.create();
        appConfig1.setLabel("AppConfig1");
        appConfig1.setCriteria(Criteria.create());
        appConfig1.setCreatedOn(LATER_TIMESTAMP);
        RESULTS.add(appConfig1);
        
        AppConfig appConfig2 = AppConfig.create();
        appConfig2.setLabel("AppConfig2");
        appConfig2.setCriteria(Criteria.create());
        appConfig2.setCreatedOn(EARLIER_TIMESTAMP);
        RESULTS.add(appConfig2);
        
        when(mockDao.getAppConfigs(TEST_STUDY, false)).thenReturn(RESULTS);
        
        AppConfig appConfig = service.getAppConfigForUser(context, false);

        assertEquals(appConfig2, appConfig);
    }
    
    // This should not actually ever happen. We're suppressing exceptions if the survey is missing.
    @Test
    public void getAppConfigForUserSurveyDoesNotExist() throws Exception {
        CriteriaContext context = new CriteriaContext.Builder()
                .withClientInfo(ClientInfo.fromUserAgentCache("app/7 (Motorola Flip-Phone; Android/14) BridgeJavaSDK/10"))
                .withStudyIdentifier(TEST_STUDY).build();
        
        AppConfig appConfig2 = setupConfigsForUser();
        
        AppConfig match = service.getAppConfigForUser(context, true);
        assertEquals(appConfig2, match);
        
        assertEquals(SURVEY_REF_LIST.get(0), match.getSurveyReferences().get(0));        
    }
    
    @Test
    public void getAppConfigForUserSurveyIdentifierAlreadySet() throws Exception {
        CriteriaContext context = new CriteriaContext.Builder()
                .withClientInfo(ClientInfo.fromUserAgentCache("app/7 (Motorola Flip-Phone; Android/14) BridgeJavaSDK/10"))
                .withStudyIdentifier(TEST_STUDY).build();
        
        AppConfig appConfig2 = setupConfigsForUser();
        appConfig2.setSurveyReferences(Lists.newArrayList(new SurveyReference("anIdentifier", "guid", DateTime.now())));
        
        AppConfig match = service.getAppConfigForUser(context, true);
        
        assertEquals("anIdentifier", match.getSurveyReferences().get(0).getIdentifier());
        verify(mockSurveyService, never()).getSurvey(eq(TestConstants.TEST_STUDY), any(), eq(false), eq(true));
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void getAppConfigForUserThrowsException() {
        CriteriaContext context = new CriteriaContext.Builder()
                .withClientInfo(ClientInfo.fromUserAgentCache("app/21 (Motorola Flip-Phone; Android/14) BridgeJavaSDK/10"))
                .withStudyIdentifier(TEST_STUDY).build();
        
        setupConfigsForUser();
        service.getAppConfigForUser(context, true);
    }
    
    @Test
    public void getAppCOnfigForUserReturnsNull() {
        CriteriaContext context = new CriteriaContext.Builder()
                .withClientInfo(ClientInfo.fromUserAgentCache("app/21 (Motorola Flip-Phone; Android/14) BridgeJavaSDK/10"))
                .withStudyIdentifier(TEST_STUDY).build();
        
        setupConfigsForUser();
        AppConfig result = service.getAppConfigForUser(context, false);
        assertNull(result);
    }

    @Test
    public void getAppConfigForUserReturnsOldestVersion() {
        Survey survey = Survey.create();
        survey.setIdentifier("theIdentifier");
        survey.setGuid(SURVEY_REF_LIST.get(0).getGuid());
        survey.setCreatedOn(SURVEY_REF_LIST.get(0).getCreatedOn().getMillis());
        when(mockSurveyService.getSurvey(TestConstants.TEST_STUDY, SURVEY_KEY, false, false)).thenReturn(survey);
        
        CriteriaContext context = new CriteriaContext.Builder()
                .withClientInfo(ClientInfo.fromUserAgentCache("iPhone/6 (Motorola Flip-Phone; Android/14) BridgeJavaSDK/10"))
                .withStudyIdentifier(TEST_STUDY).build();
        
        setupConfigsForUser();
        AppConfig appConfig = service.getAppConfigForUser(context, true);
        assertEquals(EARLIER_TIMESTAMP, appConfig.getCreatedOn());
        assertEquals("theIdentifier", appConfig.getSurveyReferences().get(0).getIdentifier());
    }
    
    @Test
    public void createAppConfig() {
        when(mockStudyService.getStudy(TEST_STUDY)).thenReturn(study);
        when(mockSchemaService.getUploadSchemaByIdAndRev(any(), any(), anyInt())).thenReturn(mockUploadSchema);
        when(mockSurveyService.getSurvey(any(), any(), anyBoolean(), anyBoolean())).thenReturn(mockSurvey);
        when(mockAppConfigElementService.getElementRevision(any(), any(), anyLong())).thenReturn(mockConfigElement);
        
        when(mockSurvey.isPublished()).thenReturn(true);
        
        AppConfig newConfig = setupAppConfig();
        newConfig.setClientData(TestUtils.getClientData());
        newConfig.setSurveyReferences(SURVEY_REF_LIST);
        newConfig.setSchemaReferences(SCHEMA_REF_LIST);
        newConfig.setConfigReferences(CONFIG_REF_LIST);
        
        AppConfig returnValue = service.createAppConfig(TEST_STUDY, newConfig);
        
        assertEquals(TIMESTAMP.getMillis(), returnValue.getCreatedOn());
        assertEquals(TIMESTAMP.getMillis(), returnValue.getModifiedOn());
        assertEquals(GUID, returnValue.getGuid());
        assertEquals(newConfig.getLabel(), returnValue.getLabel()); //
        assertEquals(TEST_STUDY.getIdentifier(), returnValue.getStudyId()); //
        assertEquals(TestUtils.getClientData(), returnValue.getClientData());
        assertEquals(SURVEY_REF_LIST, returnValue.getSurveyReferences());
        assertEquals(SCHEMA_REF_LIST, returnValue.getSchemaReferences());
        assertEquals(CONFIG_REF_LIST, returnValue.getConfigReferences());
        
        verify(mockDao).createAppConfig(appConfigCaptor.capture());
        
        AppConfig captured = appConfigCaptor.getValue();
        assertEquals(TIMESTAMP.getMillis(), captured.getCreatedOn());
        assertEquals(TIMESTAMP.getMillis(), captured.getModifiedOn());
        assertEquals(GUID, captured.getGuid());
        
        verify(substudyService).getSubstudyIds(TEST_STUDY);
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
        
        verify(substudyService).getSubstudyIds(TEST_STUDY);

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
    public void deleteAppConfigPermanently() {
        service.deleteAppConfigPermanently(TEST_STUDY, GUID);
        
        verify(mockDao).deleteAppConfigPermanently(TEST_STUDY, GUID);
    }
}
