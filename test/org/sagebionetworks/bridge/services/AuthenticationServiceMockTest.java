package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.sagebionetworks.bridge.BridgeConstants.NO_CALLER_ROLES;

import java.util.LinkedHashSet;
import java.util.Map;

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
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.exceptions.AccountDisabledException;
import org.sagebionetworks.bridge.exceptions.AuthenticationFailedException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.Tuple;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.AccountStatus;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.Verification;
import org.sagebionetworks.bridge.models.accounts.GenericAccount;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.AuthenticationService.ChannelType;
import org.sagebionetworks.bridge.services.email.BasicEmailProvider;
import org.sagebionetworks.bridge.validators.PasswordResetValidator;
import org.sagebionetworks.bridge.validators.SignInValidator;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

@RunWith(MockitoJUnitRunner.class)
public class AuthenticationServiceMockTest {
    
    private static final String SUPPORT_EMAIL = "support@support.com";
    private static final String STUDY_ID = TestConstants.TEST_STUDY_IDENTIFIER;
    private static final String RECIPIENT_EMAIL = "email@email.com";
    private static final String TOKEN = "ABC-DEF";
    private static final String REAUTH_TOKEN = "GHI-JKL";
    private static final String REAUTH_CACHE_TOKEN = TOKEN+":api:reauthCacheKey";
    private static final String USER_ID = "user-id";
    private static final String PASSWORD = "Password~!1";
    private static final SignIn SIGN_IN_REQUEST_WITH_EMAIL = new SignIn.Builder().withStudy(STUDY_ID)
            .withEmail(RECIPIENT_EMAIL).build();
    private static final SignIn SIGN_IN_WITH_EMAIL = new SignIn.Builder().withStudy(STUDY_ID).withEmail(RECIPIENT_EMAIL)
            .withToken(TOKEN).build();
    private static final SignIn SIGN_IN_WITH_PHONE = new SignIn.Builder().withStudy(STUDY_ID)
            .withPhone(TestConstants.PHONE).withToken(TOKEN).build();
    
    private static final SignIn EMAIL_PASSWORD_SIGN_IN = new SignIn.Builder().withStudy(STUDY_ID).withEmail(RECIPIENT_EMAIL)
            .withPassword(PASSWORD).build();
    private static final SignIn PHONE_PASSWORD_SIGN_IN = new SignIn.Builder().withStudy(STUDY_ID)
            .withPhone(TestConstants.PHONE).withPassword(PASSWORD).build();
    private static final SignIn REAUTH_REQUEST = new SignIn.Builder().withStudy(STUDY_ID).withEmail(RECIPIENT_EMAIL)
            .withReauthToken(TOKEN).build();
    
    private static final SubpopulationGuid SUBPOP_GUID = SubpopulationGuid.create("ABC");
    private static final ConsentStatus CONSENTED_STATUS = new ConsentStatus.Builder().withName("Name")
            .withGuid(SUBPOP_GUID).withRequired(true).withConsented(true).build();
    private static final ConsentStatus UNCONSENTED_STATUS = new ConsentStatus.Builder().withName("Name")
            .withGuid(SUBPOP_GUID).withRequired(true).withConsented(false).build();
    private static final Map<SubpopulationGuid, ConsentStatus> CONSENTED_STATUS_MAP = new ImmutableMap.Builder<SubpopulationGuid, ConsentStatus>()
            .put(SUBPOP_GUID, CONSENTED_STATUS).build();
    private static final Map<SubpopulationGuid, ConsentStatus> UNCONSENTED_STATUS_MAP = new ImmutableMap.Builder<SubpopulationGuid, ConsentStatus>()
            .put(SUBPOP_GUID, UNCONSENTED_STATUS).build();
    private static final CriteriaContext CONTEXT = new CriteriaContext.Builder()
            .withStudyIdentifier(TestConstants.TEST_STUDY).build();
    private static final StudyParticipant PARTICIPANT = new StudyParticipant.Builder().build();
    private static final AccountId ACCOUNT_ID = AccountId.forId(STUDY_ID, USER_ID);

