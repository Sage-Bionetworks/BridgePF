package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.BridgeConstants.NO_CALLER_ROLES;

import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.exceptions.AccountDisabledException;
import org.sagebionetworks.bridge.exceptions.AuthenticationFailedException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.AccountStatus;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.EmailVerification;
import org.sagebionetworks.bridge.models.accounts.GenericAccount;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.MimeType;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.AuthenticationService.ChannelType;
import org.sagebionetworks.bridge.services.email.BasicEmailProvider;
import org.sagebionetworks.bridge.services.email.MimeTypeEmailProvider;
import org.sagebionetworks.bridge.validators.PasswordResetValidator;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

@RunWith(MockitoJUnitRunner.class)
public class AuthenticationServiceMockTest {
    
    private static final String SUPPORT_EMAIL = "support@support.com";
    private static final String STUDY_ID = TestConstants.TEST_STUDY_IDENTIFIER;
    private static final String CACHE_KEY = "email@email.com:"+STUDY_ID+":signInRequest";
    private static final String RECIPIENT_EMAIL = "email@email.com";
    private static final String TOKEN = "ABC-DEF";
    private static final String REAUTH_TOKEN = "GHI-JKL";
    private static final String USER_ID = "user-id";
    private static final String PASSWORD = "Password~!1";
    private static final SignIn SIGN_IN_REQUEST_WITH_EMAIL = new SignIn.Builder().withStudy(STUDY_ID)
            .withEmail(RECIPIENT_EMAIL).build();
    private static final SignIn SIGN_IN_WITH_EMAIL = new SignIn.Builder().withStudy(STUDY_ID).withEmail(RECIPIENT_EMAIL)
            .withToken(TOKEN).build();
    private static final SignIn SIGN_IN_REQUEST_WITH_PHONE = new SignIn.Builder().withStudy(STUDY_ID)
            .withPhone(TestConstants.PHONE).build();
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
    private static final AccountId ACCOUNT_ID_WITH_PHONE = AccountId.forPhone(STUDY_ID, TestConstants.PHONE);

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
    private ParticipantService participantService;
    @Mock
    private SendMailService sendMailService;
    @Mock
    private StudyService studyService;
    @Mock
    private PasswordResetValidator passwordResetValidator;
    @Mock
    private AccountWorkflowService accountWorkflowService;
    @Mock
    private NotificationsService notificationsService; 
    @Captor
    private ArgumentCaptor<String> stringCaptor;
    @Captor
    private ArgumentCaptor<BasicEmailProvider> providerCaptor;
    @Captor
    private ArgumentCaptor<UserSession> sessionCaptor;
    @Captor
    private ArgumentCaptor<StudyParticipant> participantCaptor;
    @Spy
    private AuthenticationService service;

    private Study study;

    private Account account;

    @Before
    public void before() {
        study = Study.create();
        study.setIdentifier(STUDY_ID);
        study.setEmailSignInEnabled(true);
        study.setEmailSignInTemplate(new EmailTemplate("subject","${token}",MimeType.TEXT));
        study.setSupportEmail(SUPPORT_EMAIL);
        study.setName("Sender");
        
        account = new GenericAccount();
        
        service.setCacheProvider(cacheProvider);
        service.setBridgeConfig(config);
        service.setConsentService(consentService);
        service.setOptionsService(optionsService);
        service.setAccountDao(accountDao);
        service.setPasswordResetValidator(passwordResetValidator);
        service.setParticipantService(participantService);
        service.setSendMailService(sendMailService);
        service.setStudyService(studyService);
        service.setAccountWorkflowService(accountWorkflowService);
        service.setNotificationsService(notificationsService);

        doReturn(study).when(studyService).getStudy(STUDY_ID);
    }
    
    @Test
    public void signInWithEmail() throws Exception {
        account.setReauthToken(REAUTH_TOKEN);
        doReturn(account).when(accountDao).authenticate(study, EMAIL_PASSWORD_SIGN_IN);
        doReturn(PARTICIPANT).when(participantService).getParticipant(study, account, false);
        doReturn(CONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any());
        
        UserSession retrieved = service.signIn(study, CONTEXT, EMAIL_PASSWORD_SIGN_IN);
        assertEquals(REAUTH_TOKEN, retrieved.getReauthToken());
        verify(cacheProvider).setUserSession(retrieved);
    }
    
