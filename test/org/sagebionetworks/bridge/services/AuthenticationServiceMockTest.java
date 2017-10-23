package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.util.LinkedHashSet;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.exceptions.AccountDisabledException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountStatus;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.GenericAccount;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.MimeType;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.email.BasicEmailProvider;
import org.sagebionetworks.bridge.validators.PasswordResetValidator;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

@RunWith(MockitoJUnitRunner.class)
public class AuthenticationServiceMockTest {
    
    private static final String SUPPORT_EMAIL = "support@support.com";
    private static final String STUDY_ID = "test-study";
    private static final String RECIPIENT_EMAIL = "email@email.com";
    private static final String TOKEN = "ABC-DEF";
    private static final String REAUTH_TOKEN = "GHI-JKL";
    private static final String USER_ID = "user-id";
    private static final String PASSWORD = "password";
    private static final SignIn SIGN_IN_REQUEST = new SignIn.Builder().withStudy(STUDY_ID).withEmail(RECIPIENT_EMAIL)
            .build();
    private static final SignIn TOKEN_SIGN_IN = new SignIn.Builder().withStudy(STUDY_ID).withEmail(RECIPIENT_EMAIL)
            .withToken(TOKEN).build();
    private static final SignIn PASSWORD_SIGN_IN = new SignIn.Builder().withStudy(STUDY_ID).withEmail(RECIPIENT_EMAIL)
            .withPassword(PASSWORD).build();
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
    private static final ClientInfo INFO = ClientInfo.UNKNOWN_CLIENT;
    private static final LinkedHashSet<String> LANGS = TestUtils.newLinkedHashSet("en");
    private static final StudyParticipant PARTICIPANT = new StudyParticipant.Builder().build();

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
    @Captor
    private ArgumentCaptor<String> stringCaptor;
    @Captor
    private ArgumentCaptor<BasicEmailProvider> providerCaptor;
    @Captor
    private ArgumentCaptor<UserSession> sessionCaptor;
    @Captor
    private ArgumentCaptor<SignIn> signInCaptor;
    @Captor
    private ArgumentCaptor<Account> accountCaptor;

    private Study study;

    private Account account;
    
    private AuthenticationService service;

    @Before
    public void before() {
        study = Study.create();
        study.setIdentifier(STUDY_ID);
        study.setEmailSignInEnabled(true);
        study.setEmailSignInTemplate(new EmailTemplate("subject","body",MimeType.TEXT));
        study.setSupportEmail(SUPPORT_EMAIL);
        study.setName("Sender");
        
        account = new GenericAccount();
        
        service = spy(new AuthenticationService());
        service.setCacheProvider(cacheProvider);
        service.setBridgeConfig(config);
        service.setConsentService(consentService);
        service.setOptionsService(optionsService);
        service.setAccountDao(accountDao);
        service.setPasswordResetValidator(passwordResetValidator);
        service.setParticipantService(participantService);
        service.setSendMailService(sendMailService);
        service.setStudyService(studyService);

        doReturn(study).when(studyService).getStudy(STUDY_ID);
    }
    
    @Test
    public void signIn() throws Exception {
        account.setReauthToken(REAUTH_TOKEN);
        doReturn(account).when(accountDao).authenticate(study, PASSWORD_SIGN_IN);
        doReturn(PARTICIPANT).when(participantService).getParticipant(study, account, false);
        
        UserSession retrieved = service.signIn(study, CONTEXT, PASSWORD_SIGN_IN);
        assertEquals(REAUTH_TOKEN, retrieved.getReauthToken());
    }
    
    @Test
    public void requestEmailSignIn() throws Exception {
        doReturn(account).when(accountDao).getAccountWithEmail(study, RECIPIENT_EMAIL);
        doReturn(TOKEN).when(service).getVerificationToken();
        
        service.requestEmailSignIn(SIGN_IN_REQUEST);
        
        verify(accountDao).getAccountWithEmail(study, RECIPIENT_EMAIL);
        
        verify(cacheProvider).setSignIn(eq(TOKEN), eq(STUDY_ID + ":" + RECIPIENT_EMAIL), signInCaptor.capture(),
                eq(300));
        SignIn signIn = signInCaptor.getValue();
        assertEquals(SIGN_IN_REQUEST, signIn);

        verify(sendMailService).sendEmail(providerCaptor.capture());
        
        BasicEmailProvider provider = providerCaptor.getValue();
        assertEquals(TOKEN, provider.getTokenMap().get("token"));
        assertEquals(study, provider.getStudy());
        assertEquals(RECIPIENT_EMAIL, Iterables.getFirst(provider.getRecipientEmails(), null));
    }
    
