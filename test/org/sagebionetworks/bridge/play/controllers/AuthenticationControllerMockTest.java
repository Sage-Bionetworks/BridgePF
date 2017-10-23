package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_CONTEXT;
import static org.sagebionetworks.bridge.TestUtils.assertResult;
import static org.sagebionetworks.bridge.TestUtils.mockPlayContextWithJson;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

import org.apache.http.HttpStatus;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.NotAuthenticatedException;
import org.sagebionetworks.bridge.exceptions.UnsupportedVersionException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.Metrics;
import org.sagebionetworks.bridge.models.OperatingSystem;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.Email;
import org.sagebionetworks.bridge.models.accounts.EmailVerification;
import org.sagebionetworks.bridge.models.accounts.PasswordReset;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.services.AuthenticationService;
import org.sagebionetworks.bridge.services.StudyService;

import play.mvc.Http;
import play.mvc.Result;
import play.test.Helpers;

@RunWith(MockitoJUnitRunner.class)
public class AuthenticationControllerMockTest {
    
    private static final String REAUTH_TOKEN = "reauthToken";
    private static final String SESSION_TOKEN = "sessionToken";
    private static final String TEST_INTERNAL_SESSION_ID = "internal-session-id";
    private static final String TEST_PASSWORD = "password";
    private static final String TEST_ACCOUNT_ID = "spId";
    private static final String TEST_EMAIL = "email@email.com";
    private static final String TEST_REQUEST_ID = "request-id";
    private static final String TEST_SESSION_TOKEN = "session-token";
    private static final String TEST_STUDY_ID_STRING = "study-key";
    private static final String TEST_TOKEN = "testToken";
    private static final StudyIdentifier TEST_STUDY_ID = new StudyIdentifierImpl(TEST_STUDY_ID_STRING);
    private static final String TEST_VERIFY_EMAIL_TOKEN = "verify-email-token";
    private static final SignIn SIGN_IN = new SignIn.Builder().withStudy(TEST_STUDY_ID_STRING).withEmail(TEST_EMAIL)
            .withPassword(TEST_PASSWORD).withToken(TEST_TOKEN).build();
    private static final Map<String, String[]> HEADERS = new ImmutableMap.Builder<String, String[]>()
            .put("Accept-Language", new String[] { "en" }).build();
    AuthenticationController controller;

    @Mock
    AuthenticationService authenticationService;

    private Study study;
    
    @Mock
    StudyService studyService;
    
    @Mock
    CacheProvider cacheProvider;
    
    @Captor
    ArgumentCaptor<StudyParticipant> participantCaptor;
    
    @Captor
    ArgumentCaptor<RequestInfo> requestInfoCaptor;
    
    @Captor
    ArgumentCaptor<SignIn> signInCaptor;
    
    @Captor
    ArgumentCaptor<Email> emailCaptor;
    
    @Captor
    ArgumentCaptor<PasswordReset> passwordResetCaptor;
    
    UserSession userSession;
    
    @Before
    public void before() {
        controller = spy(new AuthenticationController());
        controller.setAuthenticationService(authenticationService);
        controller.setCacheProvider(cacheProvider);
        
        userSession = new UserSession();
        userSession.setReauthToken(REAUTH_TOKEN);
        userSession.setSessionToken(SESSION_TOKEN);
        userSession.setStudyIdentifier(TEST_STUDY_ID);
        
        study = new DynamoStudy();
        study.setIdentifier(TEST_STUDY_ID_STRING);
        study.setDataGroups(TestConstants.USER_DATA_GROUPS);
        when(studyService.getStudy(TEST_STUDY_ID_STRING)).thenReturn(study);
        when(studyService.getStudy(TEST_STUDY_ID)).thenReturn(study);
        controller.setStudyService(studyService);
    }
    
    @Test
    public void requestEmailSignIn() throws Exception {
        mockPlayContextWithJson(TestUtils.createJson("{'study':'study-key','email':'email@email.com'}"));
        
        Result result = controller.requestEmailSignIn();
        assertResult(result, 202, "Email sent.");
     
        verify(authenticationService).requestEmailSignIn(signInCaptor.capture());
        assertEquals("study-key", signInCaptor.getValue().getStudyId());
        assertEquals(TEST_EMAIL, signInCaptor.getValue().getEmail());
    }
    