    @Mock
    private CacheProvider cacheProvider;
    @Mock
    private BridgeConfig config;
    @Mock
    private ConsentService consentService;
    @Mock
    private AccountDao accountDao;
    @Mock
    private ParticipantService participantService;
    @Mock
    private StudyService studyService;
    @Mock
    private PasswordResetValidator passwordResetValidator;
    @Mock
    private AccountWorkflowService accountWorkflowService;
    @Captor
    private ArgumentCaptor<String> stringCaptor;
    @Captor
    private ArgumentCaptor<BasicEmailProvider> providerCaptor;
    @Captor
    private ArgumentCaptor<UserSession> sessionCaptor;
    @Captor
    private ArgumentCaptor<StudyParticipant> participantCaptor;
    @Captor
    private ArgumentCaptor<AccountId> accountIdCaptor;
    @Captor
    private ArgumentCaptor<Tuple<String>> tupleCaptor;
    @Spy
    private AuthenticationService service;

    private Study study;

    private Account account;

    @Before
    public void before() {
        study = Study.create();
        study.setIdentifier(STUDY_ID);
        study.setSupportEmail(SUPPORT_EMAIL);
        study.setName("Sender");
        
        account = new GenericAccount();
        
        service.setCacheProvider(cacheProvider);
        service.setBridgeConfig(config);
        service.setConsentService(consentService);
        service.setAccountDao(accountDao);
        service.setPasswordResetValidator(passwordResetValidator);
        service.setParticipantService(participantService);
        service.setStudyService(studyService);
        service.setAccountWorkflowService(accountWorkflowService);

        doReturn(study).when(studyService).getStudy(STUDY_ID);
    }
    
    @Test
    public void signInWithEmail() throws Exception {
        account.setReauthToken(REAUTH_TOKEN);
        doReturn(account).when(accountDao).authenticate(study, EMAIL_PASSWORD_SIGN_IN);
        doReturn(PARTICIPANT).when(participantService).getParticipant(study, account, false);
        doReturn(CONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), any());

        UserSession retrieved = service.signIn(study, CONTEXT, EMAIL_PASSWORD_SIGN_IN);
        
