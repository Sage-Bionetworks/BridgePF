package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.EnumSet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.http.HttpStatus;
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
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.exceptions.NotAuthenticatedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.Metrics;
import org.sagebionetworks.bridge.models.accounts.EmailVerification;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.SignUp;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.services.AuthenticationService;
import org.sagebionetworks.bridge.services.StudyService;

import play.mvc.Http;
import play.mvc.Http.Context;
import play.mvc.Result;
import play.test.Helpers;

@RunWith(MockitoJUnitRunner.class)
public class AuthenticationControllerMockTest {
    private static final String TEST_INTERNAL_SESSION_ID = "internal-session-id";
    private static final String TEST_PASSWORD = "password";
    private static final String TEST_USER_STORMPATH_ID = "spId";
    private static final String TEST_USERNAME = "username";
    private static final String TEST_REQUEST_ID = "request-id";
    private static final String TEST_SESSION_TOKEN = "session-token";
    private static final String TEST_STUDY_ID_STRING = "study-key";
    private static final StudyIdentifier TEST_STUDY_ID = new StudyIdentifierImpl(TEST_STUDY_ID_STRING);
    private static final String TEST_VERIFY_EMAIL_TOKEN = "verify-email-token";

    AuthenticationController controller;

    @Mock
    AuthenticationService authenticationService;

    private Study study;
    
    @Mock
    StudyService studyService;
    
    @Captor
    ArgumentCaptor<SignUp> signUpCaptor;
    
    @Before
    public void before() {
        controller = spy(new AuthenticationController());
        controller.setAuthenticationService(authenticationService);
        
        study = new DynamoStudy();
        when(studyService.getStudy(TEST_STUDY_ID_STRING)).thenReturn(study);
        controller.setStudyService(studyService);
    }
    
    @Test
    public void userCannotAssignRolesToSelfOnSignUp() throws Exception {
        Context context = TestUtils.mockPlayContextWithJson("{\"study\":\"study-key\",\"username\":\"test\",\"email\":\"bridge-testing+test@sagebase.org\",\"password\":\"P@ssword1\",\"roles\":[\"admin\"]}");
        Http.Context.current.set(context);
        
        Result result = controller.signUp();
        assertEquals(201, result.status());
        verify(authenticationService).signUp(same(study), signUpCaptor.capture(), eq(true));
        
        SignUp signUp = signUpCaptor.getValue();
        assertTrue(signUp.getRoles().isEmpty());
    }

    @Test
    public void getSessionIfItExistsNullToken() {
        doReturn(null).when(controller).getSessionToken();
        assertNull(controller.getSessionIfItExists());
    }

    @Test
    public void getSessionIfItExistsEmptyToken() {
        doReturn("").when(controller).getSessionToken();
        assertNull(controller.getSessionIfItExists());
    }

    @Test
    public void getSessionIfItExistsBlankToken() {
        doReturn("   ").when(controller).getSessionToken();
        assertNull(controller.getSessionIfItExists());
    }

    @Test
    public void getSessionIfItExistsSuccess() {
        // mock getSessionToken and getMetrics
        doReturn(TEST_SESSION_TOKEN).when(controller).getSessionToken();

        Metrics metrics = new Metrics(TEST_REQUEST_ID);
        doReturn(metrics).when(controller).getMetrics();

        // mock AuthenticationService
        UserSession session = createSession();
        when(authenticationService.getSession(TEST_SESSION_TOKEN)).thenReturn(session);

        // execute and validate
        UserSession retVal = controller.getSessionIfItExists();
        assertSame(session, retVal);
        assertSessionInfoInMetrics(metrics);
    }

    @Test(expected = NotAuthenticatedException.class)
    public void getAuthenticatedSessionNullToken() {
        doReturn(null).when(controller).getSessionToken();
        controller.getAuthenticatedSession();
    }

    @Test(expected = NotAuthenticatedException.class)
    public void getAuthenticatedSessionEmptyToken() {
        doReturn("").when(controller).getSessionToken();
        controller.getAuthenticatedSession();
    }

    @Test(expected = NotAuthenticatedException.class)
    public void getAuthenticatedSessionBlankToken() {
        doReturn("   ").when(controller).getSessionToken();
        controller.getAuthenticatedSession();
    }

    @Test(expected = NotAuthenticatedException.class)
    public void getAuthenticatedSessionNullSession() {
        // mock getSessionToken and getMetrics
        doReturn(TEST_SESSION_TOKEN).when(controller).getSessionToken();

        Metrics metrics = new Metrics(TEST_REQUEST_ID);
        doReturn(metrics).when(controller).getMetrics();

        // mock AuthenticationService
        when(authenticationService.getSession(TEST_SESSION_TOKEN)).thenReturn(null);

        // execute
        controller.getAuthenticatedSession();
    }

