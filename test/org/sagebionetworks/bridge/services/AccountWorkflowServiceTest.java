package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;

import javax.mail.internet.MimeBodyPart;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.Email;
import org.sagebionetworks.bridge.models.accounts.EmailVerification;
import org.sagebionetworks.bridge.models.accounts.PasswordReset;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.MimeType;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.email.BasicEmailProvider;
import org.sagebionetworks.bridge.services.email.MimeTypeEmail;
import org.sagebionetworks.bridge.services.email.MimeTypeEmailProvider;

@RunWith(MockitoJUnitRunner.class)
public class AccountWorkflowServiceTest {
    
    private static final String SUPPORT_EMAIL = "support@support.com";
    private static final String SPTOKEN = "sptoken";
    private static final String USER_ID = "userId";
    private static final String EMAIL = "email@email.com";

    @Mock
    private StudyService mockStudyService;
    
    @Mock
    private SendMailService mockSendMailService;
    
    @Mock
    private AccountDao mockAccountDao;
    
    @Mock
    private CacheProvider mockCacheProvider;
    
    @Mock
    private Account mockAccount;
    
    @Captor
    private ArgumentCaptor<BasicEmailProvider> emailProviderCaptor;
    
    @Captor
    private ArgumentCaptor<Account> accountCaptor;
    
    private Study study;
    
    @Spy
    private AccountWorkflowService service;
    
    @Before
    public void before() {
        EmailTemplate verifyEmailTemplate = new EmailTemplate("VE ${studyName}", "Body ${url}", MimeType.TEXT);
        EmailTemplate resetPasswordTemplate = new EmailTemplate("RP ${studyName}", "Body ${url}", MimeType.TEXT);
        EmailTemplate accountExistsTemplate = new EmailTemplate("AE ${studyName}", "Body ${url}", MimeType.TEXT);
        
        study = Study.create();
        study.setIdentifier(TEST_STUDY_IDENTIFIER);
        study.setName("This study name");
        study.setSupportEmail(SUPPORT_EMAIL);
        study.setVerifyEmailTemplate(verifyEmailTemplate);
        study.setResetPasswordTemplate(resetPasswordTemplate);
        study.setAccountExistsTemplate(accountExistsTemplate);
        
        service.setAccountDao(mockAccountDao);
        service.setCacheProvider(mockCacheProvider);
        service.setSendMailService(mockSendMailService);
        service.setStudyService(mockStudyService);
    }
    
    @Test
    public void sendEmailVerificationToken() throws Exception {
        when(service.createTimeLimitedToken()).thenReturn("ABC");
        
        service.sendEmailVerificationToken(study, USER_ID, EMAIL);
        
        verify(mockSendMailService).sendEmail(emailProviderCaptor.capture());
        
        MimeTypeEmailProvider provider = emailProviderCaptor.getValue();
        MimeTypeEmail email = provider.getMimeTypeEmail();
        assertEquals("\"This study name\" <support@support.com>", email.getSenderAddress());
        assertEquals(1, email.getRecipientAddresses().size());
        assertEquals(EMAIL, email.getRecipientAddresses().get(0));
        assertEquals("VE This study name", email.getSubject());
        MimeBodyPart body = email.getMessageParts().get(0);
        String bodyString = (String)body.getContent();
        assertTrue(bodyString.contains("/mobile/verifyEmail.html?study=api&sptoken=ABC"));
    }
    
    @Test
    public void resendEmailVerificationToken() {
        when(mockStudyService.getStudy(TEST_STUDY)).thenReturn(study);
        when(mockAccountDao.getAccountWithEmail(study, EMAIL)).thenReturn(mockAccount);
        when(mockAccount.getId()).thenReturn(USER_ID);
        
        Email email = new Email(TEST_STUDY_IDENTIFIER, EMAIL);
        
        service.resendEmailVerificationToken(TEST_STUDY, email);
        
        verify(service).sendEmailVerificationToken(study, USER_ID, EMAIL);
    }
    
    @Test
    public void resendEmailVerificationTokenFailsWithMissingStudy() {
        when(mockStudyService.getStudy(TEST_STUDY)).thenThrow(new EntityNotFoundException(Study.class));
        when(mockAccountDao.getAccountWithEmail(study, EMAIL)).thenReturn(mockAccount);
        when(mockAccount.getId()).thenReturn(USER_ID);
        
        Email email = new Email(TEST_STUDY_IDENTIFIER, EMAIL);
        
        try {
            service.resendEmailVerificationToken(TEST_STUDY, email);
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
        }
        verify(service, never()).sendEmailVerificationToken(study, USER_ID, EMAIL);
    }
    
    @Test
    public void resendEmailVerificationTokenFailsQuietlyWithMissingAccount() {
        when(mockStudyService.getStudy(TEST_STUDY)).thenReturn(study);
        when(mockAccountDao.getAccountWithEmail(study, EMAIL)).thenReturn(null);
        when(mockAccount.getId()).thenReturn(USER_ID);
        
        Email email = new Email(TEST_STUDY_IDENTIFIER, EMAIL);
        
        service.resendEmailVerificationToken(TEST_STUDY, email);
        
        verify(service, never()).sendEmailVerificationToken(study, USER_ID, EMAIL);
    }
    
    @Test
    public void verifyEmail() {
        when(mockCacheProvider.getString(SPTOKEN)).thenReturn(
            TestUtils.createJson("{'studyId':'api','userId':'userId'}"));
        when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);
        when(mockAccountDao.getAccount(study, "userId")).thenReturn(mockAccount);
        when(mockAccount.getId()).thenReturn("accountId");
        
