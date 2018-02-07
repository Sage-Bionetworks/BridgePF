package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
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
import org.sagebionetworks.bridge.exceptions.AuthenticationFailedException;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.EmailVerification;
import org.sagebionetworks.bridge.models.accounts.PasswordReset;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.MimeType;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.AuthenticationService.ChannelType;
import org.sagebionetworks.bridge.services.email.BasicEmailProvider;
import org.sagebionetworks.bridge.services.email.MimeTypeEmail;
import org.sagebionetworks.bridge.services.email.MimeTypeEmailProvider;
import org.sagebionetworks.bridge.validators.SignInValidator;

import com.google.common.collect.Iterables;

@RunWith(MockitoJUnitRunner.class)
public class AccountWorkflowServiceTest {
    
    private static final String SUPPORT_EMAIL = "support@support.com";
    private static final String STUDY_ID = TestConstants.TEST_STUDY_IDENTIFIER;
    private static final String SPTOKEN = "sptoken";
    private static final String USER_ID = "userId";
    private static final String EMAIL = "email@email.com";
    private static final String TOKEN = "ABC-DEF";
    private static final String CACHE_KEY = "email@email.com:"+STUDY_ID+":signInRequest";
    private static final String EMAIL_CACHE_KEY = "email@email.com:api:signInRequest";
    private static final String PHONE_CACHE_KEY = TestConstants.PHONE.getNumber() + ":api:phoneSignInRequest";
    
    private static final AccountId ACCOUNT_ID_WITH_ID = AccountId.forId(TEST_STUDY_IDENTIFIER, USER_ID);
    private static final AccountId ACCOUNT_ID_WITH_EMAIL = AccountId.forEmail(TEST_STUDY_IDENTIFIER, EMAIL);
    private static final AccountId ACCOUNT_ID_WITH_PHONE = AccountId.forPhone(TEST_STUDY_IDENTIFIER, TestConstants.PHONE);
    private static final SignIn SIGN_IN_REQUEST_WITH_PHONE = new SignIn.Builder().withStudy(STUDY_ID)
            .withPhone(TestConstants.PHONE).build();
    private static final SignIn SIGN_IN_REQUEST_WITH_EMAIL = new SignIn.Builder().withStudy(STUDY_ID)
            .withEmail(EMAIL).build();
    private static final SignIn SIGN_IN_WITH_PHONE = new SignIn.Builder().withStudy(STUDY_ID)
            .withPhone(TestConstants.PHONE).withToken(TOKEN).build();
    private static final SignIn SIGN_IN_WITH_EMAIL = new SignIn.Builder().withEmail(EMAIL).withStudy(STUDY_ID)
            .withToken(TOKEN).build();
    private static final CriteriaContext CONTEXT = new CriteriaContext.Builder()
            .withStudyIdentifier(TestConstants.TEST_STUDY).build();

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
        EmailTemplate emailSignInTemplate = new EmailTemplate("subject","${token}",MimeType.TEXT);
        
