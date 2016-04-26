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
import static org.sagebionetworks.bridge.TestConstants.TEST_CONTEXT;

import java.util.EnumSet;
import java.util.HashSet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Sets;

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
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.services.AuthenticationService;
import org.sagebionetworks.bridge.services.StudyService;

import play.mvc.Result;
import play.test.Helpers;

@RunWith(MockitoJUnitRunner.class)
public class AuthenticationControllerMockTest {
    
    private static final HashSet<String> DATA_GROUPS = Sets.newHashSet("A","B");
    private static final String TEST_INTERNAL_SESSION_ID = "internal-session-id";
    private static final String TEST_PASSWORD = "password";
    private static final String TEST_USER_STORMPATH_ID = "spId";
    private static final String TEST_EMAIL = "email@email.com";
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
    ArgumentCaptor<StudyParticipant> participantCaptor;
    
    @Before
    public void before() {
        controller = spy(new AuthenticationController());
        controller.setAuthenticationService(authenticationService);
        
        study = new DynamoStudy();
        study.setDataGroups(DATA_GROUPS);
        when(studyService.getStudy(TEST_STUDY_ID_STRING)).thenReturn(study);
        controller.setStudyService(studyService);
    }
    
    @Test
    public void userCannotAssignRolesToSelfOnSignUp() throws Exception {
        TestUtils.mockPlayContextWithJson(TestUtils.createJson("{'study':'study-key','email':'bridge-testing+test@sagebase.org',"+
                "'password':'P@ssword1','roles':['admin'],'dataGroups':['A','B']}"));
        
        Result result = controller.signUp();
        assertEquals(201, result.status());
        verify(authenticationService).signUp(same(study), participantCaptor.capture(), eq(true));
        
        StudyParticipant participant = participantCaptor.getValue();
        assertTrue(participant.getRoles().isEmpty());
        assertEquals("bridge-testing+test@sagebase.org", participant.getEmail());
        assertEquals("P@ssword1", participant.getPassword());
        assertEquals(DATA_GROUPS, participant.getDataGroups());
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

    private void signInExistingSession(boolean isConsented, Roles role, boolean shouldThrow) throws Exception {
        // mock getSessionToken and getMetrics
        doReturn(TEST_SESSION_TOKEN).when(controller).getSessionToken();

        Metrics metrics = new Metrics(TEST_REQUEST_ID);
        doReturn(metrics).when(controller).getMetrics();

        // mock AuthenticationService
        User user = new User();
        user.setId(TEST_USER_STORMPATH_ID);
        if (isConsented) {
            user.setConsentStatuses(TestUtils.toMap(TestConstants.REQUIRED_SIGNED_CURRENT));
        }
        if (role != null) {
            user.setRoles(EnumSet.of(role));
        }

        UserSession session = createSessionWithUser(user);
        when(authenticationService.getSession(TEST_SESSION_TOKEN)).thenReturn(session);

        // execute and validate
        try {
            Result result = controller.signIn();
            if (shouldThrow) {
                fail("expected exception");
            }
            assertSessionInPlayResult(result);
        } catch (ConsentRequiredException ex) {
            if (!shouldThrow) {
                throw ex;
            }
        }
        assertSessionInfoInMetrics(metrics);
    }
    @Test
    public void signInExistingSession() throws Exception {
        signInExistingSession(true, null, false);
    }

    @Test
    public void signInExistingSessionUnconsented() throws Exception {
        signInExistingSession(false, null, true);
    }

    @Test
    public void signInExistingSessionUnconsentedAdmin() throws Exception {
        signInExistingSession(false, Roles.ADMIN, false);
    }

    @Test
    public void signInExistingSessionUnconsentedDeveloper() throws Exception {
        signInExistingSession(false, Roles.DEVELOPER, false);
    }

    @Test
    public void signInExistingSessionUnconsentedResearcher() throws Exception {
        signInExistingSession(false, Roles.RESEARCHER, false);
    }

    @Test
    public void signInExistingSessionUnconsentedTestUser() throws Exception {
        signInExistingSession(false, Roles.TEST_USERS, true);
    }

    @Test
    public void signInExistingSessionUnconsentedWorker() throws Exception {
        signInExistingSession(false, Roles.WORKER, false);
    }

    private void signInNewSession(boolean isConsented, Roles role, boolean shouldThrow) throws Exception {
        // mock getSessionToken and getMetrics
        doReturn(null).when(controller).getSessionToken();

        doReturn(TEST_CONTEXT).when(controller).getCriteriaContext(any(StudyIdentifier.class));

        Metrics metrics = new Metrics(TEST_REQUEST_ID);
        doReturn(metrics).when(controller).getMetrics();

        // mock request
        String requestJsonString = "{\n" +
                "   \"email\":\"" + TEST_EMAIL + "\",\n" +
                "   \"password\":\"" + TEST_PASSWORD + "\",\n" +
                "   \"study\":\"" + TEST_STUDY_ID_STRING + "\"\n" +
                "}";

        TestUtils.mockPlayContextWithJson(requestJsonString);

        // mock AuthenticationService
        User user = new User();
        user.setId(TEST_USER_STORMPATH_ID);
        if (isConsented) {
            user.setConsentStatuses(TestUtils.toMap(TestConstants.REQUIRED_SIGNED_CURRENT));
        }
        if (role != null) {
            user.setRoles(EnumSet.of(role));
        }

        UserSession session = createSessionWithUser(user);
        ArgumentCaptor<SignIn> signInCaptor = ArgumentCaptor.forClass(SignIn.class);
        when(authenticationService.signIn(same(study), any(), signInCaptor.capture())).thenReturn(session);

        // execute and validate
        try {
            Result result = controller.signIn();
            if (shouldThrow) {
                fail("expected exception");
            }
            assertSessionInPlayResult(result);
        } catch (ConsentRequiredException ex) {
            if (!shouldThrow) {
                throw ex;
            }
        }
        assertSessionInfoInMetrics(metrics);

        // validate signIn
        SignIn signIn = signInCaptor.getValue();
        assertEquals(TEST_EMAIL, signIn.getEmail());
        assertEquals(TEST_PASSWORD, signIn.getPassword());
    }

    @Test
    public void signInNewSession() throws Exception {
        signInNewSession(true, null, false);
    }

    @Test
    public void signInNewSessionUnconsented() throws Exception {
        signInNewSession(false, null, true);
    }

    @Test
    public void signInNewSessionUnconsentedAdmin() throws Exception {
        signInNewSession(false, Roles.ADMIN, false);
    }

    @Test
    public void signInNewSessionUnconsentedDeveloper() throws Exception {
        signInNewSession(false, Roles.DEVELOPER, false);
    }

    @Test
    public void signInNewSessionUnconsentedResearcher() throws Exception {
        signInNewSession(false, Roles.RESEARCHER, false);
    }

    @Test
    public void signInNewSessionUnconsentedTestUser() throws Exception {
        signInNewSession(false, Roles.TEST_USERS, true);
    }

    @Test
    public void signInNewSessionUnconsentedWorker() throws Exception {
        signInNewSession(false, Roles.WORKER, false);
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

        TestUtils.mockPlayContextWithJson(requestJsonString);

        // mock AuthenticationService
        UserSession session = createSession();
        ArgumentCaptor<EmailVerification> emailVerifyCaptor = ArgumentCaptor.forClass(EmailVerification.class);
        when(authenticationService.verifyEmail(same(study), any(), emailVerifyCaptor.capture())).thenReturn(session);

        doReturn(TEST_CONTEXT).when(controller).getCriteriaContext(any(StudyIdentifier.class));

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
        doReturn(TEST_CONTEXT).when(controller).getCriteriaContext(any(StudyIdentifier.class));
        
        // mock getMetrics
        Metrics metrics = new Metrics(TEST_REQUEST_ID);
        doReturn(metrics).when(controller).getMetrics();

        // mock request
        String requestJsonString = "{\n" +
                "   \"sptoken\":\"" + TEST_VERIFY_EMAIL_TOKEN + "\",\n" +
                "   \"study\":\"" + TEST_STUDY_ID_STRING + "\"\n" +
                "}";

        TestUtils.mockPlayContextWithJson(requestJsonString);

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