    @Test(expected = ConsentRequiredException.class)
    public void unconsentedSignInWithEmail() throws Exception {
        doReturn(account).when(accountDao).authenticate(study, EMAIL_PASSWORD_SIGN_IN);
        doReturn(PARTICIPANT).when(participantService).getParticipant(study, account, false);
        doReturn(UNCONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any());
        
        service.signIn(study, CONTEXT, EMAIL_PASSWORD_SIGN_IN);
    }
    
    @Test
    public void adminSignInWithEmail() throws Exception {
        account.setReauthToken(REAUTH_TOKEN);
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(PARTICIPANT)
                .withRoles(Sets.newHashSet(Roles.DEVELOPER)).build();
        doReturn(account).when(accountDao).authenticate(study, EMAIL_PASSWORD_SIGN_IN);
        doReturn(participant).when(participantService).getParticipant(study, account, false);
        // Does not throw consent required exception, despite being unconsented, because user has DEVELOPER role. 
        doReturn(UNCONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any());
        
        UserSession retrieved = service.signIn(study, CONTEXT, EMAIL_PASSWORD_SIGN_IN);
        assertEquals(REAUTH_TOKEN, retrieved.getReauthToken());
    }
    
    @Test
    public void signInWithPhone() throws Exception {
        account.setReauthToken(REAUTH_TOKEN);
        doReturn(account).when(accountDao).authenticate(study, PHONE_PASSWORD_SIGN_IN);
        doReturn(PARTICIPANT).when(participantService).getParticipant(study, account, false);
        doReturn(CONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any());
        
        UserSession retrieved = service.signIn(study, CONTEXT, PHONE_PASSWORD_SIGN_IN);
        assertEquals(REAUTH_TOKEN, retrieved.getReauthToken());
        verify(cacheProvider).setUserSession(retrieved);
    }
    
    @Test(expected = ConsentRequiredException.class)
    public void unconsentedSignInWithPhone() throws Exception {
        doReturn(account).when(accountDao).authenticate(study, PHONE_PASSWORD_SIGN_IN);
        doReturn(PARTICIPANT).when(participantService).getParticipant(study, account, false);
        doReturn(UNCONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any());
        
        service.signIn(study, CONTEXT, PHONE_PASSWORD_SIGN_IN);
    }
    
    @Test
    public void adminSignInWithPhone() throws Exception {
        account.setReauthToken(REAUTH_TOKEN);
        StudyParticipant participant = new StudyParticipant.Builder()
                .copyOf(PARTICIPANT).withRoles(Sets.newHashSet(Roles.RESEARCHER)).build();
        doReturn(account).when(accountDao).authenticate(study, PHONE_PASSWORD_SIGN_IN);
        doReturn(participant).when(participantService).getParticipant(study, account, false);
        // Does not throw consent required exception, despite being unconsented, because user has RESEARCHER role. 
        doReturn(UNCONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any());
        
        UserSession retrieved = service.signIn(study, CONTEXT, PHONE_PASSWORD_SIGN_IN);
        assertEquals(REAUTH_TOKEN, retrieved.getReauthToken());
    }
    
    @Test
    public void requestEmailSignIn() throws Exception {
        doReturn(account).when(accountDao).getAccount(SIGN_IN_REQUEST_WITH_EMAIL.getAccountId());
        
        service.requestEmailSignIn(SIGN_IN_REQUEST_WITH_EMAIL);
        
        verify(cacheProvider).getObject(stringCaptor.capture(), eq(String.class));
        assertEquals(CACHE_KEY, stringCaptor.getValue());
        
        verify(accountDao).getAccount(SIGN_IN_REQUEST_WITH_EMAIL.getAccountId());
        
        verify(cacheProvider).setObject(eq(CACHE_KEY), stringCaptor.capture(), eq(300));
        assertNotNull(stringCaptor.getValue());

        verify(sendMailService).sendEmail(providerCaptor.capture());
        
        BasicEmailProvider provider = providerCaptor.getValue();
        assertEquals(21, provider.getTokenMap().get("token").length());
        assertEquals(study, provider.getStudy());
        assertEquals(RECIPIENT_EMAIL, Iterables.getFirst(provider.getRecipientEmails(), null));
    }
    
