package org.sagebionetworks.bridge.play.controllers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_CONTEXT;
import static org.sagebionetworks.bridge.TestUtils.assertResult;
import static org.sagebionetworks.bridge.TestUtils.mockPlayContext;
import static org.sagebionetworks.bridge.TestUtils.mockPlayContextWithJson;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

import org.apache.http.HttpStatus;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.junit.After;
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
import org.sagebionetworks.bridge.exceptions.NotAuthenticatedException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.exceptions.UnsupportedVersionException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.CriteriaContext;
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
    
    private static final DateTime NOW = DateTime.now();
    private static final String REAUTH_TOKEN = "reauthToken";
    private static final String TEST_INTERNAL_SESSION_ID = "internal-session-id";
    private static final String TEST_PASSWORD = "password";
    private static final String TEST_ACCOUNT_ID = "spId";
    private static final String TEST_EMAIL = "email@email.com";
    private static final String TEST_SESSION_TOKEN = "session-token";
    private static final String TEST_STUDY_ID_STRING = "study-key";
    private static final StudyIdentifier TEST_STUDY_ID = new StudyIdentifierImpl(TEST_STUDY_ID_STRING);
    private static final String TEST_TOKEN = "verify-token";
    private static final SignIn EMAIL_PASSWORD_SIGN_IN_REQUEST = new SignIn.Builder().withStudy(TEST_STUDY_ID_STRING)
            .withEmail(TEST_EMAIL).withPassword(TEST_PASSWORD).build();
    private static final SignIn EMAIL_SIGN_IN_REQUEST = new SignIn.Builder().withStudy(TEST_STUDY_ID_STRING)
            .withEmail(TEST_EMAIL).withToken(TEST_TOKEN).build();
    private static final SignIn PHONE_SIGN_IN_REQUEST = new SignIn.Builder().withStudy(TEST_STUDY_ID_STRING)
            .withPhone(TestConstants.PHONE).build();
    private static final SignIn PHONE_SIGN_IN = new SignIn.Builder().withStudy(TEST_STUDY_ID_STRING)
            .withPhone(TestConstants.PHONE).withToken(TEST_TOKEN).build();

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

    @Captor
    ArgumentCaptor<CriteriaContext> contextCaptor;
    
    UserSession userSession;
    
    // This is manually mocked along with a request payload and captured in some tests
    // for verification
    Http.Response response;
    
    @Mock
    Metrics metrics;
    
    @Before
    public void before() {
        DateTimeUtils.setCurrentMillisFixed(NOW.getMillis());
        
        controller = spy(new AuthenticationController());
        controller.setAuthenticationService(authenticationService);
        controller.setCacheProvider(cacheProvider);
        
        userSession = new UserSession();
        userSession.setReauthToken(REAUTH_TOKEN);
        userSession.setSessionToken(TEST_SESSION_TOKEN);
        userSession.setParticipant(new StudyParticipant.Builder().withId(TEST_ACCOUNT_ID).build());
        userSession.setInternalSessionToken(TEST_INTERNAL_SESSION_ID);
        userSession.setStudyIdentifier(TEST_STUDY_ID);
        
        study = new DynamoStudy();
        study.setIdentifier(TEST_STUDY_ID_STRING);
        study.setDataGroups(TestConstants.USER_DATA_GROUPS);
        when(studyService.getStudy(TEST_STUDY_ID_STRING)).thenReturn(study);
        when(studyService.getStudy(TEST_STUDY_ID)).thenReturn(study);
        
        controller.setStudyService(studyService);
        
        doReturn(metrics).when(controller).getMetrics();
    }
    
    @After
    public void after() {
        DateTimeUtils.setCurrentMillisSystem();
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
        response = mockPlayContextWithJson(TestUtils.createJson("{'study':'study-key','email':'email@email.com','token':'ABC'}"));
        userSession.setAuthenticated(true);
        study.setIdentifier("study-test");
        doReturn(study).when(studyService).getStudy("study-test");
        doReturn(userSession).when(authenticationService).emailSignIn(any(CriteriaContext.class), any(SignIn.class));
        
        Result result = controller.emailSignIn();
        assertEquals(200, result.status());
        JsonNode node = BridgeObjectMapper.get().readTree(Helpers.contentAsString(result));
        assertTrue(node.get("authenticated").booleanValue());
     
        verify(authenticationService).emailSignIn(any(CriteriaContext.class), signInCaptor.capture());
        
        SignIn captured = signInCaptor.getValue();
        assertEquals(TEST_EMAIL, captured.getEmail());
        assertEquals("study-key", captured.getStudyId());
        assertEquals("ABC", captured.getToken());
        
        verifyCommonLoggingForSignIns();
    }
    
    @Test(expected = BadRequestException.class)
    public void emailSignInMissingStudyId() throws Exception { 
        mockPlayContextWithJson(TestUtils.createJson("{'email':'email@email.com','token':'abc'}"));
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
            response = mockPlayContextWithJson(TestUtils.createJson(
                    "{'study':'study-key','email':'email@email.com','reauthToken':'abc'}"));
            when(authenticationService.reauthenticate(any(), any(), any())).thenReturn(userSession);
            
            Result result = controller.reauthenticate();
            assertEquals(200, result.status());
            
            verify(authenticationService).reauthenticate(any(), any(), signInCaptor.capture());
            SignIn signIn = signInCaptor.getValue();
            assertEquals("study-key", signIn.getStudyId());
            assertEquals("email@email.com", signIn.getEmail());
            assertEquals("abc", signIn.getReauthToken());
            
            verifyCommonLoggingForSignIns();
            
            JsonNode node = BridgeObjectMapper.get().readTree(Helpers.contentAsString(result));
            assertEquals(REAUTH_TOKEN, node.get("reauthToken").textValue());
            
            verifyCommonLoggingForSignIns();
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
    public void getSessionIfItExistsSuccess() throws Exception {
        mockPlayContext(); 
        // mock getSessionToken and getMetrics
        doReturn(TEST_SESSION_TOKEN).when(controller).getSessionToken();

        // mock AuthenticationService
        when(authenticationService.getSession(TEST_SESSION_TOKEN)).thenReturn(userSession);

        // execute and validate
        UserSession retVal = controller.getSessionIfItExists();
        assertSame(userSession, retVal);
        verifyMetrics();
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

        // mock AuthenticationService
        when(authenticationService.getSession(TEST_SESSION_TOKEN)).thenReturn(null);

        // execute
        controller.getAuthenticatedSession();
    }

    @Test(expected = NotAuthenticatedException.class)
    public void getAuthenticatedSessionNotAuthenticated() {
        // mock getSessionToken and getMetrics
        doReturn(TEST_SESSION_TOKEN).when(controller).getSessionToken();

        // mock AuthenticationService
        UserSession session = createSession(TestConstants.REQUIRED_SIGNED_CURRENT, null);
        session.setAuthenticated(false);
        when(authenticationService.getSession(TEST_SESSION_TOKEN)).thenReturn(session);

        // execute
        controller.getAuthenticatedSession();
    }

    @Test
    public void getAuthenticatedSessionSuccess() throws Exception {
        TestUtils.mockPlayContext();
        // mock getSessionToken and getMetrics
        doReturn(TEST_SESSION_TOKEN).when(controller).getSessionToken();

        // mock AuthenticationService
        UserSession session = createSession(TestConstants.REQUIRED_SIGNED_CURRENT, null);
        when(authenticationService.getSession(TEST_SESSION_TOKEN)).thenReturn(session);

        // execute and validate
        UserSession retVal = controller.getAuthenticatedSession();
        assertSame(session, retVal);
        verifyMetrics();
    }

    @Test
    public void signUpWithCompleteUserData() throws Exception {
        // Other fields will be passed along to the PartcipantService, but it will not be utilized
        // These are the fields that *can* be changed. They are all passed along.
        StudyParticipant originalParticipant = TestUtils.getStudyParticipant(AuthenticationControllerMockTest.class);
        ObjectNode node = BridgeObjectMapper.get().valueToTree(originalParticipant);
        node.put("study", TEST_STUDY_ID_STRING);
        
        mockPlayContextWithJson(node.toString());
        
        Result result = controller.signUp();
        assertResult(result, 201, "Signed up.");
        
        verify(authenticationService).signUp(eq(study), participantCaptor.capture(), eq(false));
        
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
        response = TestUtils.mockPlayContextWithJson(node.toString(), headers);
        controller.signUp();
    }

    @Test(expected = EntityNotFoundException.class)
    public void signUpNoStudy() throws Exception {
        // Participant - don't add study
        StudyParticipant originalParticipant = TestUtils.getStudyParticipant(AuthenticationControllerMockTest.class);
        ObjectNode node = BridgeObjectMapper.get().valueToTree(originalParticipant);

        // Setup and execute. This will throw.
        mockPlayContextWithJson(node.toString());
        controller.signUp();
    }

    @Test
    public void signInExistingSession() throws Exception {
        response = mockPlayContext();
        // mock getSessionToken and getMetrics
        doReturn(TEST_SESSION_TOKEN).when(controller).getSessionToken();

        // mock AuthenticationService
        ConsentStatus consentStatus = TestConstants.REQUIRED_SIGNED_CURRENT;
        UserSession session = createSession(consentStatus, null);
        when(authenticationService.getSession(TEST_SESSION_TOKEN)).thenReturn(session);

        // execute and validate
        Result result = controller.signInV3();
        assertSessionInPlayResult(result);
        verifyCommonLoggingForSignIns();    
    }

    @SuppressWarnings("static-access")
    private void signInNewSession(boolean isConsented, Roles role) throws Exception {
        // mock getSessionToken and getMetrics
        doReturn(null).when(controller).getSessionToken();

        doReturn(TEST_CONTEXT).when(controller).getCriteriaContext(any(StudyIdentifier.class));

        // mock request
        String requestJsonString = "{\n" +
                "   \"email\":\"" + TEST_EMAIL + "\",\n" +
                "   \"password\":\"" + TEST_PASSWORD + "\",\n" +
                "   \"study\":\"" + TEST_STUDY_ID_STRING + "\"\n" +
                "}";

        response = TestUtils.mockPlayContextWithJson(requestJsonString);

        // mock AuthenticationService
        ConsentStatus consentStatus = (isConsented) ? TestConstants.REQUIRED_SIGNED_CURRENT : null;
        UserSession session = createSession(consentStatus, role);
        
        ArgumentCaptor<SignIn> signInCaptor = ArgumentCaptor.forClass(SignIn.class);
        when(authenticationService.signIn(same(study), any(), signInCaptor.capture())).thenReturn(session);

        // execute and validate
        Result result = controller.signInV3();
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
        verifyCommonLoggingForSignIns();

        // validate signIn
        SignIn signIn = signInCaptor.getValue();
        assertEquals(TEST_EMAIL, signIn.getEmail());
        assertEquals(TEST_PASSWORD, signIn.getPassword());
    }

    @Test
    public void signInNewSession() throws Exception {
        signInNewSession(true, null);
    }

    @Test
    public void signOut() throws Exception {
        mockPlayContext();
        
        // mock getSessionToken and getMetrics
        doReturn(TEST_SESSION_TOKEN).when(controller).getSessionToken();

        // mock AuthenticationService
        UserSession session = createSession(TestConstants.REQUIRED_SIGNED_CURRENT, null);
        when(authenticationService.getSession(TEST_SESSION_TOKEN)).thenReturn(session);

        // execute and validate
        Result result = controller.signOut();
        assertEquals(HttpStatus.SC_OK, result.status());
        
        @SuppressWarnings("static-access")
        Http.Response mockResponse = controller.response();

        verify(authenticationService).signOut(session);
        verify(mockResponse).discardCookie(BridgeConstants.SESSION_TOKEN_HEADER);
        verifyMetrics();
    }

    @Test
    public void signOutAlreadySignedOut() throws Exception {
        // mock getSessionToken and getMetrics
        doReturn(null).when(controller).getSessionToken();

        // execute and validate
        Result result = controller.signOut();
        assertEquals(HttpStatus.SC_OK, result.status());

        // No session, so no check on metrics or AuthService.signOut()
    }

    @Test
    public void verifyEmail() throws Exception {
        // mock request
        String requestJsonString = "{\n" +
                "   \"sptoken\":\"" + TEST_TOKEN + "\",\n" +
                "   \"study\":\"" + TEST_STUDY_ID_STRING + "\"\n" +
                "}";

        mockPlayContextWithJson(requestJsonString);

        // mock AuthenticationService
        ArgumentCaptor<EmailVerification> emailVerifyCaptor = ArgumentCaptor.forClass(EmailVerification.class);

        // execute and validate
        Result result = controller.verifyEmail();
        TestUtils.assertResult(result, 200, "Email address verified.");

        // validate email verification
        verify(authenticationService).verifyEmail(emailVerifyCaptor.capture());
        EmailVerification emailVerify = emailVerifyCaptor.getValue();
        assertEquals(TEST_TOKEN, emailVerify.getSptoken());
    }
    
    @Test(expected = UnsupportedVersionException.class)
    public void signInBlockedByVersionKillSwitch() throws Exception {
        Map<String,String[]> headers = new ImmutableMap.Builder<String,String[]>()
                .put("User-Agent", new String[]{"App/14 (Unknown iPhone; iOS/9.0.2) BridgeSDK/4"}).build();
        String json = TestUtils.createJson(
                "{'study':'" + TEST_STUDY_ID_STRING + 
                "','email':'email@email.com','password':'bar'}");
        mockPlayContextWithJson(json, headers);
        study.getMinSupportedAppVersions().put(OperatingSystem.IOS, 20);
        
        controller.signInV3();
    }
    
    @Test(expected = UnsupportedVersionException.class)
    public void emailSignInBlockedByVersionKillSwitch() throws Exception {
        Map<String,String[]> headers = new ImmutableMap.Builder<String,String[]>()
                .put("User-Agent", new String[]{"App/14 (Unknown iPhone; iOS/9.0.2) BridgeSDK/4"}).build();
        String json = TestUtils.createJson(
                "{'study':'" + TEST_STUDY_ID_STRING + 
                "','email':'email@email.com','password':'bar'}");
        mockPlayContextWithJson(json, headers);
        study.getMinSupportedAppVersions().put(OperatingSystem.IOS, 20);
        
        controller.emailSignIn();
    }
    
    @Test(expected = UnsupportedVersionException.class)
    public void phoneSignInBlockedByVersionKillSwitch() throws Exception {
        Map<String,String[]> headers = new ImmutableMap.Builder<String,String[]>()
                .put("User-Agent", new String[]{"App/14 (Unknown iPhone; iOS/9.0.2) BridgeSDK/4"}).build();
        String json = TestUtils.createJson(
                "{'study':'" + TEST_STUDY_ID_STRING + 
                "','email':'email@email.com','password':'bar'}");
        mockPlayContextWithJson(json, headers);
        study.getMinSupportedAppVersions().put(OperatingSystem.IOS, 20);
        
        controller.phoneSignIn();
    }
    
    @Test
    public void resendEmailVerificationWorks() throws Exception {
        mockSignInWithEmailPayload();
        study.getMinSupportedAppVersions().put(OperatingSystem.IOS, 0);
        
        controller.resendEmailVerification();
        
        verify(authenticationService).resendEmailVerification(eq(TEST_STUDY_ID), emailCaptor.capture());
        Email deser = emailCaptor.getValue();
        assertEquals(TEST_STUDY_ID, deser.getStudyIdentifier());
        assertEquals(TEST_EMAIL, deser.getEmail());
    }
    
    @Test(expected = UnsupportedVersionException.class)
    public void resendEmailVerificationAppVersionDisabled() throws Exception {
        mockSignInWithEmailPayload();
        study.getMinSupportedAppVersions().put(OperatingSystem.IOS, 20);
        
        controller.resendEmailVerification();
    }

    @Test(expected = EntityNotFoundException.class)
    public void resendEmailVerificationNoStudy() throws Exception {
        mockPlayContextWithJson(new Email((StudyIdentifier) null, TEST_EMAIL));
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
        mockPlayContextWithJson(new PasswordReset("aPassword", "aSpToken", null));
        controller.resetPassword();
    }

    private void mockResetPasswordRequest() throws Exception {
        Map<String,String[]> headers = new ImmutableMap.Builder<String,String[]>()
                .put("User-Agent", new String[]{"App/14 (Unknown iPhone; iOS/9.0.2) BridgeSDK/4"}).build();
        String json = TestUtils.createJson("{'study':'" + TEST_STUDY_ID_STRING + 
            "','sptoken':'aSpToken','password':'aPassword'}");
        mockPlayContextWithJson(json, headers);
    }
    
    @Test
    public void requestResetPasswordWithEmail() throws Exception {
        mockSignInWithEmailPayload();
        study.getMinSupportedAppVersions().put(OperatingSystem.IOS, 0);
        
        controller.requestResetPassword();
        
        verify(authenticationService).requestResetPassword(eq(study), signInCaptor.capture());
        SignIn deser = signInCaptor.getValue();
        assertEquals(TEST_STUDY_ID_STRING, deser.getStudyId());
        assertEquals(TEST_EMAIL, deser.getEmail());
    }
    
    @Test
    public void requestResetPasswordWithPhone() throws Exception {
        mockSignInWithPhonePayload();
        study.getMinSupportedAppVersions().put(OperatingSystem.IOS, 0);
        
        controller.requestResetPassword();
        
        verify(authenticationService).requestResetPassword(eq(study), signInCaptor.capture());
        SignIn deser = signInCaptor.getValue();
        assertEquals(TEST_STUDY_ID_STRING, deser.getStudyId());
        assertEquals(TestConstants.PHONE.getNumber(), deser.getPhone().getNumber());
    }
    
    @Test(expected = UnsupportedVersionException.class)
    public void requestResetPasswordAppVersionDisabled() throws Exception {
        mockSignInWithEmailPayload();
        study.getMinSupportedAppVersions().put(OperatingSystem.IOS, 20); // blocked
        
        controller.requestResetPassword();
    }

    @Test(expected = EntityNotFoundException.class)
    public void requestResetPasswordNoStudy() throws Exception {
        when(studyService.getStudy((String)any())).thenThrow(new EntityNotFoundException(Study.class));
        mockPlayContextWithJson(new SignIn.Builder().withEmail(TEST_EMAIL).build());
        controller.requestResetPassword();
    }
    
    @Test
    public void signUpWithNoCheckForConsentDeclared() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder()
                .withEmail(TEST_EMAIL).withPassword(TEST_PASSWORD).build();
        
        ObjectNode node = (ObjectNode)BridgeObjectMapper.get().valueToTree(participant);
        node.put("study", TEST_STUDY_ID_STRING);
        TestUtils.mockPlayContextWithJson(node.toString());
        
        Result result = controller.signUp();
        TestUtils.assertResult(result, 201, "Signed up.");
        
        verify(authenticationService).signUp(eq(study), participantCaptor.capture(), eq(false));
        StudyParticipant captured = participantCaptor.getValue();
        assertEquals(TEST_EMAIL, captured.getEmail());
        assertEquals(TEST_PASSWORD, captured.getPassword());
    }

    @Test
    public void signUpWithCheckForConsentDeclaredFalse() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder()
                .withEmail(TEST_EMAIL).withPassword(TEST_PASSWORD).build();
        
        ObjectNode node = (ObjectNode)BridgeObjectMapper.get().valueToTree(participant);
        node.put("study", TEST_STUDY_ID_STRING);
        node.put("checkForConsent", false);
        TestUtils.mockPlayContextWithJson(node.toString());
        
        Result result = controller.signUp();
        TestUtils.assertResult(result, 201, "Signed up.");
        
        verify(authenticationService).signUp(eq(study), participantCaptor.capture(), eq(false));
        StudyParticipant captured = participantCaptor.getValue();
        assertEquals(TEST_EMAIL, captured.getEmail());
        assertEquals(TEST_PASSWORD, captured.getPassword());
    }
    
    @Test
    public void signUpWithCheckForConsentDeclaredTrue() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder()
                .withEmail(TEST_EMAIL).withPassword(TEST_PASSWORD).build();
        
        ObjectNode node = (ObjectNode)BridgeObjectMapper.get().valueToTree(participant);
        node.put("study", TEST_STUDY_ID_STRING);
        node.put("checkForConsent", true);
        TestUtils.mockPlayContextWithJson(node.toString());
        
        Result result = controller.signUp();
        TestUtils.assertResult(result, 201, "Signed up.");
        
        verify(authenticationService).signUp(eq(study), participantCaptor.capture(), eq(true));
        StudyParticipant captured = participantCaptor.getValue();
        assertEquals(TEST_EMAIL, captured.getEmail());
        assertEquals(TEST_PASSWORD, captured.getPassword());
    }
    
    public void requestPhoneSignIn() throws Exception {
        mockPlayContextWithJson(PHONE_SIGN_IN_REQUEST);
        
        Result result = controller.requestPhoneSignIn();
        assertResult(result, 202, "Message sent.");
        
        verify(authenticationService).requestPhoneSignIn(signInCaptor.capture());
        
        SignIn captured = signInCaptor.getValue();
        assertEquals(TEST_STUDY_ID_STRING, captured.getStudyId());
        assertEquals(TestConstants.PHONE.getNumber(), captured.getPhone().getNumber());
    }
    
    @Test
    public void phoneSignIn() throws Exception {
        response = mockPlayContextWithJson(PHONE_SIGN_IN);
        
        when(authenticationService.phoneSignIn(any(), any())).thenReturn(userSession);
        
        Result result = controller.phoneSignIn();
        assertEquals(200, result.status());
        
        // Returns user session.
        JsonNode node = TestUtils.getJson(result);
        assertEquals(TEST_SESSION_TOKEN, node.get("sessionToken").textValue());
        assertEquals("UserSessionInfo", node.get("type").textValue());
        
        verify(authenticationService).phoneSignIn(contextCaptor.capture(), signInCaptor.capture());
        
        CriteriaContext context = contextCaptor.getValue();
        assertEquals(TEST_STUDY_ID_STRING, context.getStudyIdentifier().getIdentifier());
        
        SignIn captured = signInCaptor.getValue();
        assertEquals(TEST_STUDY_ID_STRING, captured.getStudyId());
        assertEquals(TEST_TOKEN, captured.getToken());
        assertEquals(TestConstants.PHONE.getNumber(), captured.getPhone().getNumber());
        
        verifyCommonLoggingForSignIns();
    }
    
    @Test(expected = BadRequestException.class)
    public void phoneSignInMissingStudy() throws Exception {
        SignIn badPhoneSignIn = new SignIn.Builder().withStudy(null)
                .withPhone(TestConstants.PHONE).withToken(TEST_TOKEN).build();
        mockPlayContextWithJson(badPhoneSignIn);
        
        controller.phoneSignIn();
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void phoneSignInBadStudy() throws Exception {
        SignIn badPhoneSignIn = new SignIn.Builder().withStudy("bad-study")
                .withPhone(TestConstants.PHONE).withToken(TEST_TOKEN).build();
        mockPlayContextWithJson(badPhoneSignIn);
        when(authenticationService.phoneSignIn(any(), any())).thenReturn(userSession);
        when(studyService.getStudy((String)any())).thenThrow(new EntityNotFoundException(Study.class));
        
        controller.phoneSignIn();
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void signInV3ThrowsNotFound() throws Exception {
        mockPlayContextWithJson(PHONE_SIGN_IN);
        
        when(authenticationService.signIn(any(), any(), any())).thenThrow(new UnauthorizedException());
        
        controller.signInV3();
    }
    
    @Test(expected = UnauthorizedException.class)
    public void signInV4ThrowsUnauthoried() throws Exception {
        mockPlayContextWithJson(PHONE_SIGN_IN);
        
        when(authenticationService.signIn(any(), any(), any())).thenThrow(new UnauthorizedException());
        
        controller.signIn();
    }
    
    @Test
    public void unconsentedSignInSetsCookie() throws Exception {
        response = mockPlayContextWithJson(EMAIL_PASSWORD_SIGN_IN_REQUEST);
        when(authenticationService.signIn(any(), any(), any())).thenThrow(new ConsentRequiredException(userSession));
        
        try {
            controller.signIn();
            fail("Should have thrown exeption");
        } catch(ConsentRequiredException e) {
        }
        verifyCommonLoggingForSignIns();
    }
    
    @Test
    public void unconsentedEmailSignInSetsCookie() throws Exception {
        response = mockPlayContextWithJson(EMAIL_SIGN_IN_REQUEST);
        when(authenticationService.emailSignIn(any(), any())).thenThrow(new ConsentRequiredException(userSession));
        
        try {
            controller.emailSignIn();
            fail("Should have thrown exeption");
        } catch(ConsentRequiredException e) {
        }
        verifyCommonLoggingForSignIns();
    }
    
    @Test
    public void unconsentedPhoneSignInSetsCookie() throws Exception {
        response = mockPlayContextWithJson(PHONE_SIGN_IN_REQUEST);
        when(authenticationService.phoneSignIn(any(), any())).thenThrow(new ConsentRequiredException(userSession));
        
        try {
            controller.phoneSignIn();
            fail("Should have thrown exeption");
        } catch(ConsentRequiredException e) {
        }
        verifyCommonLoggingForSignIns();
    }
    
    private void mockSignInWithEmailPayload() throws Exception {
        Map<String, String[]> headers = new ImmutableMap.Builder<String, String[]>()
                .put("User-Agent", new String[] { "App/14 (Unknown iPhone; iOS/9.0.2) BridgeSDK/4" }).build();
        SignIn signIn = new SignIn.Builder().withStudy(TEST_STUDY_ID_STRING).withEmail(TEST_EMAIL).build();
        mockPlayContextWithJson(signIn, headers);
    }

    private void mockSignInWithPhonePayload() throws Exception {
        Map<String, String[]> headers = new ImmutableMap.Builder<String, String[]>()
                .put("User-Agent", new String[] { "App/14 (Unknown iPhone; iOS/9.0.2) BridgeSDK/4" }).build();
        SignIn signIn = new SignIn.Builder().withStudy(TEST_STUDY_ID_STRING).withPhone(TestConstants.PHONE).build();
        mockPlayContextWithJson(signIn, headers);
    }

    private static void assertSessionInPlayResult(Result result) throws Exception {
        assertEquals(HttpStatus.SC_OK, result.status());

        // test only a few key values
        String resultString = Helpers.contentAsString(result);
        JsonNode resultNode = BridgeObjectMapper.get().readTree(resultString);
        assertTrue(resultNode.get("authenticated").booleanValue());
        assertEquals(TEST_SESSION_TOKEN, resultNode.get("sessionToken").textValue());
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
    
    private void verifyMetrics() {
        verify(controller, atLeastOnce()).getMetrics();
        
        verify(metrics, atLeastOnce()).setSessionId(TEST_INTERNAL_SESSION_ID);
        verify(metrics, atLeastOnce()).setUserId(TEST_ACCOUNT_ID);
        verify(metrics, atLeastOnce()).setStudy(TEST_STUDY_ID_STRING);
    }
    
    private void verifyCommonLoggingForSignIns() throws Exception {
        verifyMetrics();
        
        verify(response).setCookie(BridgeConstants.SESSION_TOKEN_HEADER, TEST_SESSION_TOKEN,
                BridgeConstants.BRIDGE_SESSION_EXPIRE_IN_SECONDS, "/");
        
        verify(cacheProvider).updateRequestInfo(requestInfoCaptor.capture());
        RequestInfo info = requestInfoCaptor.getValue();
        assertEquals(NOW.getMillis(), info.getSignedInOn().getMillis());
    }
    
}