    @Test
    public void requestEmailSignInFailureDelays() throws Exception {
        service.getEmailSignInRequestInMillis().set(1000);
        doReturn(null).when(accountDao).getAccountWithEmail(any(), any());
        
        long start = System.currentTimeMillis();
        service.requestEmailSignIn(SIGN_IN_REQUEST);
        long total = System.currentTimeMillis()-start;
        assertTrue(total >= 1000);
        service.getEmailSignInRequestInMillis().set(0);
    }
    
    @Test(expected = UnauthorizedException.class)
    public void requestEmailSignInDisabled() throws Exception {
        study.setEmailSignInEnabled(false);
        
        service.requestEmailSignIn(SIGN_IN_REQUEST);
    }
    
    @Test
    public void signOut() {
        StudyIdentifier studyIdentifier = new StudyIdentifierImpl(STUDY_ID);
        
        UserSession session = new UserSession();
        session.setStudyIdentifier(studyIdentifier);
        session.setParticipant(new StudyParticipant.Builder().withEmail("email@email.com").build());
        service.signOut(session);
        
        verify(accountDao).signOut(studyIdentifier, "email@email.com");
        verify(cacheProvider).removeSession(session);
    }
    
    @Test
    public void signOutNoSessionToken() {
        service.signOut(null);
        
        verify(accountDao, never()).signOut(any(), any());
        verify(cacheProvider, never()).removeSession(any());
    }
    
    @Test
    public void requestEmailSignInEmailNotRegistered() throws Exception {
        doReturn(null).when(accountDao).getAccountWithEmail(study, RECIPIENT_EMAIL);
        doReturn(TOKEN).when(service).getVerificationToken();
        
        service.requestEmailSignIn(SIGN_IN_REQUEST);

        verify(cacheProvider, never()).setString(eq(TOKEN), any(), eq(60));
        verify(sendMailService, never()).sendEmail(any());
    }

