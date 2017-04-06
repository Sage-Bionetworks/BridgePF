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

import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.exceptions.AuthenticationFailedException;
import org.sagebionetworks.bridge.exceptions.LimitExceededException;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.MimeType;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.services.email.EmailSignInEmailProvider;
import org.sagebionetworks.bridge.services.email.MimeTypeEmail;
import org.sagebionetworks.bridge.validators.EmailValidator;
import org.sagebionetworks.bridge.validators.EmailVerificationValidator;
import org.sagebionetworks.bridge.validators.PasswordResetValidator;
import org.sagebionetworks.bridge.validators.SignInValidator;

@RunWith(MockitoJUnitRunner.class)
public class AuthenticationServiceMockTest {
    
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
    private EmailVerificationValidator verificationValidator;
    @Mock
    private SignInValidator signInValidator;
    @Mock
    private PasswordResetValidator passwordResetValidator;
    @Mock
    private EmailValidator emailValidator;
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
        service.setEmailVerificationValidator(verificationValidator);
        service.setSignInValidator(signInValidator);
        service.setPasswordResetValidator(passwordResetValidator);
        service.setEmailValidator(emailValidator);
        service.setParticipantService(participantService);
        service.setSendMailService(sendMailService);
        
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
        
        service.requestEmailSignIn(study, RECIPIENT_EMAIL);
        
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
        
        service.requestEmailSignIn(study, RECIPIENT_EMAIL);
    }
    
    @Test
    public void requestEmailSignInEmailNotRegistered() {
        String cacheKey = "email@email.com:test-study:signInRequest";
        doReturn(null).when(accountDao).getAccountWithEmail(study, RECIPIENT_EMAIL);
        
        service.requestEmailSignIn(study, RECIPIENT_EMAIL);

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
        
        UserSession retSession = service.emailSignIn(study, context, RECIPIENT_EMAIL, TOKEN);
        
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
        
        service.emailSignIn(study, context, RECIPIENT_EMAIL, "wrongToken");
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
        
        service.emailSignIn(study, context, RECIPIENT_EMAIL, "wrongToken");
    }
}