    @Test(expected = UnauthorizedException.class)
    public void requestEmailSignInDisabled() {
        study.setEmailSignInEnabled(false);
        
        service.requestEmailSignIn(SIGN_IN_REQUEST_WITH_EMAIL);
    }
    
    @Test
    public void requestEmailSignInTwiceReturnsSameToken() throws Exception {
        // In this case, where there is a value and an account, we do't generate a new one,
        // we just send the message again.
        doReturn("something").when(cacheProvider).getObject(CACHE_KEY, String.class);
        doReturn(account).when(accountDao).getAccount(any());
        
        service.requestEmailSignIn(SIGN_IN_REQUEST_WITH_EMAIL);
        
        verify(cacheProvider, never()).setObject(any(), any(), anyInt());
        verify(sendMailService).sendEmail(providerCaptor.capture());
        
        MimeTypeEmailProvider provider = providerCaptor.getValue();
        assertEquals(RECIPIENT_EMAIL, provider.getMimeTypeEmail().getRecipientAddresses().get(0));
        assertEquals(SUPPORT_EMAIL, provider.getPlainSenderEmail());
        String bodyString = (String)provider.getMimeTypeEmail().getMessageParts().get(0).getContent();
        assertEquals("something", bodyString);
    }
    
    @Test
    public void signOut() {
        StudyIdentifier studyIdentifier = new StudyIdentifierImpl(STUDY_ID);
        
        UserSession session = new UserSession();
        session.setStudyIdentifier(studyIdentifier);
        session.setParticipant(new StudyParticipant.Builder().withEmail("email@email.com").withId(USER_ID).build());
        service.signOut(session);
        
        verify(accountDao).signOut(ACCOUNT_ID);
        verify(cacheProvider).removeSession(session);
    }
    
    @Test
    public void signOutNoSessionToken() {
        service.signOut(null);
        
        verify(accountDao, never()).signOut(any());
        verify(cacheProvider, never()).removeSession(any());
    }
    
    @Test
    public void requestEmailSignInEmailNotRegistered() {
        doReturn(null).when(accountDao).getAccount(ACCOUNT_ID);
        
        service.requestEmailSignIn(SIGN_IN_REQUEST_WITH_EMAIL);

        verify(cacheProvider, never()).setObject(eq(CACHE_KEY), any(), eq(60));
        verify(sendMailService, never()).sendEmail(any());
    }

    @Test
    public void emailSignIn() {
        account.setReauthToken(REAUTH_TOKEN);
        account.setStatus(AccountStatus.UNVERIFIED);
        doReturn(TOKEN).when(cacheProvider).getObject(CACHE_KEY, String.class);
        doReturn(account).when(accountDao).getAccountAfterAuthentication(SIGN_IN_WITH_EMAIL.getAccountId());
        doReturn(PARTICIPANT).when(participantService).getParticipant(study, account, false);
        doReturn(CONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any());
        
        UserSession retSession = service.emailSignIn(CONTEXT, SIGN_IN_WITH_EMAIL);
        
        assertNotNull(retSession);
        assertEquals(REAUTH_TOKEN, retSession.getReauthToken());
        verify(accountDao, never()).changePassword(eq(account), any());
        verify(accountDao).getAccountAfterAuthentication(SIGN_IN_WITH_EMAIL.getAccountId());
        verify(accountDao).verifyChannel(AuthenticationService.ChannelType.EMAIL, account);
        verify(cacheProvider).removeObject(CACHE_KEY);
        verify(cacheProvider).setUserSession(retSession);
    }
    
    @Test(expected = AuthenticationFailedException.class)
    public void emailSignInTokenWrong() {
        doReturn(TOKEN).when(cacheProvider).getObject(CACHE_KEY, String.class);
        doReturn(account).when(accountDao).getAccount(ACCOUNT_ID);
        doReturn(PARTICIPANT).when(participantService).getParticipant(study, account, false);
        
        SignIn signIn = new SignIn.Builder().withStudy(study.getIdentifier()).withEmail(RECIPIENT_EMAIL)
                .withToken("wrongToken").build();
        
        service.emailSignIn(CONTEXT, signIn);
    }
    
