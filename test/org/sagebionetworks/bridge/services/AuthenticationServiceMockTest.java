package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.exceptions.AccountDisabledException;
import org.sagebionetworks.bridge.exceptions.AuthenticationFailedException;
import org.sagebionetworks.bridge.exceptions.ConsentRequiredException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.LimitExceededException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountStatus;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.MimeType;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.email.BasicEmailProvider;
import org.sagebionetworks.bridge.validators.PasswordResetValidator;

import com.google.common.collect.ImmutableMap;

@RunWith(MockitoJUnitRunner.class)
public class AuthenticationServiceMockTest {
    
    private static final String STUDY_ID = "test-study";
    private static final String CACHE_KEY = "email@email.com:test-study:signInRequest";
    private static final String RECIPIENT_EMAIL = "email@email.com";
    private static final String TOKEN = "ABC-DEF";
    private static final String NEW_PASSWORD = "newPassword";
    private static final SignIn SIGN_IN_REQUEST = new SignIn(STUDY_ID, RECIPIENT_EMAIL, null, null);
    private static final SignIn SIGN_IN = new SignIn(STUDY_ID, RECIPIENT_EMAIL, null, TOKEN);
    private static final SignIn SIGN_IN_WITH_PASSWORD = new SignIn(STUDY_ID, RECIPIENT_EMAIL, NEW_PASSWORD, TOKEN);
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

    private Study study;

    private Account account;
    
    private AuthenticationService service;