        study = Study.create();
        study.setIdentifier(TEST_STUDY_IDENTIFIER);
        study.setName("This study name");
        study.setShortName("ShortName");
        study.setSupportEmail(SUPPORT_EMAIL);
        study.setVerifyEmailTemplate(verifyEmailTemplate);
        study.setResetPasswordTemplate(resetPasswordTemplate);
        study.setAccountExistsTemplate(accountExistsTemplate);
        study.setEmailSignInTemplate(emailSignInTemplate);

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
        when(mockCacheProvider.getObject(SPTOKEN, String.class)).thenReturn(
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
        when(mockCacheProvider.getObject(SPTOKEN, String.class)).thenReturn(null);
        
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
        
        verify(mockCacheProvider).setObject("ABC:api", EMAIL, 60*60*2);
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
        
        verify(mockCacheProvider).setObject("ABC:phone:api", 
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
        
        verify(mockCacheProvider).setObject("ABC:api", EMAIL, 60*60*2);
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
        
        verify(mockCacheProvider).setObject(eq("ABC:phone:api"), stringCaptor.capture(), eq(60*60*2));
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
        
        verify(mockCacheProvider, never()).setObject("ABC:api", TestConstants.PHONE.getNumber(), 60*60*2);
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
        
        verify(mockCacheProvider, never()).setObject("ABC:api", TestConstants.PHONE.getNumber(), 60*60*2);
        verify(mockNotificationsService, never()).sendSMSMessage(eq(TEST_STUDY), eq(TestConstants.PHONE), any());
    }
    
    @Test
    public void requestResetPasswordInvalidEmailFailsQuietly() throws Exception {
        when(service.createTimeLimitedToken()).thenReturn("ABC");
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenReturn(null);
        
        service.requestResetPassword(study, ACCOUNT_ID_WITH_EMAIL);
        
        verify(mockCacheProvider, never()).setObject("ABC:api", EMAIL, 60*5);
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
        when(mockCacheProvider.getObject("sptoken:api", String.class)).thenReturn(EMAIL);
        when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenReturn(mockAccount);

        PasswordReset passwordReset = new PasswordReset("newPassword", "sptoken", TEST_STUDY_IDENTIFIER);
        
        service.resetPassword(passwordReset);
        
        verify(mockCacheProvider).getObject("sptoken:api", String.class);
        verify(mockCacheProvider).removeObject("sptoken:api");
        verify(mockAccountDao).changePassword(mockAccount, "newPassword");
    }
    
    @Test
    public void resetPasswordWithPhone() throws Exception {
        String phoneJson = BridgeObjectMapper.get().writeValueAsString(TestConstants.PHONE);
        when(mockCacheProvider.getObject("sptoken:phone:api", String.class)).thenReturn(phoneJson);
        when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_PHONE)).thenReturn(mockAccount);

        PasswordReset passwordReset = new PasswordReset("newPassword", "sptoken", TEST_STUDY_IDENTIFIER);
        
        service.resetPassword(passwordReset);
        