    @Test(expected = AuthenticationFailedException.class)
    public void emailSignInTokenNotSet() {
        doReturn(null).when(cacheProvider).getObject(CACHE_KEY, String.class);
        doReturn(account).when(accountDao).getAccount(ACCOUNT_ID);
        doReturn(PARTICIPANT).when(participantService).getParticipant(study, account, false);
        
        SignIn signIn = new SignIn.Builder().withStudy(study.getIdentifier()).withEmail(RECIPIENT_EMAIL)
                .withToken("wrongToken").build();
        
        service.emailSignIn(CONTEXT, signIn);
    }
    
    @Test
    public void requestEmailSignInFailureDelays() throws Exception {
        service.getEmailSignInRequestInMillis().set(1000);
        doReturn(null).when(accountDao).getAccount(any());
                 
        long start = System.currentTimeMillis();
        service.requestEmailSignIn(SIGN_IN_REQUEST_WITH_EMAIL);
        long total = System.currentTimeMillis()-start;
        assertTrue(total >= 1000);
        service.getEmailSignInRequestInMillis().set(0);
    }    
    
    @Test(expected = InvalidEntityException.class)
    public void emailSignInRequestMissingStudy() {
        SignIn signInRequest = new SignIn.Builder().withEmail(RECIPIENT_EMAIL).withToken(TOKEN).build();

        service.requestEmailSignIn(signInRequest);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void emailSignInRequestMissingEmail() {
        SignIn signInRequest = new SignIn.Builder().withStudy(STUDY_ID).withToken(TOKEN).build();
        
        service.requestEmailSignIn(signInRequest);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void emailSignInMissingStudy() {
        SignIn signInRequest = new SignIn.Builder().withEmail(RECIPIENT_EMAIL).withToken(TOKEN).build();

        service.emailSignIn(CONTEXT, signInRequest);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void emailSignInMissingEmail() {
        SignIn signInRequest = new SignIn.Builder().withStudy(STUDY_ID).withToken(TOKEN).build();

        service.emailSignIn(CONTEXT, signInRequest);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void emailSignInMissingToken() {
        service.emailSignIn(CONTEXT, SIGN_IN_REQUEST_WITH_EMAIL); // not SIGN_IN which has the token
    }
    
    @Test(expected = AccountDisabledException.class)
    public void emailSignInThrowsAccountDisabled() {
        StudyParticipant participant = new StudyParticipant.Builder().withId(USER_ID).withStatus(AccountStatus.DISABLED)
                .build();
        
        doReturn(participant).when(participantService).getParticipant(study, account, false);
        study.setIdentifier(STUDY_ID);
        doReturn(TOKEN).when(cacheProvider).getObject(CACHE_KEY, String.class);
        doReturn(study).when(studyService).getStudy(STUDY_ID);
        doReturn(account).when(accountDao).getAccountAfterAuthentication(SIGN_IN_WITH_EMAIL.getAccountId());
        account.setStatus(AccountStatus.DISABLED);
        
        service.emailSignIn(CONTEXT, SIGN_IN_WITH_EMAIL);
    }
    
    @Test(expected = ConsentRequiredException.class)
    public void emailSignInThrowsConsentRequired() {
        StudyParticipant participant = new StudyParticipant.Builder().withStatus(AccountStatus.DISABLED).build();
        
        doReturn(UNCONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any());
        doReturn(participant).when(participantService).getParticipant(study, account, false);
        study.setIdentifier(STUDY_ID);
        doReturn(TOKEN).when(cacheProvider).getObject(CACHE_KEY, String.class);
        doReturn(study).when(studyService).getStudy(STUDY_ID);
        doReturn(account).when(accountDao).getAccountAfterAuthentication(SIGN_IN_WITH_EMAIL.getAccountId());
        
        service.emailSignIn(CONTEXT, SIGN_IN_WITH_EMAIL);
    }
    
    @Test
    public void emailSignInAdminOK() {
        StudyParticipant participant = new StudyParticipant.Builder().withRoles(Sets.newHashSet(Roles.ADMIN)).build();
        
        doReturn(UNCONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any());
        doReturn(participant).when(participantService).getParticipant(study, account, false);
        doReturn(TOKEN).when(cacheProvider).getObject(CACHE_KEY, String.class);
        doReturn(study).when(studyService).getStudy(STUDY_ID);
        doReturn(account).when(accountDao).getAccountAfterAuthentication(SIGN_IN_WITH_EMAIL.getAccountId());
        
        service.emailSignIn(CONTEXT, SIGN_IN_WITH_EMAIL);
    }
    
    @Test
    public void reauthentication() {
        ((GenericAccount)account).setId(USER_ID);
        account.setReauthToken(REAUTH_TOKEN);

        StudyParticipant participant = new StudyParticipant.Builder().withEmail(RECIPIENT_EMAIL).build();
        doReturn(CONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any());
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
        SignIn signIn = new SignIn.Builder().withStudy(STUDY_ID).build();
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
    public void requestPhoneSignIn() { 
        study.setShortName("AppName");
        String cacheKey = TestConstants.PHONE.getNumber() + ":api:phoneSignInRequest";
        when(accountDao.getAccount(SIGN_IN_WITH_PHONE.getAccountId())).thenReturn(account);
        when(service.getPhoneToken()).thenReturn("123456");
        
        service.requestPhoneSignIn(SIGN_IN_REQUEST_WITH_PHONE);
        
        verify(cacheProvider).getObject(cacheKey, String.class);
        verify(cacheProvider).setObject(cacheKey, "123456", 300);
        verify(notificationsService).sendSMSMessage(study.getStudyIdentifier(), TestConstants.PHONE,
                "Enter 123-456 to sign in to AppName");
    }
    
    @Test
    public void requestPhoneSignInFails() {
        // This should fail silently, or we risk giving away information about accounts in the system.
        service.requestPhoneSignIn(SIGN_IN_REQUEST_WITH_PHONE);
        
        verify(cacheProvider, never()).setObject(any(), any(), anyInt());
        verify(notificationsService, never()).sendSMSMessage(any(), any(), any());
    }    
    
    @Test
    public void phoneSignIn() {
        // Put some stuff in participant to verify session is initialized
        StudyParticipant participant = new StudyParticipant.Builder()
                .withEmail(RECIPIENT_EMAIL).withFirstName("Test").withLastName("Tester").build();
        
        String cacheKey = TestConstants.PHONE.getNumber() + ":api:phoneSignInRequest";
        when(cacheProvider.getObject(cacheKey, String.class)).thenReturn(TOKEN);
        when(accountDao.getAccountAfterAuthentication(ACCOUNT_ID_WITH_PHONE)).thenReturn(account);
        when(participantService.getParticipant(study, account, false)).thenReturn(participant);
        when(consentService.getConsentStatuses(any())).thenReturn(CONSENTED_STATUS_MAP);
        
        UserSession session = service.phoneSignIn(CONTEXT, SIGN_IN_WITH_PHONE);
        
        assertEquals(RECIPIENT_EMAIL, session.getParticipant().getEmail());
        assertEquals("Test", session.getParticipant().getFirstName());
        assertEquals("Tester", session.getParticipant().getLastName());
        
        // this doesn't pass if our mock calls above aren't executed, but verify these:
        verify(cacheProvider).removeObject(cacheKey);
        verify(cacheProvider).setUserSession(session);
        verify(accountDao).verifyChannel(ChannelType.PHONE, account);
    }

    @Test(expected = AuthenticationFailedException.class)
    public void phoneSignInFails() {
        service.phoneSignIn(CONTEXT, SIGN_IN_WITH_PHONE);
    }
    
    @Test
    public void verifyEmail() {
        EmailVerification ev = new EmailVerification("sptoken");
        when(accountWorkflowService.verifyEmail(ev)).thenReturn(account);
        
        service.verifyEmail(ev);
        
        verify(accountWorkflowService).verifyEmail(ev);
        verify(accountDao).verifyEmail(account);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void verifyEmailInvalid() {
        EmailVerification ev = new EmailVerification(null);
        service.verifyEmail(ev);
    }

}
