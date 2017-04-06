package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.LinkedHashSet;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.exceptions.AuthenticationFailedException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.LimitExceededException;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.MimeType;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.services.email.EmailSignInEmailProvider;
import org.sagebionetworks.bridge.services.email.MimeTypeEmail;
import org.sagebionetworks.bridge.validators.PasswordResetValidator;

@RunWith(MockitoJUnitRunner.class)
public class AuthenticationServiceMockTest {
    
    private static final CriteriaContext CONTEXT = new CriteriaContext.Builder()
            .withStudyIdentifier(TestConstants.TEST_STUDY).build();
    private static final String RECIPIENT_EMAIL = "email@email.com";
    private static final String TOKEN = "ABC-DEF";
    
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
    private Study study;
    @Mock
    private Account account;
    @Mock
    private UserSession session;
    
    @Captor
    private ArgumentCaptor<String> stringCaptor;
    @Captor
    private ArgumentCaptor<EmailSignInEmailProvider> providerCaptor;
    
    @Spy
    private AuthenticationService service;

    @Before
    public void before() {
        service.setCacheProvider(cacheProvider);
        service.setBridgeConfig(config);
        service.setConsentService(consentService);
        service.setOptionsService(optionsService);
        service.setAccountDao(accountDao);
        service.setPasswordResetValidator(passwordResetValidator);
        service.setParticipantService(participantService);
        service.setSendMailService(sendMailService);
        service.setStudyService(studyService);
        
        doReturn(study).when(studyService).getStudy("test-study");
        doReturn("test-study").when(study).getIdentifier();
        doReturn(new StudyIdentifierImpl("test-study")).when(study).getStudyIdentifier();
        doReturn(new EmailTemplate("subject","body",MimeType.TEXT)).when(study).getEmailSignInTemplate();
        doReturn("sender@sender.com").when(study).getSupportEmail();
        doReturn("Sender").when(study).getName();
    }
    
    @Test
    public void requestEmailSignIn() throws Exception {
        String cacheKey = "email@email.com:test-study:signInRequest";
        doReturn(account).when(accountDao).getAccountWithEmail(study, RECIPIENT_EMAIL);
        
        SignIn signInRequest = new SignIn("test-study", RECIPIENT_EMAIL, null, null);
        
        service.requestEmailSignIn(signInRequest);
        
        verify(cacheProvider).getString(stringCaptor.capture());
        assertEquals(cacheKey, stringCaptor.getValue());
        
        verify(accountDao).getAccountWithEmail(study, RECIPIENT_EMAIL);
        
        verify(cacheProvider).setString(eq(cacheKey), stringCaptor.capture(), eq(60));
        assertNotNull(stringCaptor.getValue());

        verify(sendMailService).sendEmail(providerCaptor.capture());
        
        EmailSignInEmailProvider provider = providerCaptor.getValue();
        MimeTypeEmail email = provider.getMimeTypeEmail();
        assertEquals(RECIPIENT_EMAIL, email.getRecipientAddresses().get(0));
        assertEquals("\"Sender\" <sender@sender.com>", email.getSenderAddress());
        assertEquals("body", email.getMessageParts().get(0).getContent());
    }
    
    @Test(expected = LimitExceededException.class)
    public void requestEmailSignInLimitExceeded() {
        String cacheKey = "email@email.com:test-study:signInRequest";
        
        doReturn("something").when(cacheProvider).getString(cacheKey);
        
        SignIn signInRequest = new SignIn("test-study", RECIPIENT_EMAIL, null, null);
        
        service.requestEmailSignIn(signInRequest);
    }
    
    @Test
    public void requestEmailSignInEmailNotRegistered() {
        String cacheKey = "email@email.com:test-study:signInRequest";
        doReturn(null).when(accountDao).getAccountWithEmail(study, RECIPIENT_EMAIL);
        
        SignIn signInRequest = new SignIn("test-study", RECIPIENT_EMAIL, null, null);
        
        service.requestEmailSignIn(signInRequest);

        verify(cacheProvider, never()).setString(eq(cacheKey), any(), eq(60));
        verify(sendMailService, never()).sendEmail(any());
    }