    @Before
    public void before() {
        study = Study.create();
        study.setIdentifier(STUDY_ID);
        study.setEmailSignInEnabled(true);
        study.setEmailSignInTemplate(new EmailTemplate("subject","body",MimeType.TEXT));
        study.setSupportEmail("sender@sender.com");
        study.setName("Sender");
        
        account = new SimpleAccount();
        
        service = new AuthenticationService();
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
    public void requestEmailSignIn() throws Exception {
        doReturn(account).when(accountDao).getAccountWithEmail(study, RECIPIENT_EMAIL);
        
        service.requestEmailSignIn(SIGN_IN_REQUEST);
        
        verify(cacheProvider).getString(stringCaptor.capture());
        assertEquals(CACHE_KEY, stringCaptor.getValue());
        
        verify(accountDao).getAccountWithEmail(study, RECIPIENT_EMAIL);
        
        verify(cacheProvider).setString(eq(CACHE_KEY), stringCaptor.capture(), eq(60));
        assertNotNull(stringCaptor.getValue());

        verify(sendMailService).sendEmail(providerCaptor.capture());
        
        BasicEmailProvider provider = providerCaptor.getValue();
        assertTrue(provider.getTokenMap().get("token").length() == 32); // length of GUID without "-" symbol
        assertEquals(study, provider.getStudy());
        assertEquals(RECIPIENT_EMAIL, provider.getRecipientEmail());
    }
    
    @Test(expected = UnauthorizedException.class)
    public void requestEmailSignInDisabled() {
        study.setEmailSignInEnabled(false);
        
        service.requestEmailSignIn(SIGN_IN_REQUEST);
    }
    
    @Test(expected = LimitExceededException.class)
    public void requestEmailSignInLimitExceeded() {
        doReturn("something").when(cacheProvider).getString(CACHE_KEY);
        
        service.requestEmailSignIn(SIGN_IN_REQUEST);
    }
    
    @Test
    public void requestEmailSignInEmailNotRegistered() {
        doReturn(null).when(accountDao).getAccountWithEmail(study, RECIPIENT_EMAIL);
        
        service.requestEmailSignIn(SIGN_IN_REQUEST);

        verify(cacheProvider, never()).setString(eq(CACHE_KEY), any(), eq(60));
        verify(sendMailService, never()).sendEmail(any());
    }

    @Test
    public void emailSignIn() {
        doReturn(TOKEN).when(cacheProvider).getString(CACHE_KEY);
        doReturn(account).when(accountDao).getAccountWithEmail(study, RECIPIENT_EMAIL);
        doReturn(PARTICIPANT).when(participantService).getParticipant(study, account, false);
        doReturn(CONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any());
        
        UserSession retSession = service.emailSignIn(CONTEXT, SIGN_IN);
        
        assertNotNull(retSession);
        verify(accountDao, never()).changePassword(eq(account), any());
        verify(accountDao).getAccountWithEmail(study, RECIPIENT_EMAIL);
        verify(cacheProvider).removeString(CACHE_KEY);
    }
    
    @Test(expected = AuthenticationFailedException.class)
    public void emailSignInTokenWrong() {
        doReturn(TOKEN).when(cacheProvider).getString(CACHE_KEY);
        doReturn(account).when(accountDao).getAccountWithEmail(study, RECIPIENT_EMAIL);
        doReturn(PARTICIPANT).when(participantService).getParticipant(study, account, false);
        
        SignIn signIn = new SignIn(study.getIdentifier(), RECIPIENT_EMAIL, null, "wrongToken");
        
        service.emailSignIn(CONTEXT, signIn);
    }
    
    @Test(expected = AuthenticationFailedException.class)
    public void emailSignInTokenNotSet() {
        doReturn(null).when(cacheProvider).getString(CACHE_KEY);
        doReturn(account).when(accountDao).getAccountWithEmail(study, RECIPIENT_EMAIL);
        doReturn(PARTICIPANT).when(participantService).getParticipant(study, account, false);
        
        SignIn signIn = new SignIn(study.getIdentifier(), RECIPIENT_EMAIL, null, "wrongToken");
        
        service.emailSignIn(CONTEXT, signIn);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void emailSignInRequestMissingStudy() {
        SignIn signInRequest = new SignIn(null, RECIPIENT_EMAIL, null, TOKEN);

        service.requestEmailSignIn(signInRequest);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void emailSignInRequestMissingEmail() {
        SignIn signInRequest = new SignIn(STUDY_ID, "", null, TOKEN);
        
        service.requestEmailSignIn(signInRequest);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void emailSignInMissingStudy() {
        SignIn signInRequest = new SignIn(null, RECIPIENT_EMAIL, null, TOKEN);

        service.emailSignIn(CONTEXT, signInRequest);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void emailSignInMissingEmail() {
        SignIn signInRequest = new SignIn(STUDY_ID, null, null, TOKEN);

        service.emailSignIn(CONTEXT, signInRequest);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void emailSignInMissingToken() {
        service.emailSignIn(CONTEXT, SIGN_IN_REQUEST); // not SIGN_IN which has the token
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void emailSignInThrowsEntityNotFound() {
        StudyParticipant participant = new StudyParticipant.Builder().withStatus(AccountStatus.DISABLED).build();
        
        doReturn(participant).when(participantService).getParticipant(study, account, false);
        study.setIdentifier(STUDY_ID);
        doReturn(TOKEN).when(cacheProvider).getString(CACHE_KEY);
        doReturn(study).when(studyService).getStudy(STUDY_ID);
        doReturn(account).when(accountDao).getAccountWithEmail(study, RECIPIENT_EMAIL);
        account.setStatus(AccountStatus.UNVERIFIED);
        
        service.emailSignIn(CONTEXT, SIGN_IN);
    }
    
    @Test(expected = AccountDisabledException.class)
    public void emailSignInThrowsAccountDisabled() {
        StudyParticipant participant = new StudyParticipant.Builder().withStatus(AccountStatus.DISABLED).build();
        
        doReturn(participant).when(participantService).getParticipant(study, account, false);
        study.setIdentifier(STUDY_ID);
        doReturn(TOKEN).when(cacheProvider).getString(CACHE_KEY);
        doReturn(study).when(studyService).getStudy(STUDY_ID);
        doReturn(account).when(accountDao).getAccountWithEmail(study, RECIPIENT_EMAIL);
        account.setStatus(AccountStatus.DISABLED);
        
        service.emailSignIn(CONTEXT, SIGN_IN);
    }
    
    @Test(expected = ConsentRequiredException.class)
    public void emailSignInThrowsConsentRequired() {
        StudyParticipant participant = new StudyParticipant.Builder().withStatus(AccountStatus.DISABLED).build();
        
        doReturn(UNCONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any());
        doReturn(participant).when(participantService).getParticipant(study, account, false);
        study.setIdentifier(STUDY_ID);
        doReturn(TOKEN).when(cacheProvider).getString(CACHE_KEY);
        doReturn(study).when(studyService).getStudy(STUDY_ID);
        doReturn(account).when(accountDao).getAccountWithEmail(study, RECIPIENT_EMAIL);
        
        service.emailSignIn(CONTEXT, SIGN_IN);
    }
    
    @Test
    public void emailSignInChangesPassword() {
        doReturn(TOKEN).when(cacheProvider).getString(CACHE_KEY);
        doReturn(account).when(accountDao).getAccountWithEmail(study, RECIPIENT_EMAIL);
        doReturn(PARTICIPANT).when(participantService).getParticipant(study, account, false);
        doReturn(CONSENTED_STATUS_MAP).when(consentService).getConsentStatuses(any());
        
        UserSession retSession = service.emailSignIn(CONTEXT, SIGN_IN_WITH_PASSWORD);
        
        assertNotNull(retSession);
        verify(accountDao).changePassword(account, NEW_PASSWORD);
        verify(accountDao).getAccountWithEmail(study, RECIPIENT_EMAIL);
        verify(cacheProvider).removeString(CACHE_KEY);
    }
}