        assertEquals(REAUTH_TOKEN, retrieved.getReauthToken());
        verify(cacheProvider).setUserSession(retrieved);
    }
    
    @Test(expected = ConsentRequiredException.class)
    public void unconsentedSignInWithEmail() throws Exception {
        doReturn(account).when(accountDao).authenticate(study, EMAIL_PASSWORD_SIGN_IN);
        doReturn(PARTICIPANT).when(participantService).getParticipant(study, account, false);
        doReturn(UNCONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), any());
        
        service.signIn(study, CONTEXT, EMAIL_PASSWORD_SIGN_IN);
    }
    
    @Test
    public void adminSignInWithEmail() throws Exception {
        account.setReauthToken(REAUTH_TOKEN);
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withRoles(Sets.newHashSet(Roles.DEVELOPER)).build();
        doReturn(account).when(accountDao).authenticate(study, EMAIL_PASSWORD_SIGN_IN);
        doReturn(participant).when(participantService).getParticipant(study, account, false);
        doReturn(UNCONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any());
        
        // Does not throw consent required exception, despite being unconsented, because user has DEVELOPER role.
        UserSession retrieved = service.signIn(study, CONTEXT, EMAIL_PASSWORD_SIGN_IN);
        
        assertEquals(REAUTH_TOKEN, retrieved.getReauthToken());
    }
    
    @Test
    public void signInWithPhone() throws Exception {
        account.setReauthToken(REAUTH_TOKEN);
        doReturn(account).when(accountDao).authenticate(study, PHONE_PASSWORD_SIGN_IN);
        doReturn(PARTICIPANT).when(participantService).getParticipant(study, account, false);
        doReturn(CONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), any());

        UserSession retrieved = service.signIn(study, CONTEXT, PHONE_PASSWORD_SIGN_IN);
        
        assertEquals(REAUTH_TOKEN, retrieved.getReauthToken());
        verify(cacheProvider).setUserSession(retrieved);
    }
    
    @Test(expected = ConsentRequiredException.class)
    public void unconsentedSignInWithPhone() throws Exception {
        doReturn(account).when(accountDao).authenticate(study, PHONE_PASSWORD_SIGN_IN);
        doReturn(PARTICIPANT).when(participantService).getParticipant(study, account, false);
        doReturn(UNCONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), any());
        
        service.signIn(study, CONTEXT, PHONE_PASSWORD_SIGN_IN);
    }
    
    @Test
    public void adminSignInWithPhone() throws Exception {
        account.setReauthToken(REAUTH_TOKEN);
        StudyParticipant participant = new StudyParticipant.Builder()
                .copyOf(PARTICIPANT).withRoles(Sets.newHashSet(Roles.RESEARCHER)).build();
        doReturn(account).when(accountDao).authenticate(study, PHONE_PASSWORD_SIGN_IN);
        doReturn(participant).when(participantService).getParticipant(study, account, false);
        doReturn(UNCONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any());
        
        // Does not throw consent required exception, despite being unconsented, because user has RESEARCHER role. 
        UserSession retrieved = service.signIn(study, CONTEXT, PHONE_PASSWORD_SIGN_IN);

        assertEquals(REAUTH_TOKEN, retrieved.getReauthToken());
    }
    
    @Test
    public void signOut() {
        StudyIdentifier studyIdentifier = new StudyIdentifierImpl(STUDY_ID);
        
        UserSession session = new UserSession();
        session.setStudyIdentifier(studyIdentifier);
        session.setReauthToken("reauthToken");
        session.setParticipant(new StudyParticipant.Builder().withEmail("email@email.com").withId(USER_ID).build());
        service.signOut(session);
        
        verify(accountDao).deleteReauthToken(ACCOUNT_ID);
        verify(cacheProvider).removeSession(session);
        verify(cacheProvider).removeObject("reauthToken:api:reauthCacheKey");
    }
    
    @Test
    public void signOutNoSessionToken() {
        service.signOut(null);
        
        verify(accountDao, never()).deleteReauthToken(any());
        verify(cacheProvider, never()).removeSession(any());
    }
    
    @Test
    public void emailSignIn() {
        account.setReauthToken(REAUTH_TOKEN);
        doReturn(SIGN_IN_WITH_EMAIL.getAccountId()).when(accountWorkflowService).channelSignIn(ChannelType.EMAIL,
                CONTEXT, SIGN_IN_WITH_EMAIL, SignInValidator.EMAIL_SIGNIN);
        doReturn(account).when(accountDao).getAccountAfterAuthentication(SIGN_IN_WITH_EMAIL.getAccountId());
        doReturn(PARTICIPANT).when(participantService).getParticipant(study, account, false);
        doReturn(CONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), any());

        UserSession retSession = service.emailSignIn(CONTEXT, SIGN_IN_WITH_EMAIL);
        
        assertNotNull(retSession);
        assertEquals(REAUTH_TOKEN, retSession.getReauthToken());
        verify(accountDao).getAccountAfterAuthentication(SIGN_IN_WITH_EMAIL.getAccountId());
        verify(accountDao).verifyChannel(AuthenticationService.ChannelType.EMAIL, account);
        verify(cacheProvider).setUserSession(retSession);
    }
    
    @Test(expected = AuthenticationFailedException.class)
    public void emailSignInAuthenticationFailed() {
        doThrow(new AuthenticationFailedException()).when(accountWorkflowService).channelSignIn(ChannelType.EMAIL,
                CONTEXT, SIGN_IN_WITH_EMAIL, SignInValidator.EMAIL_SIGNIN);
        
        service.emailSignIn(CONTEXT, SIGN_IN_WITH_EMAIL);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void emailSignInInvalidEntity() {
        doThrow(new InvalidEntityException("")).when(accountWorkflowService).channelSignIn(ChannelType.EMAIL, CONTEXT,
                SIGN_IN_REQUEST_WITH_EMAIL, SignInValidator.EMAIL_SIGNIN);

        service.emailSignIn(CONTEXT, SIGN_IN_REQUEST_WITH_EMAIL);
    }
    
    @Test(expected = AccountDisabledException.class)
    public void emailSignInThrowsAccountDisabled() {
        account.setStatus(AccountStatus.DISABLED);
        
        doReturn(SIGN_IN_WITH_EMAIL.getAccountId()).when(accountWorkflowService).channelSignIn(ChannelType.EMAIL,
                CONTEXT, SIGN_IN_WITH_EMAIL, SignInValidator.EMAIL_SIGNIN);
        doReturn(account).when(accountDao).getAccountAfterAuthentication(SIGN_IN_WITH_EMAIL.getAccountId());
        
        service.emailSignIn(CONTEXT, SIGN_IN_WITH_EMAIL);
    }
    
    @Test
    public void emailSignInThrowsConsentRequired() {
        StudyParticipant participant = new StudyParticipant.Builder().withStatus(AccountStatus.DISABLED).build();

        doReturn(SIGN_IN_WITH_EMAIL.getAccountId()).when(accountWorkflowService).channelSignIn(ChannelType.EMAIL,
                CONTEXT, SIGN_IN_WITH_EMAIL, SignInValidator.EMAIL_SIGNIN);
        doReturn(account).when(accountDao).getAccountAfterAuthentication(SIGN_IN_WITH_EMAIL.getAccountId());
        doReturn(participant).when(participantService).getParticipant(study, account, false);
        doReturn(UNCONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any());
        
        try {
            service.emailSignIn(CONTEXT, SIGN_IN_WITH_EMAIL);
            fail("Should have thrown exception");
        } catch(ConsentRequiredException e) {
            verify(cacheProvider).setUserSession(e.getUserSession());    
        }
    }
    
    @Test
    public void emailSignInAdminOK() {
        StudyParticipant participant = new StudyParticipant.Builder().withRoles(Sets.newHashSet(Roles.ADMIN)).build();
        
        doReturn(participant).when(participantService).getParticipant(study, account, false);
        doReturn(SIGN_IN_WITH_EMAIL.getAccountId()).when(accountWorkflowService).channelSignIn(ChannelType.EMAIL,
                CONTEXT, SIGN_IN_WITH_EMAIL, SignInValidator.EMAIL_SIGNIN);
        doReturn(account).when(accountDao).getAccountAfterAuthentication(SIGN_IN_WITH_EMAIL.getAccountId());
        doReturn(UNCONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any());
        
        // Does not throw a consent required exception because the participant is an admin. 
        service.emailSignIn(CONTEXT, SIGN_IN_WITH_EMAIL);
    }
    
    @Test
    public void reauthentication() {
        ((GenericAccount)account).setId(USER_ID);
        account.setReauthToken(REAUTH_TOKEN);

        StudyParticipant participant = new StudyParticipant.Builder().withEmail(RECIPIENT_EMAIL).build();
        doReturn(CONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), any());
        doReturn(account).when(accountDao).reauthenticate(study, REAUTH_REQUEST);
        doReturn(participant).when(participantService).getParticipant(study, account, false);
        
        UserSession session = service.reauthenticate(study, CONTEXT, REAUTH_REQUEST);
        assertEquals(RECIPIENT_EMAIL, session.getParticipant().getEmail());
        assertEquals(REAUTH_TOKEN, session.getReauthToken());
        
        verify(accountDao).reauthenticate(study, REAUTH_REQUEST);
        verify(cacheProvider).removeSessionByUserId(USER_ID);
        verify(cacheProvider).setUserSession(sessionCaptor.capture());
        
        UserSession captured = sessionCaptor.getValue();
        assertEquals(RECIPIENT_EMAIL, captured.getParticipant().getEmail());
        assertEquals(REAUTH_TOKEN, captured.getReauthToken());
        verify(cacheProvider).setObject(eq(REAUTH_CACHE_TOKEN), tupleCaptor.capture(),
                eq(BridgeConstants.BRIDGE_REAUTH_GRACE_PERIOD));
        
        Tuple<String> tuple = tupleCaptor.getValue();
        assertEquals(session.getSessionToken(), tuple.getLeft());
        assertEquals(REAUTH_TOKEN, tuple.getRight());
    }
    
    @Test
    public void reauthenticationFromCache() {
        Tuple<String> tuple = new Tuple<String>(TOKEN, "newReauthToken");
        
        UserSession session = new UserSession();
        doReturn(tuple).when(cacheProvider).getObject(REAUTH_CACHE_TOKEN, AuthenticationService.TUPLE_TYPE);
        doReturn(session).when(cacheProvider).getUserSession(TOKEN);
        
        UserSession returned = service.reauthenticate(study, CONTEXT, REAUTH_REQUEST);
        assertEquals(session, returned);
        assertEquals("newReauthToken", session.getReauthToken());
        
        // We don't have to retrieve this.
        verify(accountDao, never()).reauthenticate(any(), any());
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void reauthenticationWithoutSessionThrows() {
        Tuple<String> tuple = new Tuple<>("left", "right");
        doReturn(tuple).when(cacheProvider).getObject(REAUTH_CACHE_TOKEN, AuthenticationService.TUPLE_TYPE);
        
        service.reauthenticate(study, CONTEXT, REAUTH_REQUEST);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void reauthTokenRequired() {
        service.reauthenticate(study, CONTEXT, SIGN_IN_WITH_EMAIL); // doesn't have reauth token
    }
    
    @Test(expected = ConsentRequiredException.class)
    public void reauthThrowsUnconsentedException() {
        StudyParticipant participant = new StudyParticipant.Builder().withStatus(AccountStatus.ENABLED).build();
        
        doReturn(UNCONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any());
        doReturn(account).when(accountDao).reauthenticate(study, REAUTH_REQUEST);
        doReturn(participant).when(participantService).getParticipant(study, account, false);
        
        service.reauthenticate(study, CONTEXT, REAUTH_REQUEST);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void requestResetInvalid() throws Exception {
        SignIn signIn = new SignIn.Builder().withStudy(STUDY_ID).withPhone(TestConstants.PHONE)
                .withEmail(RECIPIENT_EMAIL).build();
        service.requestResetPassword(study, signIn);
    }
    
    @Test
    public void requestResetPassword() throws Exception {
        SignIn signIn = new SignIn.Builder().withStudy(STUDY_ID).withEmail(RECIPIENT_EMAIL).build();
        
        service.requestResetPassword(study, signIn);
        
        verify(accountWorkflowService).requestResetPassword(study, signIn.getAccountId());
    }
    
    @Test
    public void signUpWithEmailOK() throws Exception {
        study.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);
        StudyParticipant participant = new StudyParticipant.Builder().withEmail(RECIPIENT_EMAIL).withPassword(PASSWORD)
                .build();
        
        service.signUp(study, participant, false);
        
        verify(participantService).createParticipant(eq(study), eq(NO_CALLER_ROLES), participantCaptor.capture(), eq(true));
        StudyParticipant captured = participantCaptor.getValue();
        assertEquals(RECIPIENT_EMAIL, captured.getEmail());
        assertEquals(PASSWORD, captured.getPassword());
    }

    @Test
    public void signUpWithPhoneOK() throws Exception {
        study.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);
        StudyParticipant participant = new StudyParticipant.Builder().withPhone(TestConstants.PHONE)
                .withPassword(PASSWORD).build();
        
        service.signUp(study, participant, false);
        
        verify(participantService).createParticipant(eq(study), eq(NO_CALLER_ROLES), participantCaptor.capture(), eq(true));
        StudyParticipant captured = participantCaptor.getValue();
        assertEquals(TestConstants.PHONE.getNumber(), captured.getPhone().getNumber());
        assertEquals(PASSWORD, captured.getPassword());
    }
    
    @Test
    public void signUpExistingAccount() throws Exception {
        study.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);
        StudyParticipant participant = new StudyParticipant.Builder().withEmail(RECIPIENT_EMAIL).withPassword(PASSWORD)
                .build();
        doThrow(new EntityAlreadyExistsException(StudyParticipant.class, "userId", "AAA")).when(participantService)
                .createParticipant(study, NO_CALLER_ROLES, participant, true);
        
        service.signUp(study, participant, false);
        
        ArgumentCaptor<AccountId> accountIdCaptor = ArgumentCaptor.forClass(AccountId.class);
        
        verify(participantService).createParticipant(eq(study), eq(NO_CALLER_ROLES), any(), eq(true));
        verify(accountWorkflowService).notifyAccountExists(eq(study), accountIdCaptor.capture());
        
        AccountId captured = accountIdCaptor.getValue();
        assertEquals("AAA", captured.getId());
        assertEquals(STUDY_ID, captured.getStudyId());
    }
    
    @Test
    public void phoneSignIn() {
        // Put some stuff in participant to verify session is initialized
        StudyParticipant participant = new StudyParticipant.Builder()
                .withEmail(RECIPIENT_EMAIL).withFirstName("Test").withLastName("Tester").build();
        doReturn(participant).when(participantService).getParticipant(study, account, false);
        doReturn(SIGN_IN_WITH_PHONE.getAccountId()).when(accountWorkflowService).channelSignIn(ChannelType.PHONE,
                CONTEXT, SIGN_IN_WITH_PHONE, SignInValidator.PHONE_SIGNIN);
        doReturn(account).when(accountDao).getAccountAfterAuthentication(SIGN_IN_WITH_PHONE.getAccountId());
        doReturn(CONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), any());
        
        UserSession session = service.phoneSignIn(CONTEXT, SIGN_IN_WITH_PHONE);

        assertEquals(RECIPIENT_EMAIL, session.getParticipant().getEmail());
        assertEquals("Test", session.getParticipant().getFirstName());
        assertEquals("Tester", session.getParticipant().getLastName());
        
        // this doesn't pass if our mock calls above aren't executed, but verify these:
        verify(cacheProvider).setUserSession(session);
        verify(accountDao).verifyChannel(ChannelType.PHONE, account);
    }

    @Test(expected = AuthenticationFailedException.class)
    public void phoneSignInFails() {
        doThrow(new AuthenticationFailedException()).when(accountWorkflowService).channelSignIn(ChannelType.PHONE,
                CONTEXT, SIGN_IN_WITH_PHONE, SignInValidator.PHONE_SIGNIN);
        
        service.phoneSignIn(CONTEXT, SIGN_IN_WITH_PHONE);
    }
    
    @Test
    public void phoneSignInThrowsConsentRequired() {
        // Put some stuff in participant to verify session is initialized
        StudyParticipant participant = new StudyParticipant.Builder()
                .withEmail(RECIPIENT_EMAIL).withFirstName("Test").withLastName("Tester").build();
        doReturn(participant).when(participantService).getParticipant(study, account, false);
        doReturn(SIGN_IN_WITH_PHONE.getAccountId()).when(accountWorkflowService).channelSignIn(ChannelType.PHONE,
                CONTEXT, SIGN_IN_WITH_PHONE, SignInValidator.PHONE_SIGNIN);
        doReturn(account).when(accountDao).getAccountAfterAuthentication(SIGN_IN_WITH_PHONE.getAccountId());
        doReturn(UNCONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any());
        
        try {
            service.phoneSignIn(CONTEXT, SIGN_IN_WITH_PHONE);
            fail("Should have thrown exception");
        } catch(ConsentRequiredException e) {
            verify(cacheProvider).setUserSession(e.getUserSession());    
        }
    }
    
    @Test
    public void verifyEmail() {
        Verification ev = new Verification("sptoken");
        doReturn(account).when(accountWorkflowService).verifyChannel(ChannelType.EMAIL, ev);
        
        service.verifyChannel(ChannelType.EMAIL, ev);
        
        verify(accountWorkflowService).verifyChannel(ChannelType.EMAIL, ev);
        verify(accountDao).verifyChannel(ChannelType.EMAIL, account);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void verifyEmailInvalid() {
        Verification ev = new Verification(null);
        service.verifyChannel(ChannelType.EMAIL, ev);
    }
    
    @Test
    public void verifyPhone() {
        Verification ev = new Verification("sptoken");
        doReturn(account).when(accountWorkflowService).verifyChannel(ChannelType.PHONE, ev);
        
        service.verifyChannel(ChannelType.PHONE, ev);
        
        verify(accountWorkflowService).verifyChannel(ChannelType.PHONE, ev);
        verify(accountDao).verifyChannel(ChannelType.PHONE, account);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void verifyPhoneInvalid() {
        Verification ev = new Verification(null);
        service.verifyChannel(ChannelType.PHONE, ev);
    }
    
    @Test
    public void languagesArePersistedFromContext() {
        // This specifically has to be a mock to easily mock the editAccount method on the DAO.
        Account mockAccount = mock(Account.class);

        LinkedHashSet<String> languages = TestUtils.newLinkedHashSet("es","de");
        CriteriaContext context = new CriteriaContext.Builder().withLanguages(languages).withUserId(USER_ID)
                .withStudyIdentifier(TestConstants.TEST_STUDY).build();
        TestUtils.mockEditAccount(accountDao, mockAccount);
        doReturn(mockAccount).when(accountDao).getAccount(any());
        
        // No languages.
        StudyParticipant participant = new StudyParticipant.Builder().withHealthCode("healthCode").build();
        doReturn(participant).when(participantService).getParticipant(study, mockAccount, false);
        
        service.getSession(study, context);
        
        verify(accountDao).editAccount(eq(TestConstants.TEST_STUDY), eq("healthCode"), any());
        verify(mockAccount).setLanguages(languages);
    }

    @Test
    public void resendEmailVerification() {
        AccountId accountId = AccountId.forEmail(TestConstants.TEST_STUDY_IDENTIFIER, RECIPIENT_EMAIL);
        service.resendVerification(ChannelType.EMAIL, accountId);
        
        verify(accountWorkflowService).resendVerificationToken(eq(ChannelType.EMAIL), accountIdCaptor.capture());
        
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, accountIdCaptor.getValue().getStudyId());
        assertEquals(RECIPIENT_EMAIL, accountIdCaptor.getValue().getEmail());
    }

    @Test(expected = InvalidEntityException.class)
    public void resendEmailVerificationInvalid() throws Exception {
        AccountId accountId = BridgeObjectMapper.get().readValue("{}", AccountId.class);
        service.resendVerification(ChannelType.EMAIL, accountId);
    }
    
    @Test
    public void resendPhoneVerification() {
        AccountId accountId = AccountId.forPhone(TestConstants.TEST_STUDY_IDENTIFIER, TestConstants.PHONE);
        service.resendVerification(ChannelType.PHONE, accountId);
        
        verify(accountWorkflowService).resendVerificationToken(eq(ChannelType.PHONE), accountIdCaptor.capture());
        
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, accountIdCaptor.getValue().getStudyId());
        assertEquals(TestConstants.PHONE, accountIdCaptor.getValue().getPhone());
    }
    
    @Test(expected = InvalidEntityException.class)
    public void resendPhoneVerificationInvalid() throws Exception {
        AccountId accountId = BridgeObjectMapper.get().readValue("{}", AccountId.class);
        service.resendVerification(ChannelType.PHONE, accountId);
    }
    
    @Test(expected = UnauthorizedException.class)
    public void creatingExternalIdOnlyAccountFailsIfIdsNotManaged() {
        study.setExternalIdValidationEnabled(false);
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withEmail(null).withPhone(null).withExternalId("id").build();
        service.signUp(study, participant, false);
    }
    
    @Test
    public void creatingExternalIdOnlyAccountSucceedsIfIdsManaged() {
        study.setExternalIdValidationEnabled(true);
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withEmail(null).withPhone(null).withExternalId("id").build();
        service.signUp(study, participant, false);
    }
}