    @Test(expected = NotAuthenticatedException.class)
    public void getAuthenticatedSessionNotAuthenticated() {
        // mock getSessionToken and getMetrics
        doReturn(TEST_SESSION_TOKEN).when(controller).getSessionToken();

        Metrics metrics = new Metrics(TEST_REQUEST_ID);
        doReturn(metrics).when(controller).getMetrics();

        // mock AuthenticationService
        UserSession session = createSession();
        session.setAuthenticated(false);
        when(authenticationService.getSession(TEST_SESSION_TOKEN)).thenReturn(session);

        // execute
        controller.getAuthenticatedSession();
    }

    @Test
    public void getAuthenticatedSessionSuccess() {
        // mock getSessionToken and getMetrics
        doReturn(TEST_SESSION_TOKEN).when(controller).getSessionToken();

        Metrics metrics = new Metrics(TEST_REQUEST_ID);
        doReturn(metrics).when(controller).getMetrics();

        // mock AuthenticationService
        UserSession session = createSession();
        when(authenticationService.getSession(TEST_SESSION_TOKEN)).thenReturn(session);

        // execute and validate
        UserSession retVal = controller.getAuthenticatedSession();
        assertSame(session, retVal);
        assertSessionInfoInMetrics(metrics);
    }

    @Test
    public void signInExistingSession() throws Exception {
        // mock getSessionToken and getMetrics
        doReturn(TEST_SESSION_TOKEN).when(controller).getSessionToken();

        Metrics metrics = new Metrics(TEST_REQUEST_ID);
        doReturn(metrics).when(controller).getMetrics();

        // mock AuthenticationService
        UserSession session = createSession();
        when(authenticationService.getSession(TEST_SESSION_TOKEN)).thenReturn(session);

        // execute and validate
        Result result = controller.signIn();
        assertSessionInPlayResult(result);
        assertSessionInfoInMetrics(metrics);
    }

    @Test
    public void signInNewSession() throws Exception {
        // mock getSessionToken and getMetrics
        doReturn(null).when(controller).getSessionToken();

        Metrics metrics = new Metrics(TEST_REQUEST_ID);
        doReturn(metrics).when(controller).getMetrics();

        // mock request
        String requestJsonString = "{\n" +
                "   \"username\":\"" + TEST_USERNAME + "\",\n" +
                "   \"password\":\"" + TEST_PASSWORD + "\",\n" +
                "   \"study\":\"" + TEST_STUDY_ID_STRING + "\"\n" +
                "}";
        Context context = TestUtils.mockPlayContextWithJson(requestJsonString);
        Http.Context.current.set(context);

        // mock AuthenticationService
        UserSession session = createSession();
        ArgumentCaptor<SignIn> signInCaptor = ArgumentCaptor.forClass(SignIn.class);
        when(authenticationService.signIn(same(study), any(), signInCaptor.capture())).thenReturn(session);

        // execute and validate
        Result result = controller.signIn();
        assertSessionInPlayResult(result);
        assertSessionInfoInMetrics(metrics);

        // validate signIn
        SignIn signIn = signInCaptor.getValue();
        assertEquals(TEST_USERNAME, signIn.getUsername());
        assertEquals(TEST_PASSWORD, signIn.getPassword());
    }

    @Test
    public void signInExistingSessionUnconsented() throws Exception {
        // mock getSessionToken and getMetrics
        doReturn(TEST_SESSION_TOKEN).when(controller).getSessionToken();

        Metrics metrics = new Metrics(TEST_REQUEST_ID);
        doReturn(metrics).when(controller).getMetrics();

        // mock AuthenticationService
        User user = new User();
        user.setId(TEST_USER_STORMPATH_ID);

        UserSession session = createSessionWithUser(user);
        when(authenticationService.getSession(TEST_SESSION_TOKEN)).thenReturn(session);

        // execute and validate
        try {
            controller.signIn();
            fail("expected exception");
        } catch (ConsentRequiredException ex) {
            // expected exception
        }
        assertSessionInfoInMetrics(metrics);
    }

