package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.exceptions.AccountDisabledException;
import org.sagebionetworks.bridge.exceptions.AuthenticationFailedException;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.Tuple;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.AccountStatus;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.Verification;
import org.sagebionetworks.bridge.models.accounts.IdentifierHolder;
import org.sagebionetworks.bridge.models.accounts.GeneratedPassword;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.models.substudies.AccountSubstudy;
import org.sagebionetworks.bridge.services.AuthenticationService.ChannelType;
import org.sagebionetworks.bridge.validators.PasswordResetValidator;
import org.sagebionetworks.bridge.validators.SignInValidator;
import org.sagebionetworks.bridge.validators.Validate;
import org.sagebionetworks.bridge.validators.ValidatorUtils;
import org.springframework.validation.Errors;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

@RunWith(MockitoJUnitRunner.class)
public class AuthenticationServiceMockTest {
    private static final Set<String> DATA_GROUP_SET = ImmutableSet.of("group1", "group2");
    private static final String IP_ADDRESS = "ip-address";
    private static final List<String> LANGUAGES = ImmutableList.of("es","de");
    private static final String SUPPORT_EMAIL = "support@support.com";
    private static final String STUDY_ID = TestConstants.TEST_STUDY_IDENTIFIER;
    private static final String RECIPIENT_EMAIL = "email@email.com";
    private static final String TOKEN = "ABC-DEF";
    private static final String REAUTH_TOKEN = "GHI-JKL";
    private static final CacheKey REAUTH_CACHE_TOKEN = CacheKey.reauthCacheKey(TOKEN, "api");
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
    private static final AccountId ACCOUNT_ID = AccountId.forId(TestConstants.TEST_STUDY_IDENTIFIER, USER_ID);
    private static final String EXTERNAL_ID = "ext-id";
    private static final String HEALTH_CODE = "health-code";

    private static final StudyParticipant PARTICIPANT_WITH_ATTRIBUTES = new StudyParticipant.Builder().withId(USER_ID)
            .withHealthCode(HEALTH_CODE).withDataGroups(DATA_GROUP_SET).withSubstudyIds(TestConstants.USER_SUBSTUDY_IDS)
            .withLanguages(LANGUAGES).build();

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
    @Mock
    private ExternalIdService externalIdService;
    @Mock
    private IntentService intentService;
    @Captor
    private ArgumentCaptor<UserSession> sessionCaptor;
    @Captor
    private ArgumentCaptor<StudyParticipant> participantCaptor;
    @Captor
    private ArgumentCaptor<AccountId> accountIdCaptor;
    @Captor
    private ArgumentCaptor<Tuple<String>> tupleCaptor;
    @Captor
    private ArgumentCaptor<CriteriaContext> contextCaptor;
    @Spy
    private AuthenticationService service;

    private Study study;

    private Account account;

    @Before
    public void before() {
        // Create inputs.
        study = Study.create();
        study.setIdentifier(STUDY_ID);
        study.setSupportEmail(SUPPORT_EMAIL);
        study.setName("Sender");
        study.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);
        
        account = Account.create();

        // Wire up service.
        service.setCacheProvider(cacheProvider);
        service.setBridgeConfig(config);
        service.setConsentService(consentService);
        service.setAccountDao(accountDao);
        service.setPasswordResetValidator(passwordResetValidator);
        service.setParticipantService(participantService);
        service.setStudyService(studyService);
        service.setAccountWorkflowService(accountWorkflowService);
        service.setExternalIdService(externalIdService);
        service.setIntentToParticipateService(intentService);

