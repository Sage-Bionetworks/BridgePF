package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.mockito.Mockito.any;
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
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.OperatingSystem;
import org.sagebionetworks.bridge.models.appconfig.AppConfig;
import org.sagebionetworks.bridge.models.studies.Study;

import com.google.common.collect.Lists;

@RunWith(MockitoJUnitRunner.class)
public class AppConfigServiceTest {
    
    private static final List<AppConfig>  RESULTS = Lists.newArrayList();
    private static final String  GUID = BridgeUtils.generateGuid();
    private static final DateTime TIMESTAMP = DateTime.now();
    private static final long EARLIER_TIMESTAMP = DateTime.now().minusDays(1).getMillis();
    private static final long LATER_TIMESTAMP = DateTime.now().getMillis();
    
    @Mock
    private AppConfigDao mockDao;
    
    @Mock
    private StudyService mockStudyService;
    
    @Captor
    private ArgumentCaptor<AppConfig> appConfigCaptor;
    
    @Spy
    private AppConfigService service;

    private Study study;
    
    @Before
    public void before() {
        service.setAppConfigDao(mockDao);
        service.setStudyService(mockStudyService);
        
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
        
        AppConfig match = service.getAppConfigForUser(context, true);
        assertEquals(appConfig2, match);
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
        CriteriaContext context = new CriteriaContext.Builder()
                .withClientInfo(ClientInfo.fromUserAgentCache("iPhone/6 (Motorola Flip-Phone; Android/14) BridgeJavaSDK/10"))
                .withStudyIdentifier(TEST_STUDY).build();
        
        setupConfigsForUser();
        AppConfig appConfig = service.getAppConfigForUser(context, true);
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
