package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.DistributedLockDao;
import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.Email;
import org.sagebionetworks.bridge.models.accounts.EmailVerification;
import org.sagebionetworks.bridge.models.accounts.HealthId;
import org.sagebionetworks.bridge.models.accounts.PasswordReset;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.SignUp;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.validators.EmailValidator;
import org.sagebionetworks.bridge.validators.EmailVerificationValidator;
import org.sagebionetworks.bridge.validators.PasswordResetValidator;
import org.sagebionetworks.bridge.validators.SignInValidator;

import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

@RunWith(MockitoJUnitRunner.class)
public class AuthenticationServiceMockTest {

    private static final String SESSION_TOKEN = "sessionToken";
    private static final String HEALTH_CODE = "healthCode";
    private static final String HEALTH_ID = "healthId";
    private static final String PASSWORD = "P@ssword1";
    private static final String STUDY_ID = "test-study";
    private static final String EMAIL = "bridge-testing@sagebase.org";
    private static final String USERNAME = "userName";
    private static final Set<String> DATA_GROUPS = Sets.newHashSet("group1", "group2");
    private static final EmailVerificationValidator VERIFICATION_VALIDATOR = new EmailVerificationValidator();
    private static final SignInValidator SIGN_IN_VALIDATOR = new SignInValidator();
    private static final PasswordResetValidator PASSWORD_RESET_VALIDATOR = new PasswordResetValidator();
    private static final EmailValidator EMAIL_VALIDATOR = new EmailValidator();

    @Mock
    private DistributedLockDao lockDao;
    @Mock
    private CacheProvider cacheProvider;
    @Mock
    private BridgeConfig config;
    @Mock
    private ConsentService consentService;
    @Mock
    private ParticipantOptionsService optionsService;
    @Mock
    private AccountDao accountDao;
    @Mock
    private HealthCodeService healthCodeService;
    @Mock
    private StudyEnrollmentService studyEnrollmentService;

    private AuthenticationService authService;

    private Study study;

    private UserSession session;

    private SignIn signIn;

    private User user;

    private Account account;

    @Before
    public void before() {
        study = new DynamoStudy();
        study.setIdentifier(STUDY_ID);

        study.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);
        study.setDataGroups(DATA_GROUPS);

        session = new UserSession();
        session.setSessionToken(SESSION_TOKEN);

        user = new User();
        user.setUsername(USERNAME);
        user.setStudyKey(STUDY_ID);
        user.setId("userId");
        user.setEmail(EMAIL);
        user.setSharingScope(SharingScope.NO_SHARING);
        session.setUser(user);

        signIn = new SignIn(USERNAME, PASSWORD);

        account = mock(Account.class);
        when(account.getUsername()).thenReturn(USERNAME);
        when(account.getEmail()).thenReturn(EMAIL);
        when(account.getId()).thenReturn(HEALTH_ID);
        when(accountDao.authenticate(study, signIn)).thenReturn(account);

        HealthId healthId = mock(HealthId.class);
        when(healthId.getCode()).thenReturn(HEALTH_CODE);
        when(healthId.getId()).thenReturn(HEALTH_ID);
        when(healthCodeService.createMapping(study)).thenReturn(healthId);

        when(healthCodeService.getMapping(HEALTH_ID)).thenReturn(healthId);

        when(cacheProvider.getUserSession(SESSION_TOKEN)).thenReturn(session);

        when(optionsService.getEnum(HEALTH_CODE, ParticipantOption.SHARING_SCOPE, SharingScope.class))
                .thenReturn(SharingScope.NO_SHARING);