    @Test
    public void emailSignIn() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder().build();
        mockPlayContextWithJson(TestUtils.createJson("{'token':'testToken'}"), HEADERS);
        userSession.setParticipant(participant);
        userSession.setAuthenticated(true);
        study.setIdentifier("study-test");
        doReturn(study).when(studyService).getStudy("study-test");
        doReturn(userSession).when(authenticationService).emailSignIn(any(), any(), any());
        doReturn(SIGN_IN).when(cacheProvider).getSignIn("testToken");
        
        Result result = controller.emailSignIn();
        assertEquals(200, result.status());
        JsonNode node = BridgeObjectMapper.get().readTree(Helpers.contentAsString(result));
        assertTrue(node.get("authenticated").booleanValue());
     
        verify(authenticationService).emailSignIn(any(), any(), signInCaptor.capture());
        
        SignIn captured = signInCaptor.getValue();
        assertEquals(TEST_TOKEN, captured.getToken());
    }
    
    @Test(expected = InvalidEntityException.class)
    public void emailSignInNoToken() throws Exception {
        SignIn signIn = new SignIn.Builder().build();
        mockPlayContextWithJson(TestUtils.createJson("{}"), HEADERS);

        when(authenticationService.emailSignIn(any(), any(), any())).thenThrow(new InvalidEntityException(signIn,""));
        
        controller.emailSignIn();
    }
    
    @Test(expected = UnsupportedVersionException.class)
    public void emailSignInUnsupportedExceptionThrownNotConsentRequired() throws Exception {
        mockPlayContextWithJson(TestUtils.createJson("{}"), HEADERS);
        UserSession session = new UserSession();
        when(authenticationService.emailSignIn(any(), any(), any())).thenThrow(new ConsentRequiredException(session));
        doThrow(new UnsupportedVersionException(ClientInfo.UNKNOWN_CLIENT)).when(controller).verifySupportedVersionOrThrowException(any());
        
        controller.emailSignIn();
    }
    
    @Test(expected = BadRequestException.class)
    public void reauthenticateWithoutStudyThrowsException() throws Exception {
        mockPlayContextWithJson(TestUtils.createJson(
                "{'email':'email@email.com','reauthToken':'abc'}"));
        
        controller.reauthenticate();
    }
    
    @Test
    public void reauthenticate() throws Exception {
        long timestamp = DateTime.now().getMillis();
        DateTimeUtils.setCurrentMillisFixed(timestamp);
        try {
            Http.Response response = mockPlayContextWithJson(TestUtils.createJson(
                    "{'study':'study-key','email':'email@email.com','reauthToken':'abc'}"));
            when(authenticationService.reauthenticate(any(), any(), any())).thenReturn(userSession);
            doReturn(new Metrics("abcd")).when(controller).getMetrics();
            
            Result result = controller.reauthenticate();
            assertEquals(200, result.status());
            
            verify(authenticationService).reauthenticate(any(), any(), signInCaptor.capture());
            SignIn signIn = signInCaptor.getValue();
            assertEquals("study-key", signIn.getStudyId());
            assertEquals("email@email.com", signIn.getEmail());
            assertEquals("abc", signIn.getReauthToken());
            
            verify(controller).getMetrics();
            
            verify(response).setCookie(BridgeConstants.SESSION_TOKEN_HEADER, SESSION_TOKEN,
                    BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS, "/");
            
            verify(cacheProvider).updateRequestInfo(requestInfoCaptor.capture());
            RequestInfo requestInfo = requestInfoCaptor.getValue();
            assertEquals(timestamp, requestInfo.getSignedInOn().getMillis());
            
            JsonNode node = BridgeObjectMapper.get().readTree(Helpers.contentAsString(result));
            assertEquals(REAUTH_TOKEN, node.get("reauthToken").textValue());
        } finally {
            DateTimeUtils.setCurrentMillisSystem();
        }
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
        UserSession session = createSession(TestConstants.REQUIRED_SIGNED_CURRENT, null);
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
        UserSession session = createSession(TestConstants.REQUIRED_SIGNED_CURRENT, null);
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
        UserSession session = createSession(TestConstants.REQUIRED_SIGNED_CURRENT, null);
        when(authenticationService.getSession(TEST_SESSION_TOKEN)).thenReturn(session);

        // execute and validate
        UserSession retVal = controller.getAuthenticatedSession();
        assertSame(session, retVal);
        assertSessionInfoInMetrics(metrics);
    }

    @Test
    public void signUpWithCompleteUserData() throws Exception {
        // Other fields will be passed along to the PartcipantService, but it will not be utilized
        // These are the fields that *can* be changed. They are all passed along.
        StudyParticipant originalParticipant = TestUtils.getStudyParticipant(AuthenticationControllerMockTest.class);
        ObjectNode node = BridgeObjectMapper.get().valueToTree(originalParticipant);
        node.put("study", TEST_STUDY_ID_STRING);
        
        TestUtils.mockPlayContextWithJson(node.toString());
        
        Result result = controller.signUp();
        TestUtils.assertResult(result, 201, "Signed up.");
        
        verify(authenticationService).signUp(eq(study), participantCaptor.capture());
        
        StudyParticipant persistedParticipant = participantCaptor.getValue();
        assertEquals(originalParticipant.getFirstName(), persistedParticipant.getFirstName());
        assertEquals(originalParticipant.getLastName(), persistedParticipant.getLastName());
        assertEquals(originalParticipant.getEmail(), persistedParticipant.getEmail());
        assertEquals(originalParticipant.getPassword(), persistedParticipant.getPassword());
        assertEquals(originalParticipant.getSharingScope(), persistedParticipant.getSharingScope());
        assertEquals(originalParticipant.getExternalId(), persistedParticipant.getExternalId());
        assertTrue(persistedParticipant.isNotifyByEmail());
        assertEquals(originalParticipant.getDataGroups(), persistedParticipant.getDataGroups());
        assertEquals(originalParticipant.getAttributes(), persistedParticipant.getAttributes());
        assertEquals(originalParticipant.getLanguages(), persistedParticipant.getLanguages());
    }

    @Test(expected = UnsupportedVersionException.class)
    public void signUpAppVersionDisabled() throws Exception {
        // Headers
        Map<String,String[]> headers = new ImmutableMap.Builder<String,String[]>()
                .put("User-Agent", new String[]{"App/14 (Unknown iPhone; iOS/9.0.2) BridgeSDK/4"}).build();

        // Participant
        StudyParticipant originalParticipant = TestUtils.getStudyParticipant(AuthenticationControllerMockTest.class);
        ObjectNode node = BridgeObjectMapper.get().valueToTree(originalParticipant);
        node.put("study", TEST_STUDY_ID_STRING);

        // min app version is 20 (which is higher than 14)
        study.getMinSupportedAppVersions().put(OperatingSystem.IOS, 20);

        // Setup and execute. This will throw.
        TestUtils.mockPlayContextWithJson(node.toString(), headers);
        controller.signUp();
    }

    @Test(expected = EntityNotFoundException.class)
    public void signUpNoStudy() throws Exception {
        // Participant - don't add study
        StudyParticipant originalParticipant = TestUtils.getStudyParticipant(AuthenticationControllerMockTest.class);
        ObjectNode node = BridgeObjectMapper.get().valueToTree(originalParticipant);

        // Setup and execute. This will throw.
        TestUtils.mockPlayContextWithJson(node.toString());
        controller.signUp();
    }

    private void signInExistingSession(boolean isConsented, Roles role, boolean shouldThrow) throws Exception {
        // mock getSessionToken and getMetrics
        doReturn(TEST_SESSION_TOKEN).when(controller).getSessionToken();

        Metrics metrics = new Metrics(TEST_REQUEST_ID);
        doReturn(metrics).when(controller).getMetrics();

        // mock AuthenticationService
        ConsentStatus consentStatus = (isConsented) ? TestConstants.REQUIRED_SIGNED_CURRENT : null;
        UserSession session = createSession(consentStatus, role);
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

    @SuppressWarnings("static-access")
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
        ConsentStatus consentStatus = (isConsented) ? TestConstants.REQUIRED_SIGNED_CURRENT : null;
        UserSession session = createSession(consentStatus, role);
        
        ArgumentCaptor<SignIn> signInCaptor = ArgumentCaptor.forClass(SignIn.class);
        when(authenticationService.signIn(same(study), any(), signInCaptor.capture())).thenReturn(session);

        // execute and validate
        try {
            Result result = controller.signIn();
            if (shouldThrow) {
                fail("expected exception");
            }
            assertSessionInPlayResult(result);
            
            Http.Response mockResponse = controller.response();

            verify(cacheProvider).updateRequestInfo(requestInfoCaptor.capture());
            verify(mockResponse).setCookie(BridgeConstants.SESSION_TOKEN_HEADER, session.getSessionToken(),
                    BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS, "/");
            
            RequestInfo requestInfo = requestInfoCaptor.getValue();
            assertEquals("spId", requestInfo.getUserId());
            assertEquals(TEST_STUDY_ID, requestInfo.getStudyIdentifier());
            assertTrue(requestInfo.getSignedInOn() != null);
            assertEquals(TestConstants.USER_DATA_GROUPS, requestInfo.getUserDataGroups());
            assertNotNull(requestInfo.getSignedInOn());
            
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
        TestUtils.mockPlayContext();
        
        // mock getSessionToken and getMetrics
        doReturn(TEST_SESSION_TOKEN).when(controller).getSessionToken();

        Metrics metrics = new Metrics(TEST_REQUEST_ID);
        doReturn(metrics).when(controller).getMetrics();

        // mock AuthenticationService
        UserSession session = createSession(TestConstants.REQUIRED_SIGNED_CURRENT, null);
        when(authenticationService.getSession(TEST_SESSION_TOKEN)).thenReturn(session);

        // execute and validate
        Result result = controller.signOut();
        assertEquals(HttpStatus.SC_OK, result.status());
        assertSessionInfoInMetrics(metrics);
        
        @SuppressWarnings("static-access")
        Http.Response mockResponse = controller.response();

        verify(authenticationService).signOut(session);
        verify(mockResponse).discardCookie(BridgeConstants.SESSION_TOKEN_HEADER);
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
        ArgumentCaptor<EmailVerification> emailVerifyCaptor = ArgumentCaptor.forClass(EmailVerification.class);

        // execute and validate
        Result result = controller.verifyEmail();
        TestUtils.assertResult(result, 200, "Email address verified.");

        // validate email verification
        verify(authenticationService).verifyEmail(emailVerifyCaptor.capture());
        EmailVerification emailVerify = emailVerifyCaptor.getValue();
        assertEquals(TEST_VERIFY_EMAIL_TOKEN, emailVerify.getSptoken());
    }
    
    @Test(expected = UnsupportedVersionException.class)
    public void signInBlockedByVersionKillSwitch() throws Exception {
        Map<String,String[]> headers = new ImmutableMap.Builder<String,String[]>()
                .put("User-Agent", new String[]{"App/14 (Unknown iPhone; iOS/9.0.2) BridgeSDK/4"}).build();
        String json = TestUtils.createJson(
                "{'study':'" + TEST_STUDY_ID_STRING + 
                "','email':'email@email.com','password':'bar'}");
        TestUtils.mockPlayContextWithJson(json, headers);
        study.getMinSupportedAppVersions().put(OperatingSystem.IOS, 20);
        
        Metrics metrics = new Metrics(TEST_REQUEST_ID);
        doReturn(metrics).when(controller).getMetrics();

        controller.signIn();
    }
    
    @Test(expected = UnsupportedVersionException.class)
    public void emailSignInBlockedByVersionKillSwitch() throws Exception {
        Map<String,String[]> headers = new ImmutableMap.Builder<String,String[]>()
                .put("Accept-Language", new String[] {"en"})
                .put("User-Agent", new String[]{"App/14 (Unknown iPhone; iOS/9.0.2) BridgeSDK/4"}).build();
        String json = TestUtils.createJson("{'token':'token'}");
        TestUtils.mockPlayContextWithJson(json, headers);
        study.getMinSupportedAppVersions().put(OperatingSystem.IOS, 20);
        when(authenticationService.emailSignIn(any(), any(), any())).thenReturn(userSession);
        
        Metrics metrics = new Metrics(TEST_REQUEST_ID);
        doReturn(metrics).when(controller).getMetrics();
        
        when(cacheProvider.getSignIn("token")).thenReturn(SIGN_IN);

        controller.emailSignIn();
    }
    
    @Test
    public void resendEmailVerificationWorks() throws Exception {
        mockEmailRequestPayload();
        study.getMinSupportedAppVersions().put(OperatingSystem.IOS, 0);
        
        controller.resendEmailVerification();
        
        verify(authenticationService).resendEmailVerification(eq(TEST_STUDY_ID), emailCaptor.capture());
        Email deser = emailCaptor.getValue();
        assertEquals(TEST_STUDY_ID, deser.getStudyIdentifier());
        assertEquals(TEST_EMAIL, deser.getEmail());
    }
    
    @Test(expected = UnsupportedVersionException.class)
    public void resendEmailVerificationAppVersionDisabled() throws Exception {
        mockEmailRequestPayload();
        study.getMinSupportedAppVersions().put(OperatingSystem.IOS, 20);
        
        controller.resendEmailVerification();
    }

    @Test(expected = EntityNotFoundException.class)
    public void resendEmailVerificationNoStudy() throws Exception {
        TestUtils.mockPlayContextWithJson(new Email((StudyIdentifier) null, TEST_EMAIL));
        controller.resendEmailVerification();
    }

    @Test
    public void resetPassword() throws Exception {
        mockResetPasswordRequest();
        study.getMinSupportedAppVersions().put(OperatingSystem.IOS, 0);
        
        controller.resetPassword();
        
        verify(authenticationService).resetPassword(passwordResetCaptor.capture());
        
        PasswordReset passwordReset = passwordResetCaptor.getValue();
        assertEquals("aSpToken", passwordReset.getSptoken());
        assertEquals("aPassword", passwordReset.getPassword());
        assertEquals(TEST_STUDY_ID_STRING, passwordReset.getStudyIdentifier());
    }
    
    @Test(expected = UnsupportedVersionException.class)
    public void resetPasswordAppVersionDisabled() throws Exception {
        mockResetPasswordRequest();
        study.getMinSupportedAppVersions().put(OperatingSystem.IOS, 20);
        
        controller.resetPassword();
    }

    @Test(expected = EntityNotFoundException.class)
    public void resetPasswordNoStudy() throws Exception {
        TestUtils.mockPlayContextWithJson(new PasswordReset("aPassword", "aSpToken", null));
        controller.resetPassword();
    }

    private void mockResetPasswordRequest() throws Exception {
        Map<String,String[]> headers = new ImmutableMap.Builder<String,String[]>()
                .put("User-Agent", new String[]{"App/14 (Unknown iPhone; iOS/9.0.2) BridgeSDK/4"}).build();
        String json = TestUtils.createJson("{'study':'" + TEST_STUDY_ID_STRING + 
            "','sptoken':'aSpToken','password':'aPassword'}");
        TestUtils.mockPlayContextWithJson(json, headers);
    }
    
    @Test
    public void requestResetPassword() throws Exception {
        mockEmailRequestPayload();
        study.getMinSupportedAppVersions().put(OperatingSystem.IOS, 0);
        
        controller.requestResetPassword();
        
        verify(authenticationService).requestResetPassword(eq(study), emailCaptor.capture());
        Email deser = emailCaptor.getValue();
        assertEquals(TEST_STUDY_ID, deser.getStudyIdentifier());
        assertEquals(TEST_EMAIL, deser.getEmail());
    }
    
    @Test(expected = UnsupportedVersionException.class)
    public void requestResetPasswordAppVersionDisabled() throws Exception {
        mockEmailRequestPayload();
        study.getMinSupportedAppVersions().put(OperatingSystem.IOS, 20); // blocked
        
        controller.requestResetPassword();
    }

    @Test(expected = EntityNotFoundException.class)
    public void requestResetPasswordNoStudy() throws Exception {
        TestUtils.mockPlayContextWithJson(new Email((StudyIdentifier) null, TEST_EMAIL));
        controller.requestResetPassword();
    }

    private void mockEmailRequestPayload() throws Exception {
        Map<String,String[]> headers = new ImmutableMap.Builder<String,String[]>()
                .put("User-Agent", new String[]{"App/14 (Unknown iPhone; iOS/9.0.2) BridgeSDK/4"}).build();
        String json = TestUtils.createJson(
                "{'study':'" + TEST_STUDY_ID_STRING +"','email':'"+TEST_EMAIL+"'}");
        TestUtils.mockPlayContextWithJson(json, headers);
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
        assertEquals(TEST_ACCOUNT_ID, metricsJsonNode.get("user_id").textValue());
    }

    private UserSession createSession(ConsentStatus status, Roles role) {
        StudyParticipant.Builder builder = new StudyParticipant.Builder();
        builder.withId(TEST_ACCOUNT_ID);
        // set this value so we can verify it is copied into RequestInfo on a sign in.
        builder.withDataGroups(TestConstants.USER_DATA_GROUPS);
        if (role != null) {
            builder.withRoles(Sets.newHashSet(role));
        }
        UserSession session = new UserSession(builder.build());
        session.setAuthenticated(true);
        session.setInternalSessionToken(TEST_INTERNAL_SESSION_ID);
        session.setSessionToken(TEST_SESSION_TOKEN);
        session.setStudyIdentifier(TEST_STUDY_ID);
        if (status != null){
            session.setConsentStatuses(TestUtils.toMap(status));    
        }
        return session;
    }
    
}