    @Test
    public void emailSignIn() {
        doReturn(TOKEN_SIGN_IN).when(cacheProvider).getSignIn(TOKEN_SIGN_IN.getToken());
        account.setReauthToken(REAUTH_TOKEN);
        doReturn(TOKEN).when(cacheProvider).getString(REAUTH_TOKEN);
        doReturn(account).when(accountDao).getAccountAfterAuthentication(study, RECIPIENT_EMAIL);
        doReturn(PARTICIPANT).when(participantService).getParticipant(study, account, false);
        doReturn(CONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any());
        
        UserSession retSession = service.emailSignIn(INFO, LANGS, TOKEN_SIGN_IN);
        
        assertNotNull(retSession);
        assertEquals(REAUTH_TOKEN, retSession.getReauthToken());
        verify(accountDao, never()).updateAccount(account);
        verify(accountDao).getAccountAfterAuthentication(study, RECIPIENT_EMAIL);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void emailSignInRequestMissingStudy() throws Exception {
        SignIn signInRequest = new SignIn.Builder().withEmail(RECIPIENT_EMAIL).withToken(TOKEN).build();

        service.requestEmailSignIn(signInRequest);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void emailSignInRequestMissingEmail() throws Exception {
        SignIn signInRequest = new SignIn.Builder().withStudy(STUDY_ID).withToken(TOKEN).build();
        
        service.requestEmailSignIn(signInRequest);
    }
    
    @Test
    public void emailSignInRequestReturnsExistingToken() throws Exception {
        doReturn(TOKEN).when(cacheProvider).hasSignInToken(STUDY_ID+":"+RECIPIENT_EMAIL);
        doReturn(account).when(accountDao).getAccountWithEmail(study, RECIPIENT_EMAIL);
        
        SignIn signInRequest = new SignIn.Builder().withStudy(STUDY_ID).withEmail(RECIPIENT_EMAIL).build();
        
        service.requestEmailSignIn(signInRequest);
        
        verify(cacheProvider, never()).setSignIn(eq(TOKEN), eq(STUDY_ID+":"+RECIPIENT_EMAIL), any(), anyInt());
        
        verify(sendMailService).sendEmail(providerCaptor.capture());
        BasicEmailProvider provider = providerCaptor.getValue();
        assertEquals(TOKEN, provider.getTokenMap().get("token"));
    }
    
    @Test(expected = InvalidEntityException.class)
    public void emailSignInMissingToken() {
        service.emailSignIn(INFO, LANGS, SIGN_IN_REQUEST); // not SIGN_IN which has the token
    }
    
    @Test
    public void emailSignInEnablesAccount() {
        StudyParticipant participant = new StudyParticipant.Builder().withStatus(AccountStatus.ENABLED).build();
        
        doReturn(TOKEN_SIGN_IN).when(cacheProvider).getSignIn(TOKEN_SIGN_IN.getToken());
        doReturn(participant).when(participantService).getParticipant(study, account, false);
        study.setIdentifier(STUDY_ID);
        doReturn(study).when(studyService).getStudy(STUDY_ID);
        doReturn(account).when(accountDao).getAccountAfterAuthentication(study, RECIPIENT_EMAIL);
        doReturn(CONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any());
        account.setStatus(AccountStatus.UNVERIFIED);
        
        service.emailSignIn(INFO, LANGS, TOKEN_SIGN_IN);
        
        verify(accountDao).updateAccount(accountCaptor.capture());
        assertEquals(AccountStatus.ENABLED, accountCaptor.getValue().getStatus());
    }
    
    @Test(expected = AccountDisabledException.class)
    public void emailSignInThrowsAccountDisabled() {
        StudyParticipant participant = new StudyParticipant.Builder().withStatus(AccountStatus.DISABLED).build();
        
        doReturn(TOKEN_SIGN_IN).when(cacheProvider).getSignIn(TOKEN_SIGN_IN.getToken());
        doReturn(participant).when(participantService).getParticipant(study, account, false);
        study.setIdentifier(STUDY_ID);
        doReturn(study).when(studyService).getStudy(STUDY_ID);
        doReturn(account).when(accountDao).getAccountAfterAuthentication(study, RECIPIENT_EMAIL);
        account.setStatus(AccountStatus.DISABLED);
        
        service.emailSignIn(INFO, LANGS, TOKEN_SIGN_IN);
    }
    
    @Test(expected = ConsentRequiredException.class)
    public void emailSignInThrowsConsentRequired() {
        StudyParticipant participant = new StudyParticipant.Builder().withStatus(AccountStatus.DISABLED).build();
        
        doReturn(TOKEN_SIGN_IN).when(cacheProvider).getSignIn(TOKEN_SIGN_IN.getToken());
        doReturn(UNCONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any());
        doReturn(participant).when(participantService).getParticipant(study, account, false);
        study.setIdentifier(STUDY_ID);
        doReturn(study).when(studyService).getStudy(STUDY_ID);
        doReturn(account).when(accountDao).getAccountAfterAuthentication(study, RECIPIENT_EMAIL);
        
        service.emailSignIn(INFO, LANGS, TOKEN_SIGN_IN);
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
        service.reauthenticate(study, CONTEXT, TOKEN_SIGN_IN); // doesn't have reauth token
    }
    
    @Test(expected = ConsentRequiredException.class)
    public void reauthThrowsUnconsentedException() {
        StudyParticipant participant = new StudyParticipant.Builder().withStatus(AccountStatus.ENABLED).build();
        
        doReturn(UNCONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any());
        doReturn(account).when(accountDao).reauthenticate(study, REAUTH_REQUEST);
        doReturn(participant).when(participantService).getParticipant(study, account, false);
        
        service.reauthenticate(study, CONTEXT, REAUTH_REQUEST);
    }
    
}
