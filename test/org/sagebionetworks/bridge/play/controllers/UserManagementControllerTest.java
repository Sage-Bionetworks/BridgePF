package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyObject;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;
import static org.sagebionetworks.bridge.TestUtils.assertResult;
import static org.sagebionetworks.bridge.TestUtils.mockPlayContextWithJson;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;
import play.mvc.Http;
import play.mvc.Http.Response;
import play.mvc.Result;
import play.test.Helpers;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.AuthenticationService;
import org.sagebionetworks.bridge.services.SessionUpdateService;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.services.UserAdminService;

@RunWith(MockitoJUnitRunner.class)
public class UserManagementControllerTest {

    @Mock
    AuthenticationService authService;
    
    @Mock
    StudyService studyService;
    
    @Mock
    UserAdminService userAdminService;
    
    @Mock
    CacheProvider cacheProvider;
    
    @Mock
    BridgeConfig bridgeConfig;
    
    @Mock
    Study study;
    
    @Spy
    UserManagementController controller;
    
    @Captor
    ArgumentCaptor<SignIn> signInCaptor;
    
    private SessionUpdateService sessionUpdateService;
    
    private UserSession session;
    
    @Before
    public void before() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder()
                .withHealthCode("BBB")
                .withRoles(Sets.newHashSet(ADMIN))
                .withEmail("email@email.com").build();
                
        session = new UserSession(participant);
        session.setStudyIdentifier(TestConstants.TEST_STUDY);
        session.setAuthenticated(true);
        
        sessionUpdateService = new SessionUpdateService();
        sessionUpdateService.setCacheProvider(cacheProvider);
        
        controller.setStudyService(studyService);
        controller.setUserAdminService(userAdminService);
        controller.setAuthenticationService(authService);
        controller.setSessionUpdateService(sessionUpdateService);
        controller.setBridgeConfig(bridgeConfig);
        controller.setCacheProvider(cacheProvider);
        
        doReturn(session).when(userAdminService).createUser(
                anyObject(), anyObject(), anyObject(), anyBoolean(), anyBoolean());
        doReturn(session).when(authService).getSession(any(String.class));
        doReturn(study).when(studyService).getStudy(TestConstants.TEST_STUDY);
        doReturn(study).when(studyService).getStudy("api");
        
        when(study.getStudyIdentifier()).thenReturn(TestConstants.TEST_STUDY);

        Map<String,String[]> map = Maps.newHashMap();
        map.put(BridgeConstants.SESSION_TOKEN_HEADER, new String[]{"AAA"});
        
        mockPlayContextWithJson("{}");
        Http.Context context = Http.Context.current.get();
        Http.Request request = context.request();
        when(request.headers()).thenReturn(map);
        doReturn(null).when(controller).getMetrics();
    }

    @Test
    public void signInForAdmin() throws Exception {
        // Set environment to local in order to test that cookies are set
        when(bridgeConfig.getEnvironment()).thenReturn(Environment.LOCAL);
        
        SignIn signIn = new SignIn.Builder().withStudy("originalStudy")
                .withEmail(TestConstants.EMAIL).withPassword("password").build();
        Response response = TestUtils.mockPlayContextWithJson(signIn);

        when(authService.signIn(eq(study), any(CriteriaContext.class), signInCaptor.capture())).thenReturn(session);
        
        Result result = controller.signInForAdmin();
        TestUtils.assertResult(result, 200);
        
        // This isn't in the session that is returned to the user, but verify it has been changed
        assertEquals("originalStudy", session.getStudyIdentifier().getIdentifier());
        assertEquals("api", signInCaptor.getValue().getStudyId());
        
        verify(response).setCookie(BridgeConstants.SESSION_TOKEN_HEADER, session.getSessionToken(),
                BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS, "/",
                bridgeConfig.get("domain"), false, false);
    }
    
    @Test(expected = UnauthorizedException.class)
    public void signInForAdminNotAnAdmin() throws Exception {
        SignIn signIn = new SignIn.Builder().withStudy("originalStudy")
                .withEmail(TestConstants.EMAIL).withPassword("password").build();
        TestUtils.mockPlayContextWithJson(signIn);
        
        // But this person is actually a worker, not an admin
        session.setParticipant(new StudyParticipant.Builder().withRoles(ImmutableSet.of(Roles.WORKER)).build());
        when(authService.signIn(eq(study), any(CriteriaContext.class), signInCaptor.capture())).thenReturn(session);

        controller.signInForAdmin();
    }
    
    @Test
    public void changeStudyForAdmin() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(Roles.ADMIN);
        
        SignIn signIn = new SignIn.Builder().withStudy("nextStudy").build();
        Response response = TestUtils.mockPlayContextWithJson(signIn);
        
        Study nextStudy = Study.create();
        nextStudy.setIdentifier("nextStudy");
        when(studyService.getStudy("nextStudy")).thenReturn(nextStudy);
        
        controller.changeStudyForAdmin();
        assertEquals("nextStudy", session.getStudyIdentifier().getIdentifier());
        verify(cacheProvider).setUserSession(session);
    }
    
    @Test
    public void createdResponseReturnsJSONPayload() throws Exception {
        Result result = controller.createUser();

        TestUtils.assertResult(result, 201);
        JsonNode node = BridgeObjectMapper.get().readTree(Helpers.contentAsString(result));

        assertEquals("UserSessionInfo", node.get("type").asText());
        assertEquals("email@email.com", node.get("email").asText());
    }

    @Test
    public void createdResponseReturnsJSONPayloadWithStudyId() throws Exception {
        // same study id as above test
        Result result = controller.createUserWithStudyId(TEST_STUDY_IDENTIFIER);

        TestUtils.assertResult(result, 201);
    }
    
    @Test
    public void deleteUser() throws Exception {
        Result result = controller.deleteUser("ASDF");
        
        assertResult(result, 200, "User deleted.");
        verify(userAdminService).deleteUser(study, "ASDF");
    }
}
