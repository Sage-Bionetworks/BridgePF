package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.AppConfigDao;
import org.sagebionetworks.bridge.exceptions.ConstraintViolationException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.OperatingSystem;
import org.sagebionetworks.bridge.models.appconfig.AppConfig;
import org.sagebionetworks.bridge.models.studies.Study;

import com.newrelic.agent.deps.com.google.common.collect.Lists;

@RunWith(MockitoJUnitRunner.class)
public class AppConfigServiceTest {
    
    private static final List<AppConfig>  RESULTS = Lists.newArrayList();
    private static final String  GUID = BridgeUtils.generateGuid();
    
    @Mock
    private AppConfigDao mockDao;
    
    @Mock
    private StudyService mockStudyService;
    
    @Captor
    private ArgumentCaptor<AppConfig> appConfigCaptor;

    private Study study;
    
    private AppConfig appConfig;
    
    private AppConfigService service;
    
    @Before
    public void before() {
        service = new AppConfigService();
        service.setAppConfigDao(mockDao);
        service.setStudyService(mockStudyService);
        
        appConfig = AppConfig.create();
        appConfig.setCriteria(Criteria.create());
        
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
        appConfig1.setCriteria(criteria1);
        RESULTS.add(appConfig1);
        
        Criteria criteria2 = Criteria.create();
        criteria2.setMinAppVersion(OperatingSystem.ANDROID, 6);
        criteria2.setMaxAppVersion(OperatingSystem.ANDROID, 20);
        
        AppConfig appConfig2 = AppConfig.create();
        appConfig2.setCriteria(criteria2);
        RESULTS.add(appConfig2);
        
        when(mockDao.getAppConfigs(TEST_STUDY)).thenReturn(RESULTS);
        return appConfig2;
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
        when(mockDao.getAppConfig(TEST_STUDY, GUID)).thenReturn(appConfig);
        
        AppConfig returnValue = service.getAppConfig(TEST_STUDY, GUID);
        assertEquals(appConfig, returnValue);
        
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

    @Test(expected = EntityNotFoundException.class)
    public void getAppConfigForUserThrowsException() {
        CriteriaContext context = new CriteriaContext.Builder()
                .withClientInfo(ClientInfo.fromUserAgentCache("app/21 (Motorola Flip-Phone; Android/14) BridgeJavaSDK/10"))
                .withStudyIdentifier(TEST_STUDY).build();
        
        setupConfigsForUser();
        service.getAppConfigForUser(context);
    }

    @Test(expected = ConstraintViolationException.class)
    public void getAppConfigForUserThrowsConstraintViolation() {
        CriteriaContext context = new CriteriaContext.Builder()
                .withClientInfo(ClientInfo.fromUserAgentCache("iPhone/6 (Motorola Flip-Phone; Android/14) BridgeJavaSDK/10"))
                .withStudyIdentifier(TEST_STUDY).build();
        
        setupConfigsForUser();
        service.getAppConfigForUser(context);
    }
    
    @Test
    public void createAppConfig() {
        when(mockStudyService.getStudy(TEST_STUDY)).thenReturn(study);
        when(mockDao.createAppConfig(any())).thenReturn(appConfig);
        
        AppConfig returnValue = service.createAppConfig(TEST_STUDY, appConfig);
        
        verify(mockDao).createAppConfig(appConfigCaptor.capture());
        assertEquals(appConfig, appConfigCaptor.getValue());
        
        assertEquals(returnValue, appConfig);
    }
    
    @Test
    public void updateAppConfig() {
        when(mockStudyService.getStudy(TEST_STUDY)).thenReturn(study);
        when(mockDao.updateAppConfig(any())).thenReturn(appConfig);
        
        AppConfig returnValue = service.updateAppConfig(TEST_STUDY, appConfig);
        
        verify(mockDao).updateAppConfig(appConfigCaptor.capture());
        assertEquals(appConfig, appConfigCaptor.getValue());
        
        assertEquals(returnValue, appConfig);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void createAppConfigValidates() {
        when(mockStudyService.getStudy(TEST_STUDY)).thenReturn(study);
        
        service.createAppConfig(TEST_STUDY, AppConfig.create());
    }
    
    @Test(expected = InvalidEntityException.class)
    public void updateAppConfigValidates() {
        when(mockStudyService.getStudy(TEST_STUDY)).thenReturn(study);
        
        service.updateAppConfig(TEST_STUDY, AppConfig.create());
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