        verify(mockCacheProvider).getObject("sptoken:phone:api", String.class);
        verify(mockCacheProvider).removeObject("sptoken:phone:api");
        verify(mockAccountDao).changePassword(mockAccount, "newPassword");
    }
    
    @Test
    public void resetPasswordInvalidSptokenThrowsException() {
        when(mockCacheProvider.getObject("sptoken:api", String.class)).thenReturn(null);

        PasswordReset passwordReset = new PasswordReset("newPassword", "sptoken", TEST_STUDY_IDENTIFIER);
        try {
            service.resetPassword(passwordReset);
            fail("Should have thrown exception");
        } catch(BadRequestException e) {
            assertEquals("Password reset token has expired (or already been used).", e.getMessage());
        }
        verify(mockCacheProvider).getObject("sptoken:api", String.class);
        verify(mockCacheProvider, never()).removeObject("sptoken:api");
        verify(mockAccountDao, never()).changePassword(mockAccount, "newPassword");
    }
    
    @Test
    public void resetPasswordInvalidAccount() {
        when(mockCacheProvider.getObject("sptoken:api", String.class)).thenReturn(EMAIL);
        when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenReturn(null);

        PasswordReset passwordReset = new PasswordReset("newPassword", "sptoken", TEST_STUDY_IDENTIFIER);
        
        try {
            service.resetPassword(passwordReset);
            fail("Should have thrown an exception");
        } catch(EntityNotFoundException e) {
        }
        verify(mockCacheProvider).getObject("sptoken:api", String.class);
        verify(mockCacheProvider).removeObject("sptoken:api");
        verify(mockAccountDao, never()).changePassword(mockAccount, "newPassword");
    }
    
    @Test
    public void requestEmailSignIn() throws Exception {
        study.setEmailSignInEnabled(true);
        doReturn(mockAccount).when(mockAccountDao).getAccount(SIGN_IN_REQUEST_WITH_EMAIL.getAccountId());
        doReturn(study).when(mockStudyService).getStudy(study.getIdentifier());
        
        service.requestEmailSignIn(SIGN_IN_REQUEST_WITH_EMAIL);
        
        verify(mockCacheProvider).getObject(stringCaptor.capture(), eq(String.class));
        assertEquals(CACHE_KEY, stringCaptor.getValue());
        
        verify(mockAccountDao).getAccount(SIGN_IN_REQUEST_WITH_EMAIL.getAccountId());
        
        verify(mockCacheProvider).setObject(eq(CACHE_KEY), stringCaptor.capture(), eq(300));
        assertNotNull(stringCaptor.getValue());

        verify(mockSendMailService).sendEmail(emailProviderCaptor.capture());
        
        BasicEmailProvider provider = emailProviderCaptor.getValue();
        assertEquals(21, provider.getTokenMap().get("token").length());
        assertEquals(study, provider.getStudy());
        assertEquals(EMAIL, Iterables.getFirst(provider.getRecipientEmails(), null));
    }
    
    @Test
    public void requestEmailSignInFailureDelays() throws Exception {
        study.setEmailSignInEnabled(true);
        service.getEmailSignInRequestInMillis().set(1000);
        doReturn(null).when(mockAccountDao).getAccount(any());
        doReturn(study).when(mockStudyService).getStudy(study.getIdentifier());
                 
        long start = System.currentTimeMillis();
        service.requestEmailSignIn(SIGN_IN_REQUEST_WITH_EMAIL);
        long total = System.currentTimeMillis()-start;
        assertTrue(total >= 1000);
        service.getEmailSignInRequestInMillis().set(0);
    }    
    
    @Test(expected = InvalidEntityException.class)
    public void emailSignInRequestMissingStudy() {
        SignIn signInRequest = new SignIn.Builder().withEmail(EMAIL).withToken(TOKEN).build();

        service.requestEmailSignIn(signInRequest);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void emailSignInRequestMissingEmail() {
        SignIn signInRequest = new SignIn.Builder().withStudy(STUDY_ID).withToken(TOKEN).build();
        
        service.requestEmailSignIn(signInRequest);
    }
    
    @Test(expected = UnauthorizedException.class)
    public void requestEmailSignInDisabled() {
        study.setEmailSignInEnabled(false);
        doReturn(study).when(mockStudyService).getStudy(study.getIdentifier());
        
        service.requestEmailSignIn(SIGN_IN_REQUEST_WITH_EMAIL);
    }
    
    @Test
    public void requestEmailSignInTwiceReturnsSameToken() throws Exception {
        // In this case, where there is a value and an account, we do't generate a new one,
        // we just send the message again.
        study.setEmailSignInEnabled(true);
        doReturn("something").when(mockCacheProvider).getObject(CACHE_KEY, String.class);
        doReturn(mockAccount).when(mockAccountDao).getAccount(any());
        doReturn(study).when(mockStudyService).getStudy(study.getIdentifier());
        
        service.requestEmailSignIn(SIGN_IN_REQUEST_WITH_EMAIL);
        
        verify(mockCacheProvider, never()).setObject(any(), any(), anyInt());
        verify(mockSendMailService).sendEmail(emailProviderCaptor.capture());
        
        MimeTypeEmailProvider provider = emailProviderCaptor.getValue();
        assertEquals(EMAIL, provider.getMimeTypeEmail().getRecipientAddresses().get(0));
        assertEquals(SUPPORT_EMAIL, provider.getPlainSenderEmail());
        String bodyString = (String)provider.getMimeTypeEmail().getMessageParts().get(0).getContent();
        assertEquals("something", bodyString);
    }
    
    @Test
    public void requestEmailSignInEmailNotRegistered() {
        study.setEmailSignInEnabled(true);
        doReturn(null).when(mockAccountDao).getAccount(ACCOUNT_ID_WITH_ID);
        doReturn(study).when(mockStudyService).getStudy(study.getIdentifier());
        
        service.requestEmailSignIn(SIGN_IN_REQUEST_WITH_EMAIL);

        verify(mockCacheProvider, never()).setObject(eq(CACHE_KEY), any(), eq(60));
        verify(mockSendMailService, never()).sendEmail(any());
    }
    
    @Test
    public void requestPhoneSignIn() { 
        study.setShortName("AppName");
        String cacheKey = TestConstants.PHONE.getNumber() + ":api:phoneSignInRequest";
        when(mockAccountDao.getAccount(SIGN_IN_WITH_PHONE.getAccountId())).thenReturn(mockAccount);
        when(service.getPhoneToken()).thenReturn("123456");
        doReturn(study).when(mockStudyService).getStudy(study.getIdentifier());
        
        service.requestPhoneSignIn(SIGN_IN_REQUEST_WITH_PHONE);
        
        verify(mockCacheProvider).getObject(cacheKey, String.class);
        verify(mockCacheProvider).setObject(cacheKey, "123456", 300);
        verify(mockNotificationsService).sendSMSMessage(study.getStudyIdentifier(), TestConstants.PHONE,
                "Enter 123-456 to sign in to AppName");
    }
    
    @Test
    public void requestPhoneSignInFails() {
        // This should fail silently, or we risk giving away information about accounts in the system.
        service.requestPhoneSignIn(SIGN_IN_REQUEST_WITH_PHONE);
        
        verify(mockCacheProvider, never()).setObject(any(), any(), anyInt());
        verify(mockNotificationsService, never()).sendSMSMessage(any(), any(), any());
    }
    
    @Test
    public void emailChannelSignIn() {
        when(mockCacheProvider.getObject(EMAIL_CACHE_KEY, String.class)).thenReturn(TOKEN);
        
        AccountId returnedAccount = service.channelSignIn(ChannelType.EMAIL, CONTEXT, SIGN_IN_WITH_EMAIL, SignInValidator.EMAIL_SIGNIN);
        
        verify(mockCacheProvider).getObject(EMAIL_CACHE_KEY, String.class);
        verify(mockCacheProvider).removeObject(EMAIL_CACHE_KEY);
        assertEquals(SIGN_IN_WITH_EMAIL.getAccountId(), returnedAccount); 
    }
    
    @Test
    public void phoneChannelSignIn() {
        when(mockCacheProvider.getObject(PHONE_CACHE_KEY, String.class)).thenReturn(TOKEN);
        
        AccountId returnedAccount = service.channelSignIn(ChannelType.PHONE, CONTEXT, SIGN_IN_WITH_PHONE, SignInValidator.PHONE_SIGNIN);
        
        verify(mockCacheProvider).getObject(PHONE_CACHE_KEY, String.class);
        verify(mockCacheProvider).removeObject(PHONE_CACHE_KEY);
        assertEquals(SIGN_IN_WITH_PHONE.getAccountId(), returnedAccount); 
    }
    
    @Test(expected = InvalidEntityException.class)
    public void channelSignInValidates() {
        // study is missing here.
        service.channelSignIn(ChannelType.PHONE, CONTEXT, new SignIn.Builder().withPhone(TestConstants.PHONE).build(),
                SignInValidator.PHONE_SIGNIN);
    }
    
    @Test(expected = AuthenticationFailedException.class)
    public void channelSignInMissingTokenThrowsException() {
        // This should work, except that the token will not be returned from the cache
        service.channelSignIn(ChannelType.PHONE, CONTEXT, SIGN_IN_WITH_PHONE, SignInValidator.PHONE_SIGNIN);
    }
    
    @Test(expected = AuthenticationFailedException.class)
    public void channelSignInWrongTokenThrowsException() {
        when(mockCacheProvider.getObject(PHONE_CACHE_KEY, String.class)).thenReturn(TOKEN);
        
        SignIn wrongTokenSignIn = new SignIn.Builder().withStudy(STUDY_ID)
                .withPhone(TestConstants.PHONE).withToken("wrong-token").build();

        // This should work, except that the tokens do not match
        service.channelSignIn(ChannelType.PHONE, CONTEXT, wrongTokenSignIn, SignInValidator.PHONE_SIGNIN);
    }
    
    @Test(expected = AuthenticationFailedException.class)
    public void channelSignInWrongEmailThrowsException() {
        when(mockCacheProvider.getObject(EMAIL_CACHE_KEY, String.class)).thenReturn(TOKEN);

        SignIn wrongEmailSignIn = new SignIn.Builder().withStudy(STUDY_ID)
                .withEmail("wrong-email@email.com").withToken(TOKEN).build();

        service.channelSignIn(ChannelType.EMAIL, CONTEXT, wrongEmailSignIn, SignInValidator.EMAIL_SIGNIN);
    }

    @Test(expected = AuthenticationFailedException.class)
    public void channelSignInWrongPhoneThrowsException() {
        when(mockCacheProvider.getObject(PHONE_CACHE_KEY, String.class)).thenReturn(TOKEN);

        SignIn wrongPhoneSignIn = new SignIn.Builder().withStudy(STUDY_ID)
                .withPhone(new Phone("4082588569", "US")).withToken(TOKEN).build();

        service.channelSignIn(ChannelType.PHONE, CONTEXT, wrongPhoneSignIn, SignInValidator.PHONE_SIGNIN);
    }
    
}