    @Test
    public void signInNewSessionUnconsented() throws Exception {
        // mock getSessionToken and getMetrics
        doReturn(null).when(controller).getSessionToken();

        Metrics metrics = new Metrics(TEST_REQUEST_ID);
        doReturn(metrics).when(controller).getMetrics();

        // mock request
        String requestJsonString = "{\n" +
                "   \"username\":\"" + TEST_USERNAME + "\",\n" +
                "   \"password\":\"" + TEST_PASSWORD + "\",\n" +
                "   \"study\":\"" + TEST_STUDY_ID_STRING + "\"\n" +
                "}";
        Context context = TestUtils.mockPlayContextWithJson(requestJsonString);
        Http.Context.current.set(context);

        // mock AuthenticationService
        User user = new User();
        user.setId(TEST_USER_STORMPATH_ID);

        UserSession session = createSessionWithUser(user);

        ArgumentCaptor<SignIn> signInCaptor = ArgumentCaptor.forClass(SignIn.class);
        when(authenticationService.signIn(same(study), any(), signInCaptor.capture())).thenReturn(session);

        // execute and validate
        try {
            controller.signIn();
            fail("expected exception");
        } catch (ConsentRequiredException ex) {
            // expected exception
        }
        assertSessionInfoInMetrics(metrics);

        // validate signIn
        SignIn signIn = signInCaptor.getValue();
        assertEquals(TEST_USERNAME, signIn.getUsername());
        assertEquals(TEST_PASSWORD, signIn.getPassword());
    }

    @Test
    public void signInExistingSessionUnconsentedAdmin() throws Exception {
        // mock getSessionToken and getMetrics
        doReturn(TEST_SESSION_TOKEN).when(controller).getSessionToken();

        Metrics metrics = new Metrics(TEST_REQUEST_ID);
        doReturn(metrics).when(controller).getMetrics();

        // mock AuthenticationService
        User user = new User();
        user.setId(TEST_USER_STORMPATH_ID);
        user.setRoles(EnumSet.of(Roles.DEVELOPER));

        UserSession session = createSessionWithUser(user);
        when(authenticationService.getSession(TEST_SESSION_TOKEN)).thenReturn(session);

        // execute and validate
        Result result = controller.signIn();
        assertSessionInPlayResult(result);
        assertSessionInfoInMetrics(metrics);
    }

    @Test
    public void signInNewSessionUnconsentedAdmin() throws Exception {
        // mock getSessionToken and getMetrics
        doReturn(null).when(controller).getSessionToken();

        Metrics metrics = new Metrics(TEST_REQUEST_ID);
        doReturn(metrics).when(controller).getMetrics();

        // mock request
        String requestJsonString = "{\n" +
                "   \"username\":\"" + TEST_USERNAME + "\",\n" +
                "   \"password\":\"" + TEST_PASSWORD + "\",\n" +
                "   \"study\":\"" + TEST_STUDY_ID_STRING + "\"\n" +
                "}";
        Context context = TestUtils.mockPlayContextWithJson(requestJsonString);
        Http.Context.current.set(context);

        // mock AuthenticationService
        User user = new User();
        user.setId(TEST_USER_STORMPATH_ID);
        user.setRoles(EnumSet.of(Roles.DEVELOPER));

        UserSession session = createSessionWithUser(user);

        ArgumentCaptor<SignIn> signInCaptor = ArgumentCaptor.forClass(SignIn.class);
        when(authenticationService.signIn(same(study), any(), signInCaptor.capture())).thenReturn(session);

        // execute and validate
        Result result = controller.signIn();
        assertSessionInPlayResult(result);
        assertSessionInfoInMetrics(metrics);

        // validate signIn
        SignIn signIn = signInCaptor.getValue();
        assertEquals(TEST_USERNAME, signIn.getUsername());
        assertEquals(TEST_PASSWORD, signIn.getPassword());
    }

    @Test
    public void signOut() throws Exception {
        // mock getSessionToken and getMetrics
        doReturn(TEST_SESSION_TOKEN).when(controller).getSessionToken();

        Metrics metrics = new Metrics(TEST_REQUEST_ID);
        doReturn(metrics).when(controller).getMetrics();

        // mock AuthenticationService
        UserSession session = createSession();
        when(authenticationService.getSession(TEST_SESSION_TOKEN)).thenReturn(session);

        // execute and validate
        Result result = controller.signOut();
        assertEquals(HttpStatus.SC_OK, result.status());
        assertSessionInfoInMetrics(metrics);

        verify(authenticationService).signOut(session);
    }

