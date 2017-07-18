package org.sagebionetworks.bridge.play.controllers;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.AuthenticationService;
import org.sagebionetworks.bridge.services.StudyService;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;

import play.mvc.Result;
import play.test.Helpers;

@RunWith(MockitoJUnitRunner.class)
public class ApplicationControllerMockTest {

    @Mock
    StudyService studyService;
    
    @Mock
    AuthenticationService authenticationService;
    
    @Captor
    ArgumentCaptor<CriteriaContext> contextCaptor;
    
    @Captor
    ArgumentCaptor<SignIn> signInCaptor;
    
    @Spy
    ApplicationController controller;
    
    @Before
    public void before() {
        controller = new ApplicationController();
        controller.setStudyService(studyService);
        controller.setAuthenticationService(authenticationService);
        
        Study study = new DynamoStudy();
        study.setIdentifier("test-study");
        study.setSupportEmail("support@email.com");
        study.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);
        
        doReturn(study).when(studyService).getStudy("test-study");
    }
    
    @Test
    public void verifyEmailWorks() {
        Result result = controller.verifyEmail("test-study");
        
        verify(studyService).getStudy("test-study");
        String html = Helpers.contentAsString(result);
        assertTrue(html.contains("Your email address has now been verified."));
    }
    
    @Test
    public void resetPasswordWorks() {
        Result result = controller.resetPassword("test-study");
        
        verify(studyService).getStudy("test-study");
        String html = Helpers.contentAsString(result);
        assertTrue(html.contains("Password is required and must be entered twice."));
    }
    
    @Test
    public void startSessionWorks() throws Exception {
        Map<String,String[]> headers = new ImmutableMap.Builder<String,String[]>()
                .put("Accept-Language", new String[]{"en-US"}).build();
        TestUtils.mockPlayContextWithJson("{}", headers);
        
        UserSession session = new UserSession();
        session.setSessionToken("ABC");
        doReturn(session).when(authenticationService).emailSignIn(any(), any());
        
        Result result = controller.startSession("test-study", "email", "token");

        verify(authenticationService).emailSignIn(contextCaptor.capture(), signInCaptor.capture());
        
        JsonNode node = BridgeObjectMapper.get().readTree(Helpers.contentAsString(result));
        assertEquals("UserSessionInfo", node.get("type").asText());
        assertEquals("ABC", node.get("sessionToken").asText());

        CriteriaContext context = contextCaptor.getValue();
        assertEquals("en", context.getLanguages().iterator().next());

        SignIn signIn = signInCaptor.getValue();
        assertEquals("email", signIn.getEmail());
        assertEquals("token", signIn.getToken());
    }

}
