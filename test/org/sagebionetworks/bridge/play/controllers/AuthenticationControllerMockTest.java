package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.models.accounts.SignUp;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.AuthenticationService;
import org.sagebionetworks.bridge.services.StudyService;

import play.mvc.Http;
import play.mvc.Http.Context;
import play.mvc.Result;

@RunWith(MockitoJUnitRunner.class)
public class AuthenticationControllerMockTest {

    AuthenticationController controller;

    @Mock
    AuthenticationService authenticationService;
    
    @Mock
    StudyService studyService;
    
    @Captor
    ArgumentCaptor<SignUp> signUpCaptor;
    
    @Before
    public void before() {
        controller = new AuthenticationController();
        controller.setAuthenticationService(authenticationService);
        
        Study study = new DynamoStudy();
        when(studyService.getStudy("study-key")).thenReturn(study);
        controller.setStudyService(studyService);
    }
    
    @Test
    public void userCannotAssignRolesToSelfOnSignUp() throws Exception {
        Context context = TestUtils.mockPlayContextWithJson("{\"study\":\"study-key\",\"username\":\"test\",\"email\":\"bridge-testing+test@sagebase.org\",\"password\":\"P@ssword1\",\"roles\":[\"admin\"]}");
        Http.Context.current.set(context);
        
        Result result = controller.signUp();
        assertEquals(201, result.status());
        verify(authenticationService).signUp(any(), signUpCaptor.capture(), eq(true));
        
        SignUp signUp = signUpCaptor.getValue();
        assertTrue(signUp.getRoles().isEmpty());
    }
    
}