    @Test
    public void signOutAlreadySignedOut() throws Exception {
        // mock getSessionToken and getMetrics
        doReturn(null).when(controller).getSessionToken();

        Metrics metrics = new Metrics(TEST_REQUEST_ID);
        doReturn(metrics).when(controller).getMetrics();

        // execute and validate
        Result result = controller.signOut();
        assertEquals(HttpStatus.SC_OK, result.status());

        // No session, so no check on metrics or AuthService.signOut()
    }

    @Test
    public void verifyEmail() throws Exception {
        // mock getMetrics
        Metrics metrics = new Metrics(TEST_REQUEST_ID);
        doReturn(metrics).when(controller).getMetrics();

        // mock request
        String requestJsonString = "{\n" +
                "   \"sptoken\":\"" + TEST_VERIFY_EMAIL_TOKEN + "\",\n" +
                "   \"study\":\"" + TEST_STUDY_ID_STRING + "\"\n" +
                "}";
        Context context = TestUtils.mockPlayContextWithJson(requestJsonString);
        Http.Context.current.set(context);

        // mock AuthenticationService
        UserSession session = createSession();
        ArgumentCaptor<EmailVerification> emailVerifyCaptor = ArgumentCaptor.forClass(EmailVerification.class);
        when(authenticationService.verifyEmail(same(study), any(), emailVerifyCaptor.capture())).thenReturn(session);

        // execute and validate
        Result result = controller.verifyEmail();
        assertSessionInPlayResult(result);
        assertSessionInfoInMetrics(metrics);

        // validate email verification
        EmailVerification emailVerify = emailVerifyCaptor.getValue();
        assertEquals(TEST_VERIFY_EMAIL_TOKEN, emailVerify.getSptoken());
    }

    @Test
    public void verifyEmailUnconsented() throws Exception {
        // mock getMetrics
        Metrics metrics = new Metrics(TEST_REQUEST_ID);
        doReturn(metrics).when(controller).getMetrics();

        // mock request
        String requestJsonString = "{\n" +
                "   \"sptoken\":\"" + TEST_VERIFY_EMAIL_TOKEN + "\",\n" +
                "   \"study\":\"" + TEST_STUDY_ID_STRING + "\"\n" +
                "}";
        Context context = TestUtils.mockPlayContextWithJson(requestJsonString);
        Http.Context.current.set(context);

        // mock AuthenticationService
        User user = new User();
        user.setId(TEST_USER_STORMPATH_ID);

        UserSession session = createSessionWithUser(user);
        ArgumentCaptor<EmailVerification> emailVerifyCaptor = ArgumentCaptor.forClass(EmailVerification.class);
        when(authenticationService.verifyEmail(same(study), any(), emailVerifyCaptor.capture())).thenReturn(session);

        // execute and validate
        try {
            controller.verifyEmail();
            fail("expected exception");
        } catch (ConsentRequiredException ex) {
            // expected exception
        }
        assertSessionInfoInMetrics(metrics);

        // validate email verification
        EmailVerification emailVerify = emailVerifyCaptor.getValue();
        assertEquals(TEST_VERIFY_EMAIL_TOKEN, emailVerify.getSptoken());
    }

    private static void assertSessionInPlayResult(Result result) throws Exception {
        assertEquals(HttpStatus.SC_OK, result.status());

        // test only a few key values
        String resultString = Helpers.contentAsString(result);
        JsonNode resultNode = BridgeObjectMapper.get().readTree(resultString);
        assertTrue(resultNode.get("authenticated").booleanValue());
        assertEquals(TEST_SESSION_TOKEN, resultNode.get("sessionToken").textValue());
    }

    private static void assertSessionInfoInMetrics(Metrics metrics) {
        ObjectNode metricsJsonNode = metrics.getJson();
        assertEquals(TEST_INTERNAL_SESSION_ID, metricsJsonNode.get("session_id").textValue());
        assertEquals(TEST_STUDY_ID_STRING, metricsJsonNode.get("study").textValue());
        assertEquals(TEST_USER_STORMPATH_ID, metricsJsonNode.get("user_id").textValue());
    }

    private static UserSession createSession() {
        User user = new User();
        user.setId(TEST_USER_STORMPATH_ID);
        user.setConsentStatuses(TestUtils.toMap(TestConstants.REQUIRED_SIGNED_CURRENT));
        return createSessionWithUser(user);
    }

    private static UserSession createSessionWithUser(User user) {
        UserSession session = new UserSession();
        session.setAuthenticated(true);
        session.setInternalSessionToken(TEST_INTERNAL_SESSION_ID);
        session.setSessionToken(TEST_SESSION_TOKEN);
        session.setStudyIdentifier(TEST_STUDY_ID);
        session.setUser(user);
        return session;
    }
}