        authService = new AuthenticationService();
        authService.setDistributedLockDao(lockDao);
        authService.setCacheProvider(cacheProvider);
        authService.setBridgeConfig(config);
        authService.setConsentService(consentService);
        authService.setOptionsService(optionsService);
        authService.setAccountDao(accountDao);
        authService.setHealthCodeService(healthCodeService);
        authService.setStudyEnrollmentService(studyEnrollmentService);
        authService.setEmailVerificationValidator(VERIFICATION_VALIDATOR);
        authService.setSignInValidator(SIGN_IN_VALIDATOR);
        authService.setPasswordResetValidator(PASSWORD_RESET_VALIDATOR);
        authService.setEmailValidator(EMAIL_VALIDATOR);
    }

    @Test(expected = BridgeServiceException.class)
    public void signInNoUsername() throws Exception {
        when(accountDao.authenticate(study, signIn)).thenThrow(mock(BridgeServiceException.class));

        authService.signIn(study, ClientInfo.UNKNOWN_CLIENT, new SignIn(null, "bar"));
        verify(cacheProvider, never()).setUserSession(any());
    }

    @Test(expected = BridgeServiceException.class)
    public void signInNoPassword() throws Exception {
        when(accountDao.authenticate(study, signIn)).thenThrow(mock(BridgeServiceException.class));

        authService.signIn(study, ClientInfo.UNKNOWN_CLIENT, new SignIn("foobar", null));
        verify(cacheProvider, never()).setUserSession(any());
    }

    @Test(expected = EntityNotFoundException.class)
    public void signInInvalidCredentials() throws Exception {
        when(accountDao.authenticate(study, signIn)).thenThrow(mock(EntityNotFoundException.class));

        authService.signIn(study, ClientInfo.UNKNOWN_CLIENT, signIn);
        verify(cacheProvider, never()).setUserSession(any());
    }

    @Test
    public void signInCorrectCredentials() throws Exception {
        UserSession session = authService.signIn(study, ClientInfo.UNKNOWN_CLIENT, signIn);

        assertEquals("Username is for test2 user", session.getUser().getUsername(), user.getUsername());
        assertTrue("Session token has been assigned", StringUtils.isNotBlank(session.getSessionToken()));
    }

    @Test
    public void signInWhenSignedIn() throws Exception {
        // session exists in cache
        when(cacheProvider.getUserSessionByUserId(any())).thenReturn(session);

        String sessionToken = session.getSessionToken();
        UserSession newSession = authService.signIn(study, ClientInfo.UNKNOWN_CLIENT, signIn);
        assertEquals("Username is for test2 user", user.getUsername(), newSession.getUser().getUsername());
        assertEquals("Should update the existing session instead of creating a new one.", sessionToken,
                newSession.getSessionToken());
    }

    @Test
    public void signInSetsSharingScope() {
        UserSession newSession = authService.signIn(study, ClientInfo.UNKNOWN_CLIENT, signIn);
        assertEquals(SharingScope.NO_SHARING, newSession.getUser().getSharingScope()); // this is the default.
    }

    @Test
    public void getSessionWithBogusSessionToken() throws Exception {
        UserSession session = authService.getSession("anytoken");
        assertNull("Session is null", session);

        session = authService.getSession(null);
        assertNull("Session is null", session);
    }

    @Test
    public void getSessionWhenAuthenticated() throws Exception {
        UserSession newSession = authService.getSession(session.getSessionToken());

        assertEquals("Username is for test2 user", user.getUsername(), newSession.getUser().getUsername());
        assertTrue("Session token has been assigned", StringUtils.isNotBlank(newSession.getSessionToken()));
    }

    @Test(expected = NullPointerException.class)
    public void requestPasswordResetFailsOnNull() throws Exception {
        authService.requestResetPassword(study, null);
    }

    @Test(expected = InvalidEntityException.class)
    public void requestPasswordResetFailsOnEmptyString() throws Exception {
        Email email = new Email(study.getStudyIdentifier(), "");
        authService.requestResetPassword(study, email);
    }

    @Test(expected = EntityNotFoundException.class)
    public void resetPasswordWithBadTokenFails() throws Exception {
        doThrow(mock(EntityNotFoundException.class)).when(accountDao).resetPassword(any());

        authService.resetPassword(new PasswordReset("newpassword", "resettoken"));
    }

    @Test
    public void canResendEmailVerification() throws Exception {
        Email email = new Email(study.getStudyIdentifier(), user.getEmail());
        authService.resendEmailVerification(study.getStudyIdentifier(), email);
    }

    @Test
    public void createResearcherAndSignInWithoutConsentError() {
        user.setRoles(Sets.newHashSet(Roles.RESEARCHER));

        authService.signIn(study, ClientInfo.UNKNOWN_CLIENT, signIn);
    }

    @Test
    public void createAdminAndSignInWithoutConsentError() {
        user.setRoles(Sets.newHashSet(Roles.ADMIN));

        authService.signIn(study, ClientInfo.UNKNOWN_CLIENT, signIn);
    }

    @Test
    public void testSignOut() {
        authService.signOut(session);

        verify(cacheProvider).removeSession(session);
    }

    @Test
    public void signUpWillCreateDataGroups() {
        Set<String> list = Sets.newHashSet("group1");
        SignUp signUp = new SignUp(USERNAME, EMAIL, PASSWORD, null, list);

        when(accountDao.signUp(study, signUp, true)).thenReturn(account);

        authService.signUp(study, signUp, true);
        verify(optionsService).setStringSet(eq(study), any(), eq(ParticipantOption.DATA_GROUPS), eq(list));
    }

    @Test
    public void userCreatedWithDataGroupsHasThemOnSignIn() throws Exception {
        Set<String> userDataGroups = Sets.newHashSet(DATA_GROUPS.iterator().next());
        when(optionsService.getStringSet(HEALTH_CODE, ParticipantOption.DATA_GROUPS)).thenReturn(userDataGroups);

        UserSession session = authService.signIn(study, ClientInfo.UNKNOWN_CLIENT, signIn);

        assertEquals(userDataGroups, session.getUser().getDataGroups());
    }

    // This verifies that the SignUpValidatorIsUsed... TODO: tests for all other validators
    @Test // aka signUpValidatorIsUsed
    public void invalidDataGroupsAreRejected() throws Exception {
        try {
            SignUp signUp = new SignUp(USERNAME, EMAIL, PASSWORD, null, Sets.newHashSet("bugleboy"));
            authService.signUp(study, signUp, true);
            fail("Should have thrown exception");
        } catch (InvalidEntityException e) {
            assertTrue(e.getMessage().contains("dataGroups 'bugleboy' is not one of these valid values"));
        }
    }

    @Test(expected = InvalidEntityException.class)
    public void emailVerificationValidatorIsUsed() {
        EmailVerification verification = new EmailVerification(null);

        authService.verifyEmail(study, ClientInfo.UNKNOWN_CLIENT, verification);
    }

    @Test(expected = InvalidEntityException.class)
    public void signInValidatorIsUsed() {
        SignIn signIn = new SignIn(USERNAME, "");
        
        authService.signIn(study, ClientInfo.UNKNOWN_CLIENT, signIn);
    }

    @Test(expected = InvalidEntityException.class)
    public void passwordResetValidatorIsUsed() {
        PasswordReset reset = new PasswordReset("", "asdf");
        
        authService.resetPassword(reset);
    }

    @Test(expected = InvalidEntityException.class)
    public void emailValidatorIsUsedOnEmailVerification() {
        Email email = new Email(study, "");
        
        authService.resendEmailVerification(study.getStudyIdentifier(), email);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void emailValidatorIsUsedOnResetPassword() {
        Email email = new Email(study, "");
        
        authService.requestResetPassword(study, email);
    }

    // Account enumeration security. Verify the service is quite (throws no exceptions) when we don't
    // recognize an account.

    @Test
    public void secondSignUpTriggersResetPasswordInstead() {
        SignUp signUp = new SignUp(USERNAME, EMAIL, PASSWORD, null, null);

        when(accountDao.signUp(study, signUp, true)).thenThrow(mock(EntityAlreadyExistsException.class));

        authService.signUp(study, signUp, true);
        verify(accountDao).requestResetPassword(any(Study.class), any(Email.class));
    }

    @Test
    public void secondSignUpWithUsernameButDifferentEmailThrowsException() {
        SignUp signUp = new SignUp(USERNAME, EMAIL, PASSWORD, null, null);

        EntityAlreadyExistsException eaee = new EntityAlreadyExistsException(mock(Account.class), "");
        when(accountDao.signUp(study, signUp, true)).thenThrow(eaee);
        doThrow(mock(EntityNotFoundException.class)).when(accountDao).requestResetPassword(eq(study), any());

        try {
            authService.signUp(study, signUp, true);
            fail("Should have thrown an exception");
        } catch (EntityAlreadyExistsException e) {
            assertEquals("Username already exists.", e.getMessage());
        }
    }

    // In particular, it must not throw an EntityNotFoundException
    @Test
    public void resendEmailVerificationLooksSuccessfulWhenNoAccount() throws Exception {
        Email email = new Email(study.getStudyIdentifier(), "notarealaccount@sagebase.org");
        doThrow(new EntityNotFoundException(Account.class)).when(accountDao).resendEmailVerificationToken(any(), any());

        authService.resendEmailVerification(study.getStudyIdentifier(), email);
    }

    @Test
    public void requestResetPasswordLooksSuccessfulWhenNoAccount() throws Exception {
        Email email = new Email(study.getStudyIdentifier(), "notarealaccount@sagebase.org");
        doThrow(new EntityNotFoundException(Account.class)).when(accountDao).requestResetPassword(any(), any());

        authService.requestResetPassword(study, email);
    }

    // Consent statuses passed on to sessionInfo
    @Test
    public void consentStatusesPresentInSession() {
        SubpopulationGuid guid = SubpopulationGuid.create(study.getIdentifier());
        Map<SubpopulationGuid, ConsentStatus> statuses = Maps.newHashMap();
        statuses.put(guid, new ConsentStatus.Builder().withName("Name").withConsented(true).withGuid(guid).build());

        when(consentService.getConsentStatuses(any())).thenReturn(statuses);

        UserSession newSession = authService.signIn(study, ClientInfo.UNKNOWN_CLIENT, signIn);

        ConsentStatus status = Iterables.getFirst(newSession.getUser().getConsentStatuses().values(), null);
        assertTrue(status.isConsented());
        assertEquals(study.getStudyIdentifier().getIdentifier(), status.getSubpopulationGuid());
    }
}