    @Test
    public void emailSignIn() {
        StudyParticipant participant = new StudyParticipant.Builder().build();
        CriteriaContext context = new CriteriaContext.Builder()
                .withLanguages(new LinkedHashSet<String>())
                .withStudyIdentifier(study.getStudyIdentifier()).build();
        String cacheKey = "email@email.com:test-study:signInRequest";
        doReturn(TOKEN).when(cacheProvider).getString(cacheKey);
        doReturn(account).when(accountDao).getAccountWithEmail(study, RECIPIENT_EMAIL);
        doReturn(participant).when(participantService).getParticipant(study, account, false);
        
        SignIn signIn = new SignIn(study.getIdentifier(), RECIPIENT_EMAIL, null, TOKEN);
        
        UserSession retSession = service.emailSignIn(context, signIn);
        
        assertNotNull(retSession);
        verify(accountDao).getAccountWithEmail(study, RECIPIENT_EMAIL);
        verify(cacheProvider).removeString(cacheKey);
    }
    
    @Test(expected = AuthenticationFailedException.class)
    public void emailSignInTokenWrong() {
        StudyParticipant participant = new StudyParticipant.Builder().build();
        CriteriaContext context = new CriteriaContext.Builder()
                .withLanguages(new LinkedHashSet<String>())
                .withStudyIdentifier(study.getStudyIdentifier()).build();
        String cacheKey = "email@email.com:test-study:signInRequest";
        doReturn(TOKEN).when(cacheProvider).getString(cacheKey);
        doReturn(account).when(accountDao).getAccountWithEmail(study, RECIPIENT_EMAIL);
        doReturn(participant).when(participantService).getParticipant(study, account, false);
        
        SignIn signIn = new SignIn(study.getIdentifier(), RECIPIENT_EMAIL, null, "wrongToken");
        
        service.emailSignIn(context, signIn);
    }
    
    @Test(expected = AuthenticationFailedException.class)
    public void emailSignInTokenNotSet() {
        StudyParticipant participant = new StudyParticipant.Builder().build();
        CriteriaContext context = new CriteriaContext.Builder()
                .withLanguages(new LinkedHashSet<String>())
                .withStudyIdentifier(study.getStudyIdentifier()).build();
        String cacheKey = "email@email.com:test-study:signInRequest";
        doReturn(null).when(cacheProvider).getString(cacheKey);
        doReturn(account).when(accountDao).getAccountWithEmail(study, RECIPIENT_EMAIL);
        doReturn(participant).when(participantService).getParticipant(study, account, false);
        
        SignIn signIn = new SignIn(study.getIdentifier(), RECIPIENT_EMAIL, null, "wrongToken");
        
        service.emailSignIn(context, signIn);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void emailSignInRequestMissingStudy() {
        SignIn signInRequest = new SignIn(null, "email@email.com", null, "ABC");

        service.requestEmailSignIn(signInRequest);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void emailSignInRequestMissingEmail() {
        SignIn signInRequest = new SignIn(TestConstants.TEST_STUDY_IDENTIFIER, "", null, "ABC");
        
        service.requestEmailSignIn(signInRequest);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void emailSignInMissingStudy() {
        SignIn signInRequest = new SignIn(null, "email@email.com", null, "ABC");

        service.emailSignIn(CONTEXT, signInRequest);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void emailSignInMissingEmail() {
        SignIn signInRequest = new SignIn(TestConstants.TEST_STUDY_IDENTIFIER, null, null, "ABC");

        service.emailSignIn(CONTEXT, signInRequest);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void emailSignInMissingToken() {
        SignIn signInRequest = new SignIn(TestConstants.TEST_STUDY_IDENTIFIER, "email@email.com", null, null);

        service.emailSignIn(CONTEXT, signInRequest);
    }
}
