package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestUtils.assertResult;
import static org.sagebionetworks.bridge.TestUtils.getResponsePayload;
import static org.sagebionetworks.bridge.TestUtils.mockPlayContext;
import static org.sagebionetworks.bridge.TestUtils.mockPlayContextWithJson;

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
import org.mockito.runners.MockitoJUnitRunner;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.appconfig.AppConfig;
import org.sagebionetworks.bridge.services.AppConfigService;

import com.google.common.collect.Sets;
import com.newrelic.agent.deps.com.google.common.collect.Lists;

import play.mvc.Result;

@RunWith(MockitoJUnitRunner.class)
public class AppConfigControllerTest {
    
    private static final String GUID = "guid";
    
    @Spy
    private AppConfigController controller;
    
    @Mock
    private AppConfigService mockService;
    
    @Captor
    private ArgumentCaptor<CriteriaContext> contextCaptor;
    
    private AppConfig appConfig;
    
    @Before
    public void before() {
        controller.setAppConfigService(mockService);
        
        appConfig = AppConfig.create();
        appConfig.setGuid(BridgeUtils.generateGuid());
        
        UserSession session = new UserSession();
        session.setStudyIdentifier(TEST_STUDY);
        session.setParticipant(new StudyParticipant.Builder()
                .withRoles(Sets.newHashSet(DEVELOPER))
                .withHealthCode("healthCode")
                .build());
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        doReturn(session).when(controller).getAuthenticatedAndConsentedSession();
    }
    
    @Test
    public void getSelfAppConfig() throws Exception {
        mockPlayContext();
        when(mockService.getAppConfigForUser(any())).thenReturn(appConfig);
        
        Result result = controller.getSelfAppConfig();
        
        assertEquals(200, result.status());
        AppConfig found = getResponsePayload(result, AppConfig.class);
        assertEquals(appConfig.getGuid(), found.getGuid());
        
        verify(mockService).getAppConfigForUser(contextCaptor.capture());
        
        CriteriaContext context = contextCaptor.getValue();
        assertEquals(TEST_STUDY, context.getStudyIdentifier());
        assertEquals("healthCode", context.getHealthCode());
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void getAppConfigs() throws Exception {
        mockPlayContext();
        List<AppConfig> list = Lists.newArrayList(AppConfig.create(), AppConfig.create());
        when(mockService.getAppConfigs(TEST_STUDY)).thenReturn(list);
        
        Result result = controller.getAppConfigs();
        
        assertEquals(200, result.status());
        ResourceList<AppConfig> results = getResponsePayload(result, ResourceList.class);
        assertEquals(2, results.getItems().size());
        
        verify(mockService).getAppConfigs(TEST_STUDY);
    }
    
    @Test
    public void getAppConfig() throws Exception {
        mockPlayContext();
        when(mockService.getAppConfig(TEST_STUDY, GUID)).thenReturn(appConfig);
        
        Result result = controller.getAppConfig(GUID);

        assertEquals(200, result.status());
        AppConfig resultConfig = getResponsePayload(result, AppConfig.class);
        assertEquals(appConfig.getGuid(), resultConfig.getGuid());
        
        verify(mockService).getAppConfig(TEST_STUDY, GUID);
    }
    
    @Test
    public void createAppConfig() throws Exception {
        mockPlayContextWithJson(appConfig);
        when(mockService.createAppConfig(eq(TEST_STUDY), any())).thenReturn(appConfig);
        
        Result result = controller.createAppConfig();
        
        assertEquals(201, result.status());
        AppConfig resultConfig = getResponsePayload(result, AppConfig.class);
        assertEquals(appConfig.getGuid(), resultConfig.getGuid());
        
        verify(mockService).createAppConfig(eq(TEST_STUDY), any());
    }
    
    @Test
    public void updateAppConfig() throws Exception {
        mockPlayContextWithJson(appConfig);
        when(mockService.updateAppConfig(eq(TEST_STUDY), any())).thenReturn(appConfig);
        
        Result result = controller.updateAppConfig(GUID);
        
        assertEquals(200, result.status());
        AppConfig resultConfig = getResponsePayload(result, AppConfig.class);
        assertEquals(appConfig.getGuid(), resultConfig.getGuid());
        
        verify(mockService).updateAppConfig(eq(TEST_STUDY), any());
    }
    
    @Test
    public void deleteAppConfig() throws Exception {
        Result result = controller.deleteAppConfig(GUID);
        assertResult(result, 200, "App config deleted.");
        
        verify(mockService).deleteAppConfig(TEST_STUDY, GUID);
    }
}
