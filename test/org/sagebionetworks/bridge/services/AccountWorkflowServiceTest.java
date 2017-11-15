package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
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
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.EmailVerification;
import org.sagebionetworks.bridge.models.accounts.PasswordReset;
import org.sagebionetworks.bridge.models.accounts.Phone;
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
    private static final AccountId ACCOUNT_ID_WITH_ID = AccountId.forId(TEST_STUDY_IDENTIFIER, USER_ID);
    private static final AccountId ACCOUNT_ID_WITH_EMAIL = AccountId.forEmail(TEST_STUDY_IDENTIFIER, EMAIL);
    private static final AccountId ACCOUNT_ID_WITH_PHONE = AccountId.forPhone(TEST_STUDY_IDENTIFIER, TestConstants.PHONE);

    @Mock
    private StudyService mockStudyService;
    
    @Mock
    private SendMailService mockSendMailService;
    
    @Mock
    private NotificationsService mockNotificationsService;
    
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
    
    @Captor
    private ArgumentCaptor<String> stringCaptor;
    
    @Captor
    private ArgumentCaptor<String> secondStringCaptor;
    
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
        study.setShortName("ShortName");
        study.setSupportEmail(SUPPORT_EMAIL);
        study.setVerifyEmailTemplate(verifyEmailTemplate);
        study.setResetPasswordTemplate(resetPasswordTemplate);
        study.setAccountExistsTemplate(accountExistsTemplate);
        
        service.setAccountDao(mockAccountDao);
        service.setCacheProvider(mockCacheProvider);
        service.setSendMailService(mockSendMailService);
        service.setStudyService(mockStudyService);
        service.setNotificationsService(mockNotificationsService);
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
    public void sendEmailVerificationTokenNoEmail() throws Exception {
        service.sendEmailVerificationToken(study, USER_ID, null);
        verify(mockSendMailService, never()).sendEmail(any());
    }
    
    @Test
    public void resendEmailVerificationToken() {
        when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenReturn(mockAccount);
        when(mockAccount.getId()).thenReturn(USER_ID);
        when(mockAccount.getEmail()).thenReturn(EMAIL);
        
        service.resendEmailVerificationToken(ACCOUNT_ID_WITH_EMAIL);
        
        verify(service).sendEmailVerificationToken(study, USER_ID, EMAIL);
    }
    
    @Test
    public void resendEmailVerificationTokenFailsWithMissingStudy() {
        when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenThrow(new EntityNotFoundException(Study.class));
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenReturn(mockAccount);
        when(mockAccount.getId()).thenReturn(USER_ID);
        
        try {
            service.resendEmailVerificationToken(ACCOUNT_ID_WITH_EMAIL);
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
        }
        verify(service, never()).sendEmailVerificationToken(study, USER_ID, EMAIL);
    }
    
    @Test
    public void resendEmailVerificationTokenFailsQuietlyWithMissingAccount() {
        when(mockStudyService.getStudy(TEST_STUDY)).thenReturn(study);
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenReturn(null);
        when(mockAccount.getId()).thenReturn(USER_ID);
        
        service.resendEmailVerificationToken(ACCOUNT_ID_WITH_EMAIL);
        
        verify(service, never()).sendEmailVerificationToken(study, USER_ID, EMAIL);
    }
    
    @Test
    public void verifyEmail() {
        when(mockCacheProvider.getString(SPTOKEN)).thenReturn(
            TestUtils.createJson("{'studyId':'api','userId':'userId'}"));
        when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_ID)).thenReturn(mockAccount);
        when(mockAccount.getId()).thenReturn("accountId");
        
        EmailVerification verification = new EmailVerification(SPTOKEN);
        
        Account account = service.verifyEmail(verification);
        assertEquals("accountId",account.getId());
    }
    
    @Test(expected = BadRequestException.class)
    public void verifyEmailBadSptokenThrowsException() {
        when(mockCacheProvider.getString(SPTOKEN)).thenReturn(null);
        
        EmailVerification verification = new EmailVerification(SPTOKEN);
        
        service.verifyEmail(verification);
    }
    
    @Test
    public void notifyAccountExistsForEmail() throws Exception {
        AccountId accountId = AccountId.forId(TEST_STUDY_IDENTIFIER, USER_ID);
        when(service.createTimeLimitedToken()).thenReturn("ABC");
        when(mockAccount.getEmail()).thenReturn(EMAIL);
        when(mockAccount.getEmailVerified()).thenReturn(Boolean.TRUE);
        when(mockAccountDao.getAccount(accountId)).thenReturn(mockAccount);
        
        service.notifyAccountExists(study, accountId);
        
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
    public void notifyAccountExistsForPhone() throws Exception {
        AccountId accountId = AccountId.forPhone(TEST_STUDY_IDENTIFIER, TestConstants.PHONE);
        when(service.createTimeLimitedToken()).thenReturn("ABC");
        when(mockAccount.getPhone()).thenReturn(TestConstants.PHONE);
        when(mockAccount.getPhoneVerified()).thenReturn(Boolean.TRUE);
        when(mockAccountDao.getAccount(accountId)).thenReturn(mockAccount);
        
        service.notifyAccountExists(study, accountId);
        
        verify(mockCacheProvider).setString("ABC:phone:api", 
                BridgeObjectMapper.get().writeValueAsString(TestConstants.PHONE), 
                AccountWorkflowService.EXPIRE_IN_SECONDS);
        verify(mockNotificationsService).sendSMSMessage(eq(study.getStudyIdentifier()), 
                eq(TestConstants.PHONE), stringCaptor.capture());
        String message = stringCaptor.getValue();
        assertTrue(message.contains("Account for ShortName already exists. Reset password: "));
        assertTrue(message.contains("/mobile/resetPassword.html?study=api&sptoken=ABC"));
    }
    
    @Test
    public void requestResetPasswordWithEmail() throws Exception {
        when(service.createTimeLimitedToken()).thenReturn("ABC");
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenReturn(mockAccount);
        when(mockStudyService.getStudy(TEST_STUDY)).thenReturn(study);
        when(mockAccount.getEmail()).thenReturn(EMAIL);
        when(mockAccount.getEmailVerified()).thenReturn(Boolean.TRUE);        
        when(mockAccount.getStudyIdentifier()).thenReturn(TEST_STUDY);
        
        service.requestResetPassword(study, ACCOUNT_ID_WITH_EMAIL);
        
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
    public void requestResetPasswordWithPhone() throws Exception {
        when(service.createTimeLimitedToken()).thenReturn("ABC");
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_PHONE)).thenReturn(mockAccount);
        when(mockStudyService.getStudy(TEST_STUDY)).thenReturn(study);
        when(mockAccount.getPhone()).thenReturn(TestConstants.PHONE);
        when(mockAccount.getPhoneVerified()).thenReturn(Boolean.TRUE);        
        when(mockAccount.getStudyIdentifier()).thenReturn(TEST_STUDY);
        
        service.requestResetPassword(study, ACCOUNT_ID_WITH_PHONE);
        
        verify(mockCacheProvider).setString(eq("ABC:phone:api"), stringCaptor.capture(), eq(60*60*2));
        verify(mockNotificationsService).sendSMSMessage(eq(TEST_STUDY), eq(TestConstants.PHONE), secondStringCaptor.capture());
        
        Phone captured = BridgeObjectMapper.get().readValue(stringCaptor.getValue(), Phone.class);
        assertEquals(TestConstants.PHONE, captured); 
        
        String message = secondStringCaptor.getValue();
        assertTrue(message.contains("Reset ShortName password: "));
        assertTrue(message.contains("/mobile/resetPassword.html?study=api&sptoken=ABC"));
    }

    @Test
    public void requestResetPasswordFailsQuietlyIfEmailPhoneUnverifiedUsingEmail() {
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_PHONE)).thenReturn(mockAccount);
        when(mockAccount.getEmail()).thenReturn(EMAIL);
        when(mockAccount.getPhoneVerified()).thenReturn(Boolean.FALSE);
        when(mockAccount.getEmailVerified()).thenReturn(Boolean.FALSE);
        when(mockAccount.getStudyIdentifier()).thenReturn(TEST_STUDY);

        service.requestResetPassword(study, ACCOUNT_ID_WITH_PHONE);
        
        verify(mockCacheProvider, never()).setString("ABC:api", TestConstants.PHONE.getNumber(), 60*60*2);
        verify(mockNotificationsService, never()).sendSMSMessage(eq(TEST_STUDY), eq(TestConstants.PHONE), any());
    }

    @Test
    public void requestResetPasswordFailsQuietlyIfEmailPhoneUnverifiedUsingPhone() {
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_PHONE)).thenReturn(mockAccount);
        when(mockAccount.getPhone()).thenReturn(TestConstants.PHONE);
        when(mockAccount.getPhoneVerified()).thenReturn(Boolean.FALSE);
        when(mockAccount.getEmailVerified()).thenReturn(Boolean.FALSE);
        when(mockAccount.getStudyIdentifier()).thenReturn(TEST_STUDY);
        
        service.requestResetPassword(study, ACCOUNT_ID_WITH_PHONE);
        
        verify(mockCacheProvider, never()).setString("ABC:api", TestConstants.PHONE.getNumber(), 60*60*2);
        verify(mockNotificationsService, never()).sendSMSMessage(eq(TEST_STUDY), eq(TestConstants.PHONE), any());
    }
    
    @Test
    public void requestResetPasswordInvalidEmailFailsQuietly() throws Exception {
        when(service.createTimeLimitedToken()).thenReturn("ABC");
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenReturn(null);
        
        service.requestResetPassword(study, ACCOUNT_ID_WITH_EMAIL);
        
        verify(mockCacheProvider, never()).setString("ABC:api", EMAIL, 60*5);
        verify(mockSendMailService, never()).sendEmail(emailProviderCaptor.capture());
    }

    @Test
    public void requestRestPasswordUnverifiedEmailFailsQuietly() throws Exception {
        when(service.createTimeLimitedToken()).thenReturn("ABC");
        when(mockAccount.getEmail()).thenReturn(EMAIL);
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenReturn(mockAccount);
        
        service.requestResetPassword(study, ACCOUNT_ID_WITH_EMAIL);
        
        verifyNoMoreInteractions(mockSendMailService);
        verifyNoMoreInteractions(mockNotificationsService);
    }
    
    @Test
    public void requestRestPasswordUnverifiedPhoneFailsQuietly() throws Exception {
        when(service.createTimeLimitedToken()).thenReturn("ABC");
        when(mockAccount.getPhone()).thenReturn(TestConstants.PHONE);
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_PHONE)).thenReturn(mockAccount);
        
        service.requestResetPassword(study, ACCOUNT_ID_WITH_PHONE);
        
        verifyNoMoreInteractions(mockSendMailService);
        verifyNoMoreInteractions(mockNotificationsService);
    }
    
    @Test
    public void resetPasswordWithEmail() {
        when(mockCacheProvider.getString("sptoken:api")).thenReturn(EMAIL);
        when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenReturn(mockAccount);

        PasswordReset passwordReset = new PasswordReset("newPassword", "sptoken", TEST_STUDY_IDENTIFIER);
        
        service.resetPassword(passwordReset);
        
        verify(mockCacheProvider).getString("sptoken:api");
        verify(mockCacheProvider).removeString("sptoken:api");
        verify(mockAccountDao).changePassword(mockAccount, "newPassword");
    }
    
    @Test
    public void resetPasswordWithPhone() throws Exception {
        String phoneJson = BridgeObjectMapper.get().writeValueAsString(TestConstants.PHONE);
        when(mockCacheProvider.getString("sptoken:phone:api")).thenReturn(phoneJson);
        when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_PHONE)).thenReturn(mockAccount);

        PasswordReset passwordReset = new PasswordReset("newPassword", "sptoken", TEST_STUDY_IDENTIFIER);
        
        service.resetPassword(passwordReset);
        
        verify(mockCacheProvider).getString("sptoken:phone:api");
        verify(mockCacheProvider).removeString("sptoken:phone:api");
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
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenReturn(null);

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