        EmailVerification verification = new EmailVerification(SPTOKEN);
        
        String accountId = service.verifyEmail(verification);
        assertEquals("accountId",accountId);
    }
    
    @Test(expected = BadRequestException.class)
    public void verifyEmailBadSptokenThrowsException() {
        when(mockCacheProvider.getString(SPTOKEN)).thenReturn(null);
        
        EmailVerification verification = new EmailVerification(SPTOKEN);
        
        service.verifyEmail(verification);
    }
    
    @Test
    public void notifyAccountExists() throws Exception {
        when(service.createTimeLimitedToken()).thenReturn("ABC");
        
        Email emailObj = new Email(TEST_STUDY_IDENTIFIER, EMAIL);
        
        service.notifyAccountExists(study, emailObj);
        
        verify(mockCacheProvider).setString("ABC:api", EMAIL, 60*60*2);
        verify(mockSendMailService).sendEmail(emailProviderCaptor.capture());
        MimeTypeEmailProvider provider = emailProviderCaptor.getValue();
        MimeTypeEmail email = provider.getMimeTypeEmail();
        assertEquals("\"This study name\" <support@support.com>", email.getSenderAddress());
        assertEquals(1, email.getRecipientAddresses().size());
        assertEquals(EMAIL, email.getRecipientAddresses().get(0));
        assertEquals("AE This study name", email.getSubject());
        MimeBodyPart body = email.getMessageParts().get(0);
        String bodyString = (String)body.getContent();
        assertTrue(bodyString.contains("/mobile/resetPassword.html?study=api&sptoken=ABC"));
    }
    
    @Test
    public void requestResetPassword() throws Exception {
        when(service.createTimeLimitedToken()).thenReturn("ABC");
        when(mockAccountDao.getAccountWithEmail(study, EMAIL)).thenReturn(mockAccount);
        
        Email emailObj = new Email(TEST_STUDY_IDENTIFIER, EMAIL);
        
        service.requestResetPassword(study, emailObj);
        
        verify(mockCacheProvider).setString("ABC:api", EMAIL, 60*60*2);
        verify(mockSendMailService).sendEmail(emailProviderCaptor.capture());
        MimeTypeEmailProvider provider = emailProviderCaptor.getValue();
        MimeTypeEmail email = provider.getMimeTypeEmail();
        assertEquals("\"This study name\" <support@support.com>", email.getSenderAddress());
        assertEquals(1, email.getRecipientAddresses().size());
        assertEquals(EMAIL, email.getRecipientAddresses().get(0));
        assertEquals("RP This study name", email.getSubject());
        MimeBodyPart body = email.getMessageParts().get(0);
        String bodyString = (String)body.getContent();
        assertTrue(bodyString.contains("/mobile/resetPassword.html?study=api&sptoken=ABC"));
    }
    
    @Test
    public void requestResetPasswordInvalidEmailFailsQuietly() throws Exception {
        when(service.createTimeLimitedToken()).thenReturn("ABC");
        when(mockAccountDao.getAccountWithEmail(study, EMAIL)).thenReturn(null);
        
        Email emailObj = new Email(TEST_STUDY_IDENTIFIER, EMAIL);
        
        service.requestResetPassword(study, emailObj);
        
        verify(mockCacheProvider, never()).setString("ABC:api", EMAIL, 60*5);
        verify(mockSendMailService, never()).sendEmail(emailProviderCaptor.capture());
    }

    @Test
    public void resetPassword() {
        when(mockCacheProvider.getString("sptoken:api")).thenReturn(EMAIL);
        when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);
        when(mockAccountDao.getAccountWithEmail(study, EMAIL)).thenReturn(mockAccount);

        PasswordReset passwordReset = new PasswordReset("newPassword", "sptoken", TEST_STUDY_IDENTIFIER);
        
        service.resetPassword(passwordReset);
        
        verify(mockCacheProvider).getString("sptoken:api");
        verify(mockCacheProvider).removeString("sptoken:api");
        verify(mockAccountDao).changePassword(mockAccount, "newPassword");
    }
    
    @Test
    public void resetPasswordInvalidSptokenThrowsException() {
        when(mockCacheProvider.getString("sptoken:api")).thenReturn(null);

        PasswordReset passwordReset = new PasswordReset("newPassword", "sptoken", TEST_STUDY_IDENTIFIER);
        try {
            service.resetPassword(passwordReset);
            fail("Should have thrown exception");
        } catch(BadRequestException e) {
            assertEquals("Password reset token has expired (or already been used).", e.getMessage());
        }
        verify(mockCacheProvider).getString("sptoken:api");
        verify(mockCacheProvider, never()).removeString("sptoken:api");
        verify(mockAccountDao, never()).changePassword(mockAccount, "newPassword");
    }
    
    @Test
    public void resetPasswordInvalidAccount() {
        when(mockCacheProvider.getString("sptoken:api")).thenReturn(EMAIL);
        when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);
        when(mockAccountDao.getAccountWithEmail(study, EMAIL)).thenReturn(null);

        PasswordReset passwordReset = new PasswordReset("newPassword", "sptoken", TEST_STUDY_IDENTIFIER);
        
        try {
            service.resetPassword(passwordReset);
            fail("Should have thrown an exception");
        } catch(EntityNotFoundException e) {
        }
        verify(mockCacheProvider).getString("sptoken:api");
        verify(mockCacheProvider).removeString("sptoken:api");
        verify(mockAccountDao, never()).changePassword(mockAccount, "newPassword");
    }
}