        doReturn(study).when(studyService).getStudy(STUDY_ID);
    }
    
    @After
    public void after() {
        BridgeUtils.setRequestContext(RequestContext.NULL_INSTANCE);
    }
    
    @Test // Test some happy path stuff, like the correct initialization of the user session
    public void signIn() {
        AccountSubstudy as1 = AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, "substudyA", USER_ID);
        AccountSubstudy as2 = AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, "substudyB", USER_ID);
        
        when(service.getGuid()).thenReturn("SESSION_TOKEN");
        
        when(config.getEnvironment()).thenReturn(Environment.PROD);
        
        account.setReauthToken("REAUTH_TOKEN");
        account.setHealthCode(HEALTH_CODE);
        account.setAccountSubstudies(ImmutableSet.of(as1, as2));
        account.setId(USER_ID);
        
        CriteriaContext context = new CriteriaContext.Builder()
            .withStudyIdentifier(TestConstants.TEST_STUDY)
            .withLanguages(LANGUAGES)
            .withClientInfo(ClientInfo.fromUserAgentCache("app/13"))
            .withIpAddress("127.1.1.11").build();

        
        doReturn(account).when(accountDao).authenticate(study, EMAIL_PASSWORD_SIGN_IN);
        doReturn(PARTICIPANT_WITH_ATTRIBUTES).when(participantService).getParticipant(study, account, false);
        doReturn(CONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(contextCaptor.capture(), any());
        
        UserSession session = service.signIn(study, context, EMAIL_PASSWORD_SIGN_IN);
        
        verify(cacheProvider).setUserSession(session);
        verify(cacheProvider).removeSessionByUserId(USER_ID);
        
        assertEquals(CONSENTED_STATUS_MAP, session.getConsentStatuses());
        assertTrue(session.isAuthenticated());
        assertEquals("127.1.1.11", session.getIpAddress());
        assertEquals("SESSION_TOKEN", session.getSessionToken());
        assertEquals("SESSION_TOKEN", session.getInternalSessionToken());
        assertEquals("REAUTH_TOKEN", session.getReauthToken());
        assertEquals(Environment.PROD, session.getEnvironment());
        assertEquals(TestConstants.TEST_STUDY, session.getStudyIdentifier());

        // updated context
        CriteriaContext updatedContext = contextCaptor.getValue();
        assertEquals(HEALTH_CODE, updatedContext.getHealthCode());
        assertEquals(LANGUAGES, updatedContext.getLanguages());
        assertEquals(DATA_GROUP_SET, updatedContext.getUserDataGroups());
        assertEquals(TestConstants.USER_SUBSTUDY_IDS, updatedContext.getUserSubstudyIds());
        assertEquals(USER_ID, updatedContext.getUserId());
    }
    
    @Test
    public void signInWithEmail() {
        account.setId(USER_ID);
        account.setReauthToken(REAUTH_TOKEN);
        doReturn(account).when(accountDao).authenticate(study, EMAIL_PASSWORD_SIGN_IN);
        doReturn(PARTICIPANT).when(participantService).getParticipant(study, account, false);
        doReturn(CONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), any());

        UserSession retrieved = service.signIn(study, CONTEXT, EMAIL_PASSWORD_SIGN_IN);
        
        assertEquals(REAUTH_TOKEN, retrieved.getReauthToken());
        verify(cacheProvider).removeSessionByUserId(USER_ID);
        verify(cacheProvider).setUserSession(retrieved);
    }
    
    @Test(expected = ConsentRequiredException.class)
    public void unconsentedSignInWithEmail() {
        doReturn(account).when(accountDao).authenticate(study, EMAIL_PASSWORD_SIGN_IN);
        doReturn(PARTICIPANT).when(participantService).getParticipant(study, account, false);
        doReturn(UNCONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), any());
        
        service.signIn(study, CONTEXT, EMAIL_PASSWORD_SIGN_IN);
    }
    
    @Test
    public void adminSignInWithEmail() {
        account.setReauthToken(REAUTH_TOKEN);
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withRoles(Sets.newHashSet(Roles.DEVELOPER)).build();
        doReturn(account).when(accountDao).authenticate(study, EMAIL_PASSWORD_SIGN_IN);
        doReturn(participant).when(participantService).getParticipant(study, account, false);
        doReturn(UNCONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), eq(account));
        
        // Does not throw consent required exception, despite being unconsented, because user has DEVELOPER role.
        UserSession retrieved = service.signIn(study, CONTEXT, EMAIL_PASSWORD_SIGN_IN);
        
        assertEquals(REAUTH_TOKEN, retrieved.getReauthToken());
        assertEquals(UNCONSENTED_STATUS_MAP, retrieved.getConsentStatuses());
    }
    
    @Test
    public void signInWithPhone() {
        account.setId(USER_ID);
        account.setReauthToken(REAUTH_TOKEN);
        doReturn(account).when(accountDao).authenticate(study, PHONE_PASSWORD_SIGN_IN);
        doReturn(PARTICIPANT).when(participantService).getParticipant(study, account, false);
        doReturn(CONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), any());

        UserSession retrieved = service.signIn(study, CONTEXT, PHONE_PASSWORD_SIGN_IN);
        
        assertEquals(REAUTH_TOKEN, retrieved.getReauthToken());
        verify(cacheProvider).removeSessionByUserId(USER_ID);
        verify(cacheProvider).setUserSession(retrieved);
    }
    
    @Test(expected = ConsentRequiredException.class)
    public void unconsentedSignInWithPhone() {
        doReturn(account).when(accountDao).authenticate(study, PHONE_PASSWORD_SIGN_IN);
        doReturn(PARTICIPANT).when(participantService).getParticipant(study, account, false);
        doReturn(UNCONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), any());
        
        service.signIn(study, CONTEXT, PHONE_PASSWORD_SIGN_IN);
    }
    
    @Test
    public void adminSignInWithPhone() {
        account.setReauthToken(REAUTH_TOKEN);
        StudyParticipant participant = new StudyParticipant.Builder()
                .copyOf(PARTICIPANT).withRoles(Sets.newHashSet(Roles.RESEARCHER)).build();
        doReturn(account).when(accountDao).authenticate(study, PHONE_PASSWORD_SIGN_IN);
        doReturn(participant).when(participantService).getParticipant(study, account, false);
        doReturn(UNCONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), any());
        
        // Does not throw consent required exception, despite being unconsented, because user has RESEARCHER role. 
        UserSession retrieved = service.signIn(study, CONTEXT, PHONE_PASSWORD_SIGN_IN);

        assertEquals(REAUTH_TOKEN, retrieved.getReauthToken());
        assertEquals(UNCONSENTED_STATUS_MAP, retrieved.getConsentStatuses());
    }
    
    @Test
    public void signOut() {
        StudyIdentifier studyIdentifier = new StudyIdentifierImpl(STUDY_ID);
        
        UserSession session = new UserSession();
        session.setStudyIdentifier(studyIdentifier);
        session.setReauthToken(TOKEN);
        session.setParticipant(new StudyParticipant.Builder().withEmail("email@email.com").withId(USER_ID).build());
        service.signOut(session);
        
        verify(accountDao).deleteReauthToken(ACCOUNT_ID);
        verify(cacheProvider).removeSession(session);
    }
    
    @Test
    public void signOutNoSessionToken() {
        service.signOut(null);
        
        verify(accountDao, never()).deleteReauthToken(any());
        verify(cacheProvider, never()).removeSession(any());
    }
    
    @Test
    public void emailSignIn() {
        account.setId(USER_ID);
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
        verify(cacheProvider).removeSessionByUserId(USER_ID);
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
        doReturn(UNCONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), any());
        
        try {
            service.emailSignIn(CONTEXT, SIGN_IN_WITH_EMAIL);
            fail("Should have thrown exception");
        } catch(ConsentRequiredException e) {
            verify(cacheProvider).setUserSession(e.getUserSession());
            assertEquals(UNCONSENTED_STATUS_MAP, e.getUserSession().getConsentStatuses());
        }
    }
    
    @Test
    public void emailSignInAdminOK() {
        StudyParticipant participant = new StudyParticipant.Builder().withRoles(Sets.newHashSet(Roles.ADMIN)).build();
        
        doReturn(participant).when(participantService).getParticipant(study, account, false);
        doReturn(SIGN_IN_WITH_EMAIL.getAccountId()).when(accountWorkflowService).channelSignIn(ChannelType.EMAIL,
                CONTEXT, SIGN_IN_WITH_EMAIL, SignInValidator.EMAIL_SIGNIN);
        doReturn(account).when(accountDao).getAccountAfterAuthentication(SIGN_IN_WITH_EMAIL.getAccountId());
        doReturn(UNCONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), any());
        
        // Does not throw a consent required exception because the participant is an admin. 
        UserSession retrieved = service.emailSignIn(CONTEXT, SIGN_IN_WITH_EMAIL);
        assertEquals(UNCONSENTED_STATUS_MAP, retrieved.getConsentStatuses());
    }
    
    @Test
    public void reauthentication() {
        account.setId(USER_ID);
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
                eq(BridgeConstants.REAUTH_TOKEN_GRACE_PERIOD_SECONDS));
        
        Tuple<String> tuple = tupleCaptor.getValue();
        assertEquals(session.getSessionToken(), tuple.getLeft());
        assertEquals(REAUTH_TOKEN, tuple.getRight());
    }
    
    @Test
    public void reauthenticationFromCache() {
        Tuple<String> tuple = new Tuple<>(TOKEN, "newReauthToken");
        
        StudyParticipant participant = new StudyParticipant.Builder().withId(USER_ID).build();
        UserSession oldSession = new UserSession();
        oldSession.setStudyIdentifier(study.getStudyIdentifier());
        oldSession.setParticipant(participant);
        oldSession.setConsentStatuses(UNCONSENTED_STATUS_MAP);
        
        doReturn(tuple).when(cacheProvider).getObject(REAUTH_CACHE_TOKEN, AuthenticationService.TUPLE_TYPE);
        doReturn(oldSession).when(cacheProvider).getUserSession(TOKEN);
        doReturn(participant).when(participantService).getParticipant(study, account, false);
        // Re-checking the consent status, we see the user is consented, despite the 
        // cached session.
        doReturn(CONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), any());
        
        AccountId accountId = AccountId.forId(study.getIdentifier(), USER_ID);
        doReturn(account).when(accountDao).getAccountAfterAuthentication(accountId);
        account.setReauthToken("newReauthToken");
        
        UserSession returned = service.reauthenticate(study, CONTEXT, REAUTH_REQUEST);
        assertEquals("newReauthToken", returned.getReauthToken());
        
        // We don't have to retrieve this.
        verify(accountDao, never()).reauthenticate(any(), any());
    }
    
    @Test(expected = ConsentRequiredException.class)
    public void reauthenticationFromCacheFailsOnConsent() {
        Tuple<String> tuple = new Tuple<>(TOKEN, "newReauthToken");
        
        StudyParticipant participant = new StudyParticipant.Builder().withId(USER_ID).build();
        UserSession oldSession = new UserSession();
        oldSession.setStudyIdentifier(study.getStudyIdentifier());
        oldSession.setParticipant(participant);
        oldSession.setConsentStatuses(CONSENTED_STATUS_MAP);
        
        doReturn(tuple).when(cacheProvider).getObject(REAUTH_CACHE_TOKEN, AuthenticationService.TUPLE_TYPE);
        doReturn(oldSession).when(cacheProvider).getUserSession(TOKEN);
        doReturn(participant).when(participantService).getParticipant(study, account, false);
        // Re-checking the consent status, we see the user is no longer consented, 
        // despite the cached session.
        doReturn(UNCONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), any());
        
        AccountId accountId = AccountId.forId(study.getIdentifier(), USER_ID);
        doReturn(account).when(accountDao).getAccountAfterAuthentication(accountId);
        
        service.reauthenticate(study, CONTEXT, REAUTH_REQUEST);
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
    
    @Test
    public void reauthThrowsUnconsentedException() {
        StudyParticipant participant = new StudyParticipant.Builder().withStatus(AccountStatus.ENABLED).build();
        
        doReturn(account).when(accountDao).reauthenticate(study, REAUTH_REQUEST);
        doReturn(participant).when(participantService).getParticipant(study, account, false);
        doReturn(UNCONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), any());
        
        try {
            service.reauthenticate(study, CONTEXT, REAUTH_REQUEST);
            fail("Should have thrown exception");
        } catch(ConsentRequiredException e) {
            assertEquals(UNCONSENTED_STATUS_MAP, e.getUserSession().getConsentStatuses());
        }
    }
    
    @Test(expected = InvalidEntityException.class)
    public void requestResetInvalid() {
        SignIn signIn = new SignIn.Builder().withStudy(STUDY_ID).withPhone(TestConstants.PHONE)
                .withEmail(RECIPIENT_EMAIL).build();
        service.requestResetPassword(study, false, signIn);
    }
    
    @Test
    public void requestResetPassword() {
        SignIn signIn = new SignIn.Builder().withStudy(STUDY_ID).withEmail(RECIPIENT_EMAIL).build();
        
        service.requestResetPassword(study, false, signIn);
        
        verify(accountWorkflowService).requestResetPassword(study, false, signIn.getAccountId());
    }
    
    @Test
    public void signUpWithEmailOK() {
        study.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);
        StudyParticipant participant = new StudyParticipant.Builder().withEmail(RECIPIENT_EMAIL).withPassword(PASSWORD)
                .build();
        
        service.signUp(study, participant);
        
        verify(participantService).createParticipant(eq(study), participantCaptor.capture(), eq(true));
        StudyParticipant captured = participantCaptor.getValue();
        assertEquals(RECIPIENT_EMAIL, captured.getEmail());
        assertEquals(PASSWORD, captured.getPassword());
    }

    @Test
    public void signUpWithPhoneOK() {
        study.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);
        StudyParticipant participant = new StudyParticipant.Builder().withPhone(TestConstants.PHONE)
                .withPassword(PASSWORD).build();
        
        service.signUp(study, participant);
        
        verify(participantService).createParticipant(eq(study), participantCaptor.capture(), eq(true));
        StudyParticipant captured = participantCaptor.getValue();
        assertEquals(TestConstants.PHONE.getNumber(), captured.getPhone().getNumber());
        assertEquals(PASSWORD, captured.getPassword());
    }
    
    @Test
    public void signUpExistingAccount() {
        study.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);
        StudyParticipant participant = new StudyParticipant.Builder().withEmail(RECIPIENT_EMAIL).withPassword(PASSWORD)
                .build();
        doThrow(new EntityAlreadyExistsException(Account.class, "userId", "user-id")).when(participantService)
                .createParticipant(study, participant, true);
        
        service.signUp(study, participant);
        
        verify(participantService).createParticipant(eq(study), any(), eq(true));
        verify(accountWorkflowService).notifyAccountExists(eq(study), accountIdCaptor.capture());
        
        AccountId captured = accountIdCaptor.getValue();
        assertEquals("user-id", captured.getId());
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, captured.getStudyId());
    }
    
    @Test
    public void signUpExistingExternalId() {
        study.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);
        study.setExternalIdValidationEnabled(true);
        StudyParticipant participant = new StudyParticipant.Builder().withExternalId(EXTERNAL_ID).build();
        
        doThrow(new EntityAlreadyExistsException(ExternalIdentifier.class, "identifier", EXTERNAL_ID)).when(participantService)
                .createParticipant(study, participant, true);
        
        service.signUp(study, participant);
        
        verify(participantService).createParticipant(eq(study), any(), eq(true));
        verify(accountWorkflowService).notifyAccountExists(eq(study), accountIdCaptor.capture());
        
        AccountId captured = accountIdCaptor.getValue();
        assertEquals(EXTERNAL_ID, captured.getExternalId());
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, captured.getStudyId());
    }
    
    @Test
    public void phoneSignIn() {
        account.setId(USER_ID);

        // Put some stuff in participant to verify session is initialized
        StudyParticipant participant = new StudyParticipant.Builder().withDataGroups(DATA_GROUP_SET)
                .withEmail(RECIPIENT_EMAIL).withHealthCode(HEALTH_CODE).withId(USER_ID).withLanguages(LANGUAGES)
                .withFirstName("Test").withLastName("Tester").withPhone(TestConstants.PHONE).build();
        doReturn(participant).when(participantService).getParticipant(study, account, false);
        doReturn(SIGN_IN_WITH_PHONE.getAccountId()).when(accountWorkflowService).channelSignIn(ChannelType.PHONE,
                CONTEXT, SIGN_IN_WITH_PHONE, SignInValidator.PHONE_SIGNIN);
        doReturn(account).when(accountDao).getAccountAfterAuthentication(SIGN_IN_WITH_PHONE.getAccountId());
        doReturn(CONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), any());

        // Execute and validate.
        UserSession session = service.phoneSignIn(CONTEXT, SIGN_IN_WITH_PHONE);

        assertEquals(RECIPIENT_EMAIL, session.getParticipant().getEmail());
        assertEquals("Test", session.getParticipant().getFirstName());
        assertEquals("Tester", session.getParticipant().getLastName());
        
        // this doesn't pass if our mock calls above aren't executed, but verify these:
        verify(cacheProvider).removeSessionByUserId(USER_ID);
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
        doReturn(UNCONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), any());
        doReturn(account).when(accountDao).getAccountAfterAuthentication(SIGN_IN_WITH_PHONE.getAccountId());
         
        try {
            service.phoneSignIn(CONTEXT, SIGN_IN_WITH_PHONE);
            fail("Should have thrown exception");
        } catch(ConsentRequiredException e) {
            verify(cacheProvider).setUserSession(e.getUserSession());
            assertEquals(UNCONSENTED_STATUS_MAP, e.getUserSession().getConsentStatuses());            
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

        CriteriaContext context = new CriteriaContext.Builder().withLanguages(LANGUAGES).withUserId(USER_ID)
                .withStudyIdentifier(TestConstants.TEST_STUDY).build();
        TestUtils.mockEditAccount(accountDao, mockAccount);
        doReturn(mockAccount).when(accountDao).getAccount(any());
        
        // No languages.
        StudyParticipant participant = new StudyParticipant.Builder().withHealthCode("healthCode").build();
        doReturn(participant).when(participantService).getParticipant(study, mockAccount, false);
        
        service.getSession(study, context);
        
        verify(accountDao).editAccount(eq(TestConstants.TEST_STUDY), eq("healthCode"), any());
        verify(mockAccount).setLanguages(ImmutableList.copyOf(LANGUAGES));
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
    
    @Test(expected = BadRequestException.class)
    public void generatePasswordExternalIdManagementDisabled() {
        study.setExternalIdValidationEnabled(false);
        service.generatePassword(study, EXTERNAL_ID, true);
    }
    
    @Test(expected = BadRequestException.class)
    public void generatePasswordExternalIdNotSubmitted() {
        study.setExternalIdValidationEnabled(true);
        service.generatePassword(study, null, true);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void generatePasswordExternalIdRecordMissing() {
        when(externalIdService.getExternalId(study.getStudyIdentifier(), EXTERNAL_ID)).thenReturn(Optional.empty());
        study.setExternalIdValidationEnabled(true);
        service.generatePassword(study, EXTERNAL_ID, false);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void generatePasswordNoAccountDoNotCreateAccount() {
        ExternalIdentifier externalIdentifier = ExternalIdentifier.create(study.getStudyIdentifier(), EXTERNAL_ID);
        study.setExternalIdValidationEnabled(true);
        when(externalIdService.getExternalId(study.getStudyIdentifier(), EXTERNAL_ID))
                .thenReturn(Optional.of(externalIdentifier));
        
        service.generatePassword(study, EXTERNAL_ID, false);
    }
    
    @Test
    public void generatePasswordAndAccountOK() {
        ExternalIdentifier externalIdentifier = ExternalIdentifier.create(study.getStudyIdentifier(), EXTERNAL_ID);
        study.setExternalIdValidationEnabled(true);
        doReturn(PASSWORD).when(service).generatePassword(anyInt());
        when(externalIdService.getExternalId(study.getStudyIdentifier(), EXTERNAL_ID))
                .thenReturn(Optional.of(externalIdentifier));
        
        IdentifierHolder idHolder = new IdentifierHolder("userId");
        when(participantService.createParticipant(eq(study), participantCaptor.capture(), eq(false))).thenReturn(idHolder);
        
        GeneratedPassword password = service.generatePassword(study, EXTERNAL_ID, true);
        assertEquals(EXTERNAL_ID, password.getExternalId());
        assertEquals(PASSWORD, password.getPassword());
        
        StudyParticipant participant = participantCaptor.getValue();
        assertEquals(EXTERNAL_ID, participant.getExternalId());
        assertEquals(PASSWORD, participant.getPassword());
    }
    
    @Test
    public void generatePasswordAndAccountWhenExternalIdTaken() {
        ExternalIdentifier externalIdentifier = ExternalIdentifier.create(study.getStudyIdentifier(), EXTERNAL_ID);
        externalIdentifier.setHealthCode("someoneElsesHealthCode");
        study.setExternalIdValidationEnabled(true);
        when(externalIdService.getExternalId(study.getStudyIdentifier(), EXTERNAL_ID))
                .thenReturn(Optional.of(externalIdentifier));
        
        when(participantService.createParticipant(eq(study), participantCaptor.capture(), eq(false)))
                        .thenThrow(new EntityAlreadyExistsException(Account.class, "id", "asdf"));
        
        try {
            service.generatePassword(study, EXTERNAL_ID, true);
            fail("Should have thrown an exception");
        } catch(EntityAlreadyExistsException e) {
            // expected exception
        }
        verify(accountDao).getAccount(AccountId.forExternalId(TestConstants.TEST_STUDY_IDENTIFIER, EXTERNAL_ID));
        verify(participantService).createParticipant(eq(study), any(), eq(false));
        verifyNoMoreInteractions(accountDao);
        verifyNoMoreInteractions(participantService);
    }
    
    
    @Test
    public void generatePasswordAndAccountWhenExternalIdMissing() {
        ExternalIdentifier externalIdentifier = ExternalIdentifier.create(study.getStudyIdentifier(), EXTERNAL_ID);
        externalIdentifier.setHealthCode("someoneElsesHealthCode");
        study.setExternalIdValidationEnabled(true);
        when(externalIdService.getExternalId(study.getStudyIdentifier(), EXTERNAL_ID))
            .thenReturn(Optional.empty());
        
        try {
            service.generatePassword(study, EXTERNAL_ID, true);
            fail("Should have thrown an exception");
        } catch(EntityNotFoundException e) {
            // expected exception
        }
        verify(accountDao, never()).getAccount(any());
        verify(participantService, never()).createParticipant(any(), any(), anyBoolean());
        verify(accountDao, never()).changePassword(any(), any(), any());
    }
    
    @Test
    public void generatePasswordOK() {
        ExternalIdentifier externalIdentifier = ExternalIdentifier.create(study.getStudyIdentifier(), EXTERNAL_ID);
        study.setExternalIdValidationEnabled(true);
        when(externalIdService.getExternalId(study.getStudyIdentifier(), EXTERNAL_ID))
                .thenReturn(Optional.of(externalIdentifier));
        doReturn(PASSWORD).when(service).generatePassword(anyInt());
        
        when(accountDao.getAccount(any())).thenReturn(account);
        account.setHealthCode(HEALTH_CODE);
        
        GeneratedPassword password = service.generatePassword(study, EXTERNAL_ID, true);
        assertEquals(EXTERNAL_ID, password.getExternalId());
        assertEquals(PASSWORD, password.getPassword());
        
        verify(accountDao).changePassword(account, null, PASSWORD);
    }
    
    @Test
    public void generatedPasswordPassesValidation() {
        // This is a very large password, which you could set in a study like this
        String password = service.generatePassword(100);

        Errors errors = Validate.getErrorsFor(password);
        ValidatorUtils.validatePassword(errors, PasswordPolicy.DEFAULT_PASSWORD_POLICY, password);
        assertFalse(errors.hasErrors());
        assertEquals(100, password.length());
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void generatePasswordExternalIdMismatchesCallerSubstudies() {
        BridgeUtils.setRequestContext(
                new RequestContext.Builder().withCallerSubstudies(ImmutableSet.of("substudyB")).build());
        study.setExternalIdValidationEnabled(true);
        
        ExternalIdentifier externalIdentifier = ExternalIdentifier.create(study.getStudyIdentifier(), EXTERNAL_ID);
        externalIdentifier.setSubstudyId("substudyA");
        when(externalIdService.getExternalId(study.getStudyIdentifier(), EXTERNAL_ID))
                .thenReturn(Optional.of(externalIdentifier));
        
        account.setAccountSubstudies(ImmutableSet.of(AccountSubstudy.create(study.getIdentifier(), "substudyA", "id")));
        
        service.generatePassword(study, EXTERNAL_ID, false);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void generatePasswordAccountMismatchesCallerSubstudies() {
        BridgeUtils.setRequestContext(
                new RequestContext.Builder().withCallerSubstudies(ImmutableSet.of("substudyA")).build());
        study.setExternalIdValidationEnabled(true);
        
        ExternalIdentifier externalIdentifier = ExternalIdentifier.create(study.getStudyIdentifier(), EXTERNAL_ID);
        externalIdentifier.setSubstudyId("substudyA");
        when(externalIdService.getExternalId(study.getStudyIdentifier(), EXTERNAL_ID))
                .thenReturn(Optional.of(externalIdentifier));
        
        when(accountDao.getAccount(any())).thenReturn(account);
        account.setAccountSubstudies(ImmutableSet.of(AccountSubstudy.create(study.getIdentifier(), "substudyB", "id")));
        
        service.generatePassword(study, EXTERNAL_ID, false);
    }

    @Test(expected = UnauthorizedException.class)
    public void creatingExternalIdOnlyAccountFailsIfIdsNotManaged() {
        study.setExternalIdValidationEnabled(false);
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withEmail(null).withPhone(null).withExternalId("id").build();
        service.signUp(study, participant);
    }
    
    @Test
    public void creatingExternalIdOnlyAccountSucceedsIfIdsManaged() {
        study.setExternalIdValidationEnabled(true);
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withEmail(null).withPhone(null).withExternalId("id").build();
        service.signUp(study, participant);
        
        verify(participantService).createParticipant(study, participant, true);
    }
    
    @Test
    public void signInWithIntentToParticipate() {
        account.setId(USER_ID);
        Account consentedAccount = Account.create();

        doReturn(account).when(accountDao).authenticate(study, EMAIL_PASSWORD_SIGN_IN);
        doReturn(PARTICIPANT_WITH_ATTRIBUTES).when(participantService).getParticipant(study, account,
                false);
        doReturn(UNCONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), eq(account));
        
        doReturn(consentedAccount).when(accountDao).getAccount(any());
        doReturn(PARTICIPANT_WITH_ATTRIBUTES).when(participantService).getParticipant(study, consentedAccount,
                false);
        doReturn(CONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), eq(consentedAccount));
        
        // This would normally throw except that the intentService reports consents were updated
        when(intentService.registerIntentToParticipate(study, account)).thenReturn(true);
        
        service.signIn(study, CONTEXT, EMAIL_PASSWORD_SIGN_IN);
    }
    
    @Test
    public void emailSignInWithIntentToParticipate() {
        Account consentedAccount = Account.create();

        when(accountWorkflowService.channelSignIn(ChannelType.EMAIL, CONTEXT, SIGN_IN_WITH_EMAIL,
                SignInValidator.EMAIL_SIGNIN)).thenReturn(SIGN_IN_WITH_EMAIL.getAccountId());
        when(accountDao.getAccountAfterAuthentication(any())).thenReturn(account);
        when(participantService.getParticipant(study, account, false)).thenReturn(
                PARTICIPANT_WITH_ATTRIBUTES);
        when(consentService.getConsentStatuses(any(), eq(account))).thenReturn(UNCONSENTED_STATUS_MAP);
        
        when(accountDao.getAccount(any())).thenReturn(consentedAccount);
        when(participantService.getParticipant(study, consentedAccount, false)).thenReturn(
                PARTICIPANT_WITH_ATTRIBUTES);
        when(consentService.getConsentStatuses(any(), eq(consentedAccount))).thenReturn(CONSENTED_STATUS_MAP);

        // This would normally throw except that the intentService reports consents were updated
        when(intentService.registerIntentToParticipate(study, account)).thenReturn(true);
        
        service.emailSignIn(CONTEXT, SIGN_IN_WITH_EMAIL);
    }

    @Test
    public void phoneSignInWithIntentToParticipate() {
        Account consentedAccount = Account.create();

        when(accountWorkflowService.channelSignIn(ChannelType.PHONE, CONTEXT, SIGN_IN_WITH_PHONE,
                SignInValidator.PHONE_SIGNIN)).thenReturn(SIGN_IN_WITH_PHONE.getAccountId());
        when(accountDao.getAccountAfterAuthentication(any())).thenReturn(account);
        when(participantService.getParticipant(study, account, false)).thenReturn(
                PARTICIPANT_WITH_ATTRIBUTES);
        when(consentService.getConsentStatuses(any(), eq(account))).thenReturn(UNCONSENTED_STATUS_MAP);
        
        when(accountDao.getAccount(any())).thenReturn(consentedAccount);
        when(participantService.getParticipant(study, consentedAccount, false)).thenReturn(
                PARTICIPANT_WITH_ATTRIBUTES);
        when(consentService.getConsentStatuses(any(), eq(consentedAccount))).thenReturn(CONSENTED_STATUS_MAP);

        // This would normally throw except that the intentService reports consents were updated
        when(intentService.registerIntentToParticipate(study, account)).thenReturn(true);
        
        service.phoneSignIn(CONTEXT, SIGN_IN_WITH_PHONE);
    }
    
    @Test
    public void consentedSignInDoesNotExecuteIntentToParticipate() {
        doReturn(account).when(accountDao).authenticate(study, EMAIL_PASSWORD_SIGN_IN);
        doReturn(PARTICIPANT).when(participantService).getParticipant(study, account, false);
        doReturn(CONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any(), eq(account));
        
        service.signIn(study, CONTEXT, EMAIL_PASSWORD_SIGN_IN);
        
        verify(intentService, never()).registerIntentToParticipate(study, account);
    }
    
    @Test
    public void consentedEmailSignInDoesNotExecuteIntentToParticipate() {
        when(accountWorkflowService.channelSignIn(ChannelType.EMAIL, CONTEXT, SIGN_IN_WITH_EMAIL,
                SignInValidator.EMAIL_SIGNIN)).thenReturn(SIGN_IN_WITH_EMAIL.getAccountId());
        when(accountDao.getAccountAfterAuthentication(any())).thenReturn(account);
        when(participantService.getParticipant(study, account, false)).thenReturn(PARTICIPANT);
        when(consentService.getConsentStatuses(any(), eq(account))).thenReturn(CONSENTED_STATUS_MAP);
        
        service.emailSignIn(CONTEXT, SIGN_IN_WITH_EMAIL);
        
        verify(intentService, never()).registerIntentToParticipate(study, account);
    }

    @Test
    public void consentedPhoneSignInDoesNotExecuteIntentToParticipate() {
        when(accountWorkflowService.channelSignIn(ChannelType.PHONE, CONTEXT, SIGN_IN_WITH_PHONE,
                SignInValidator.PHONE_SIGNIN)).thenReturn(SIGN_IN_WITH_PHONE.getAccountId());
        when(accountDao.getAccountAfterAuthentication(any())).thenReturn(account);
        when(participantService.getParticipant(study, account, false)).thenReturn(PARTICIPANT);
        when(consentService.getConsentStatuses(any(), eq(account))).thenReturn(CONSENTED_STATUS_MAP);
        
        service.phoneSignIn(CONTEXT, SIGN_IN_WITH_PHONE);
        
        verify(intentService, never()).registerIntentToParticipate(study, account);
    }

    // Most of the other behaviors are tested in other methods. This test specifically tests the session created has
    // the correct attributes.
    @Test
    public void getSessionFromAccount() {
        // Create inputs.
        Study study = Study.create();
        study.setIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);

        CriteriaContext context = new CriteriaContext.Builder().withIpAddress(IP_ADDRESS)
                .withStudyIdentifier(TestConstants.TEST_STUDY).build();

        Account account = Account.create();
        account.setId(USER_ID);
        account.setReauthToken(REAUTH_TOKEN);

        // Mock pre-reqs.
        when(participantService.getParticipant(any(), any(Account.class), anyBoolean())).thenReturn(PARTICIPANT);
        when(config.getEnvironment()).thenReturn(Environment.LOCAL);
        when(consentService.getConsentStatuses(any(), any())).thenReturn(CONSENTED_STATUS_MAP);

        // Execute and validate.
        UserSession session = service.getSessionFromAccount(study, context, account);
        assertSame(PARTICIPANT, session.getParticipant());
        assertNotNull(session.getSessionToken());
        assertNotNull(session.getInternalSessionToken());
        assertTrue(session.isAuthenticated());
        assertEquals(Environment.LOCAL, session.getEnvironment());
        assertEquals(IP_ADDRESS, session.getIpAddress());
        assertEquals(TestConstants.TEST_STUDY, session.getStudyIdentifier());
        assertEquals(REAUTH_TOKEN, session.getReauthToken());
        assertEquals(CONSENTED_STATUS_MAP, session.getConsentStatuses());
    }
}