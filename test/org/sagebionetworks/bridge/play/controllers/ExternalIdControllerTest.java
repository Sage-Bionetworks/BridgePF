package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestUtils.assertResult;

import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.NotAuthenticatedException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifierInfo;
import org.sagebionetworks.bridge.models.accounts.GeneratedPassword;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.AuthenticationService;
import org.sagebionetworks.bridge.services.ExternalIdService;
import org.sagebionetworks.bridge.services.StudyService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import play.mvc.Http;
import play.mvc.Result;
import play.test.Helpers;

@RunWith(MockitoJUnitRunner.class)
public class ExternalIdControllerTest {
    
    private static final BridgeObjectMapper MAPPER = BridgeObjectMapper.get();
    
    private static final List<ExternalIdentifierInfo> EXT_IDS = Lists.newArrayList(
            new ExternalIdentifierInfo("AAA", null, false), new ExternalIdentifierInfo("BBB",  null, false),
            new ExternalIdentifierInfo("CCC", null, false));
    
    private static final TypeReference<ForwardCursorPagedResourceList<ExternalIdentifierInfo>> PAGE_REF =
            new TypeReference<ForwardCursorPagedResourceList<ExternalIdentifierInfo>>() {};
    
    @Mock
    ExternalIdService externalIdService;
    
    @Mock
    StudyService studyService;
    
    @Mock
    AuthenticationService authenticationService;
    
    @Mock
    UserSession session;
    
    @Mock
    BridgeConfig bridgeConfig;
    
    @Captor
    ArgumentCaptor<List<String>> externalIdCaptor;
    
    Study study;
    
    ExternalIdController controller;
    
    @Before
    public void before() throws Exception {
        controller = spy(new ExternalIdController());
        controller.setExternalIdService(externalIdService);
        controller.setStudyService(studyService);
        controller.setAuthenticationService(authenticationService);
        controller.setBridgeConfig(bridgeConfig);
        
        when(bridgeConfig.getEnvironment()).thenReturn(Environment.UAT);
        
        StudyParticipant participant = new StudyParticipant.Builder()
                .withHealthCode("BBB").build();
        
        when(session.getParticipant()).thenReturn(participant);
        when(session.getStudyIdentifier()).thenReturn(TestConstants.TEST_STUDY);
        
        doReturn(session).when(controller).getAuthenticatedSession(Roles.DEVELOPER);

        study = new DynamoStudy();
        study.setIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);
        when(studyService.getStudy(TestConstants.TEST_STUDY)).thenReturn(study);
        
        TestUtils.mockPlayContext();
    }

    @Test
    public void getExternalIds() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(Roles.DEVELOPER, Roles.RESEARCHER);
        // Mock out a response from service
        ForwardCursorPagedResourceList<ExternalIdentifierInfo> page = new ForwardCursorPagedResourceList<>(EXT_IDS, "CCC")
                .withRequestParam(ResourceList.PAGE_SIZE, 5).withRequestParam(ResourceList.ID_FILTER, "A");
        when(externalIdService.getExternalIds(any(), any(), any(), any())).thenReturn(page);
        
        // execute the controller
        Result result = controller.getExternalIds(null, null, null, null);
        assertResult(result, 200);
        String content = Helpers.contentAsString(result);

        ForwardCursorPagedResourceList<ExternalIdentifierInfo> deserPage =  MAPPER.readValue(content, PAGE_REF);
        assertEquals(EXT_IDS, deserPage.getItems());
        assertEquals("CCC", deserPage.getNextPageOffsetKey());
        assertEquals(5, deserPage.getRequestParams().get("pageSize"));
        assertEquals("A", deserPage.getRequestParams().get("idFilter"));
    }
    
    @Test
    public void addExternalIds() throws Exception {
        List<String> identifiers = Lists.newArrayList("AAA", "BBB", "CCC");
        TestUtils.mockPlayContextWithJson(MAPPER.writeValueAsString(identifiers));
        
        Result result = controller.addExternalIds();
        assertResult(result, 201, "External identifiers added.");
        
        verify(externalIdService).createExternalId(
                ExternalIdentifier.create(study.getStudyIdentifier(), "AAA"), true);
        verify(externalIdService).createExternalId(
                ExternalIdentifier.create(study.getStudyIdentifier(), "BBB"), true);
        verify(externalIdService).createExternalId(
                ExternalIdentifier.create(study.getStudyIdentifier(), "CCC"), true);
    }
    
    @Test
    public void noIdentifiers() throws Exception {
        TestUtils.mockPlayContextWithJson("[]");
        
        Result result = controller.addExternalIds();
        assertResult(result, 201, "External identifiers added.");
        
        verify(externalIdService, never()).createExternalId(any(), eq(true));        
    }
    
    @Test
    public void deleteIdentifiers() throws Exception {
        Map<String,String[]> map = Maps.newHashMap();
        String[] identifiers = new String[] {"AAA","BBB","CCC"};
        map.put("externalId", identifiers);
        mockRequestWithQueryString(map);
        
        Result result = controller.deleteExternalIds();
        assertResult(result, 200, "External identifiers deleted.");
        
        verify(externalIdService).deleteExternalIdPermanently(study, 
                ExternalIdentifier.create(study.getStudyIdentifier(), "AAA"));
        verify(externalIdService).deleteExternalIdPermanently(study, 
                ExternalIdentifier.create(study.getStudyIdentifier(), "BBB"));
        verify(externalIdService).deleteExternalIdPermanently(study, 
                ExternalIdentifier.create(study.getStudyIdentifier(), "CCC"));
    }
    
    @Test(expected = BadRequestException.class)
    public void deleteIdentifiersNoIdentifiers() throws Exception {
        Map<String,String[]> map = Maps.newHashMap();
        String[] identifiers = new String[] {};
        map.put("externalId", identifiers);
        mockRequestWithQueryString(map);
        
        controller.deleteExternalIds();
    }
    
    @Test(expected = BadRequestException.class)
    public void deleteIdentifiersNoneInQueryAtAll() throws Exception {
        Map<String,String[]> map = Maps.newHashMap();
        mockRequestWithQueryString(map);
        
        controller.deleteExternalIds();
    }
    
    @Test(expected = NotAuthenticatedException.class)
    public void generatePasswordRequiresResearcher() throws Exception {
        when(controller.getAuthenticatedSession(Roles.RESEARCHER)).thenThrow(new UnauthorizedException());
        
        controller.generatePassword("extid", false);
    }
    
    @Test
    public void generatePassword() throws Exception {
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
    
    private void mockRequestWithQueryString(Map<String,String[]> query) {
        Http.Request request = mock(Http.Request.class);
        
        Http.Context context = mock(Http.Context.class);
        when(context.request()).thenReturn(request);
        
        when(request.queryString()).thenReturn(query);

        Http.Context.current.set(context);
    }
    
}
