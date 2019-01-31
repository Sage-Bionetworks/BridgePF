package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.NotAuthenticatedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifierInfo;
import org.sagebionetworks.bridge.models.accounts.GeneratedPassword;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.AuthenticationService;
import org.sagebionetworks.bridge.services.ExternalIdService;
import org.sagebionetworks.bridge.services.StudyService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;

import play.mvc.Result;
import play.test.Helpers;

@RunWith(MockitoJUnitRunner.class)
public class ExternalIdControllerV4Test {

    private static final TypeReference<ForwardCursorPagedResourceList<ExternalIdentifierInfo>> REF = new TypeReference<ForwardCursorPagedResourceList<ExternalIdentifierInfo>>() {};
    
    @Mock
    private ExternalIdService mockService;
    
    @Mock
    private StudyService studyService;
    
    @Mock
    private AuthenticationService authenticationService;
    
    @Mock
    private BridgeConfig bridgeConfig;
    
    @Captor
    private ArgumentCaptor<ExternalIdentifier> externalIdCaptor;
    
    private ForwardCursorPagedResourceList<ExternalIdentifierInfo> list;
    
    private Study study;
    
    private UserSession session;
    
    @Spy
    private ExternalIdControllerV4 controller;
    
    @Before
    public void before() {
        controller.setExternalIdService(mockService);
        controller.setStudyService(studyService);
        controller.setAuthenticationService(authenticationService);
        controller.setBridgeConfig(bridgeConfig);
        
        List<ExternalIdentifierInfo> items = ImmutableList.of(new ExternalIdentifierInfo("id1", null, true),
                new ExternalIdentifierInfo("id2", null, false));
        list = new ForwardCursorPagedResourceList<>(items, "nextPageOffsetKey");
        
        study = Study.create();
        study.setIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);
        
        session = new UserSession();
        session.setStudyIdentifier(TestConstants.TEST_STUDY);
        doReturn(session).when(controller).getAuthenticatedSession(Roles.DEVELOPER, Roles.RESEARCHER);
    }
    

    @Test
    public void migrateExternalIdentifier() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(Roles.ADMIN);
        when(studyService.getStudy(TestConstants.TEST_STUDY)).thenReturn(study);
        
        TestUtils.mockPlayContextWithJson(TestUtils.createJson( 
                "{'externalId':'anExternalId','substudyId':'aSubstudyId'}"));
        
        Result result = controller.migrateExternalIdentifier();
        TestUtils.assertResult(result, 200, 
                "External ID 'anExternalId' associated to substudy 'aSubstudyId'.");
        
        verify(mockService).migrateExternalIdentifier(study, "anExternalId", "aSubstudyId");
    }
    
    @Test
    public void getExternalIdentifiers() throws Exception {
        TestUtils.mockPlayContext();
        when(mockService.getExternalIds("offsetKey", new Integer(49), "idFilter", Boolean.TRUE)).thenReturn(list);
        
        Result result = controller.getExternalIdentifiers("offsetKey", "49", "idFilter", "true");
        assertEquals(200, result.status());
        
        ForwardCursorPagedResourceList<ExternalIdentifierInfo> results = TestUtils.getResponsePayload(result, REF);
        assertEquals(2, results.getItems().size());
        
        verify(mockService).getExternalIds("offsetKey", new Integer(49), "idFilter", Boolean.TRUE);
    }
    
    @Test
    public void getExternalIdentifiersAllDefaults() throws Exception {
        TestUtils.mockPlayContext();
        when(mockService.getExternalIds(null, BridgeConstants.API_DEFAULT_PAGE_SIZE, null, null))
                .thenReturn(list);
        
        Result result = controller.getExternalIdentifiers(null, null, null, null);
        assertEquals(200, result.status());
        
        ForwardCursorPagedResourceList<ExternalIdentifierInfo> results = TestUtils.getResponsePayload(result, REF);
        assertEquals(2, results.getItems().size());
        
        verify(mockService).getExternalIds(null, BridgeConstants.API_DEFAULT_PAGE_SIZE, null, null);
    }
    
    @Test
    public void createExternalIdentifier() throws Exception {
        ExternalIdentifier extId = ExternalIdentifier.create(TestConstants.TEST_STUDY, "identifier");
        extId.setSubstudyId("substudyId");
        TestUtils.mockPlayContextWithJson(extId);
        
        Result result = controller.createExternalIdentifier();
        TestUtils.assertResult(result, 201, "External identifier created.");
        
        verify(mockService).createExternalId(externalIdCaptor.capture(), eq(false));
        
        ExternalIdentifier retrievedId = externalIdCaptor.getValue();
        assertEquals(TestConstants.TEST_STUDY.getIdentifier(), retrievedId.getStudyId());
        assertEquals("substudyId", retrievedId.getSubstudyId());
        assertEquals("identifier", retrievedId.getIdentifier());
    }
    
    @Test
    public void deleteExternalIdentifier() throws Exception {
        TestUtils.mockPlayContext();
        when(studyService.getStudy(TestConstants.TEST_STUDY)).thenReturn(study);
        
        Result result = controller.deleteExternalIdentifier("externalId");
        TestUtils.assertResult(result, 200, "External identifier deleted.");
        
        verify(mockService).deleteExternalIdPermanently(eq(study), externalIdCaptor.capture());
        assertEquals("externalId", externalIdCaptor.getValue().getIdentifier());
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, externalIdCaptor.getValue().getStudyId());
    }
    
    @Test(expected = NotAuthenticatedException.class)
    public void generatePasswordRequiresResearcher() throws Exception {
        TestUtils.mockPlayContext();
        
        controller.generatePassword("extid", false);
    }
    
    @Test
    public void generatePassword() throws Exception {
        study = new DynamoStudy();
        study.setIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);
        when(studyService.getStudy(TestConstants.TEST_STUDY)).thenReturn(study);
        
        doReturn(session).when(controller).getAuthenticatedSession(Roles.RESEARCHER);
        GeneratedPassword password = new GeneratedPassword("extid", "user-id", "some-password");
        when(authenticationService.generatePassword(study, "extid", false)).thenReturn(password);

        Result result = controller.generatePassword("extid", false);
        TestUtils.assertResult(result, 200);
        
        JsonNode node = BridgeObjectMapper.get().readTree(Helpers.contentAsString(result));
        assertEquals("extid", node.get("externalId").textValue());
        assertEquals("user-id", node.get("userId").textValue());
        assertEquals("some-password", node.get("password").textValue());
        assertEquals("GeneratedPassword", node.get("type").textValue());
        
        verify(authenticationService).generatePassword(eq(study), eq("extid"), eq(false));
    }
}
