package org.sagebionetworks.bridge.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;

import java.util.List;
import java.util.Map;

import javax.mail.internet.MimeBodyPart;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.config.BridgeConfig;
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
import org.sagebionetworks.bridge.models.accounts.AccountStatus;
import org.sagebionetworks.bridge.models.accounts.PasswordReset;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.Verification;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.MimeType;
import org.sagebionetworks.bridge.models.studies.SmsTemplate;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.redis.InMemoryJedisOps;
import org.sagebionetworks.bridge.services.AuthenticationService.ChannelType;
import org.sagebionetworks.bridge.services.email.BasicEmailProvider;
import org.sagebionetworks.bridge.services.email.EmailType;
import org.sagebionetworks.bridge.services.email.MimeTypeEmail;
import org.sagebionetworks.bridge.sms.SmsMessageProvider;
import org.sagebionetworks.bridge.validators.SignInValidator;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Iterables;

@RunWith(MockitoJUnitRunner.class)
public class AccountWorkflowServiceTest {
    private static final String SUPPORT_EMAIL = "support@support.com";
    private static final String STUDY_ID = TestConstants.TEST_STUDY_IDENTIFIER;
    private static final String SPTOKEN = "GHI-JKL";
    private static final String USER_ID = "userId";
    private static final String EMAIL = "email@email.com";
    private static final String TOKEN = "ABCDEF";
    private static final String PHONE_TOKEN = "012345";
    
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
    
    private static final CacheKey PHONE_CACHE_KEY = CacheKey.phoneSignInRequest(SIGN_IN_REQUEST_WITH_PHONE);
    
    private static final CacheKey PHONE_TOKEN_CACHE_KEY = CacheKey.verificationToken(PHONE_TOKEN);
    private static final CacheKey TOKEN_CACHE_KEY = CacheKey.verificationToken(TOKEN);
    private static final CacheKey SPTOKEN_CACHE_KEY = CacheKey.verificationToken(SPTOKEN);
    
    private static final CacheKey EMAIL_SIGNIN_CACHE_KEY = CacheKey.emailSignInRequest(SIGN_IN_WITH_EMAIL);
    private static final CacheKey PHONE_SIGNIN_CACHE_KEY = CacheKey.phoneSignInRequest(SIGN_IN_WITH_PHONE);
    private static final CacheKey PASSWORD_RESET_FOR_EMAIL = CacheKey.passwordResetForEmail(SPTOKEN, STUDY_ID);
    private static final CacheKey PASSWORD_RESET_FOR_PHONE = CacheKey.passwordResetForPhone(SPTOKEN, STUDY_ID);

    @Mock
    private BridgeConfig mockBridgeConfig;

    @Mock
    private SmsService mockSmsService;

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
    private ArgumentCaptor<CacheKey> keyCaptor;
    
    @Captor
    private ArgumentCaptor<String> stringCaptor;

    @Captor
    private ArgumentCaptor<SmsMessageProvider> smsMessageProviderCaptor;
    
    private Study study;
    
    @Spy
    private AccountWorkflowService service;
    
    @Before
    public void before() {
        EmailTemplate verifyEmailTemplate = new EmailTemplate("VE ${studyName}", "Body ${url} ${emailVerificationUrl}", MimeType.TEXT);
        EmailTemplate resetPasswordTemplate = new EmailTemplate("RP ${studyName}", "Body ${url} ${resetPasswordUrl}", MimeType.TEXT);
        EmailTemplate accountExistsTemplate = new EmailTemplate("AE ${studyName}",
                "Body ${url} ${resetPasswordUrl} ${emailSignInUrl}", MimeType.TEXT); 
        EmailTemplate emailSignInTemplate = new EmailTemplate("subject","Body ${token}", MimeType.TEXT);
        SmsTemplate phoneSignInSmsTemplate = new SmsTemplate("Enter ${token} to sign in to ${studyShortName}");
        SmsTemplate resetPasswordSmsTemplate = new SmsTemplate("Reset ${studyShortName} password: ${resetPasswordUrl}"); 
        SmsTemplate accountExistsSmsTemplate = new SmsTemplate("Account for ${studyShortName} already exists. Reset password: ${resetPasswordUrl} or ${token}");
        SmsTemplate verifyPhoneSmsTemplate = new SmsTemplate("Verify phone with ${token}");
        
        study = Study.create();
        study.setIdentifier(TEST_STUDY_IDENTIFIER);
        study.setName("This study name");
        study.setShortName("ShortName");
        study.setSupportEmail(SUPPORT_EMAIL);
        study.setVerifyEmailTemplate(verifyEmailTemplate);
        study.setResetPasswordTemplate(resetPasswordTemplate);
        study.setAccountExistsTemplate(accountExistsTemplate);
        study.setEmailSignInTemplate(emailSignInTemplate);
        study.setPhoneSignInSmsTemplate(phoneSignInSmsTemplate);
        study.setResetPasswordSmsTemplate(resetPasswordSmsTemplate);
        study.setAccountExistsSmsTemplate(accountExistsSmsTemplate);
        study.setVerifyPhoneSmsTemplate(verifyPhoneSmsTemplate);

        // Mock bridge config
        when(mockBridgeConfig.getInt(AccountWorkflowService.CONFIG_KEY_CHANNEL_THROTTLE_MAX_REQUESTS)).thenReturn(2);
        when(mockBridgeConfig.getInt(AccountWorkflowService.CONFIG_KEY_CHANNEL_THROTTLE_TIMEOUT_SECONDS)).thenReturn(
                300);

        // Set up service
        service.setAccountDao(mockAccountDao);
        service.setBridgeConfig(mockBridgeConfig);
        service.setCacheProvider(mockCacheProvider);
        service.setJedisOps(new InMemoryJedisOps());
        service.setSendMailService(mockSendMailService);
        service.setSmsService(mockSmsService);
        service.setStudyService(mockStudyService);

        // Add params to mock account.
        when(mockAccount.getId()).thenReturn(USER_ID);
        // */when(mockAccount.getHealthCode()).thenReturn(HEALTH_CODE);
    }
    
    @Test
    public void sendEmailVerificationToken() throws Exception {
        when(service.getNextToken()).thenReturn(SPTOKEN);
        
        service.sendEmailVerificationToken(study, USER_ID, EMAIL);
        
        verify(mockSendMailService).sendEmail(emailProviderCaptor.capture());
        verify(mockCacheProvider).setObject(eq(SPTOKEN_CACHE_KEY), stringCaptor.capture(),
                eq(AccountWorkflowService.VERIFY_OR_RESET_EXPIRE_IN_SECONDS));
        
        String string = stringCaptor.getValue();
        JsonNode node = BridgeObjectMapper.get().readTree(string);
        assertEquals("api", node.get("studyId").textValue());
        assertEquals("userId", node.get("userId").textValue());
        assertEquals("email", node.get("type").textValue());
        
        BasicEmailProvider provider = emailProviderCaptor.getValue();
        Map<String,String> tokens = provider.getTokenMap();
        assertEquals(SPTOKEN, tokens.get("sptoken"));
        assertEquals("2 hours", tokens.get("emailVerificationExpirationPeriod"));
        
        MimeTypeEmail email = provider.getMimeTypeEmail();
        assertEquals("\"This study name\" <support@support.com>", email.getSenderAddress());
        assertEquals(1, email.getRecipientAddresses().size());
        assertEquals(EMAIL, email.getRecipientAddresses().get(0));
        assertEquals("VE This study name", email.getSubject());
        MimeBodyPart body = email.getMessageParts().get(0);
        String bodyString = (String)body.getContent();
        assertTrue(bodyString.contains("/mobile/verifyEmail.html?study=api&sptoken="+SPTOKEN));
        assertTrue(bodyString.contains("/ve?study=api&sptoken="+SPTOKEN));
        assertEquals(EmailType.VERIFY_EMAIL, email.getType());
        verifyNoMoreInteractions(mockCacheProvider);
    }
    
    @Test
    public void sendEmailVerificationTokenNoEmail() {
        service.sendEmailVerificationToken(study, USER_ID, null);
        verify(mockSendMailService, never()).sendEmail(any());
        verifyNoMoreInteractions(mockCacheProvider);
    }

    @Test
    public void sendEmailVerificationTokenThrottled() {
        // Throttle limit is 2. Make 3 requests, and send only 2 emails.
        when(service.getNextToken()).thenReturn(TOKEN);
        service.sendEmailVerificationToken(study, USER_ID, EMAIL);
        service.sendEmailVerificationToken(study, USER_ID, EMAIL);
        service.sendEmailVerificationToken(study, USER_ID, EMAIL);
        verify(mockSendMailService, times(2)).sendEmail(any());
    }

    @Test
    public void sendPhoneVerificationToken() throws Exception {
        when(service.getNextPhoneToken()).thenReturn("012345");

        service.sendPhoneVerificationToken(study, USER_ID, TestConstants.PHONE);

        verify(mockSmsService).sendSmsMessage(eq(USER_ID), smsMessageProviderCaptor.capture());
        verify(mockCacheProvider).setObject(eq(PHONE_TOKEN_CACHE_KEY), stringCaptor.capture(), eq(AccountWorkflowService.VERIFY_OR_RESET_EXPIRE_IN_SECONDS));
        
        String string = stringCaptor.getValue();
        JsonNode node = BridgeObjectMapper.get().readTree(string);
        assertEquals("api", node.get("studyId").textValue());
        assertEquals("userId", node.get("userId").textValue());
        assertEquals("phone", node.get("type").textValue());
        
        SmsMessageProvider provider = smsMessageProviderCaptor.getValue();
        Map<String,String> tokens = provider.getTokenMap();
        assertEquals("012-345", tokens.get("token"));
        assertEquals("2 hours", tokens.get("phoneVerificationExpirationPeriod"));
        assertEquals("Transactional", provider.getSmsType());
        
        String message = provider.getSmsRequest().getMessage();
        assertTrue(message.contains("012-345"));
        verifyNoMoreInteractions(mockCacheProvider);
    }
    
    @Test
    public void sendPhoneVerificationTokenNoPhone() {
        service.sendPhoneVerificationToken(study, USER_ID, null);
        verify(mockSmsService, never()).sendSmsMessage(any(), any());
        verify(mockCacheProvider, never()).setObject(any(), any(), anyInt());
        verifyNoMoreInteractions(mockCacheProvider);
    }

    @Test
    public void sendPhoneVerificationTokenThrottled() {
        // Throttle limit is 2. Make 3 requests, and send only 2 emails.
        service.sendPhoneVerificationToken(study, USER_ID, TestConstants.PHONE);
        service.sendPhoneVerificationToken(study, USER_ID, TestConstants.PHONE);
        service.sendPhoneVerificationToken(study, USER_ID, TestConstants.PHONE);
        verify(mockSmsService, times(2)).sendSmsMessage(eq(USER_ID), smsMessageProviderCaptor.capture());
    }
    
    @Test
    public void resendEmailVerificationToken() {
        when(service.getNextToken()).thenReturn(TOKEN);
        when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenReturn(mockAccount);
        when(mockAccount.getId()).thenReturn(USER_ID);
        when(mockAccount.getEmail()).thenReturn(EMAIL);
        
        service.resendVerificationToken(ChannelType.EMAIL, ACCOUNT_ID_WITH_EMAIL);
        
        verify(service).sendEmailVerificationToken(study, USER_ID, EMAIL);
        verify(mockCacheProvider).setObject(eq(TOKEN_CACHE_KEY), any(),
                eq(AccountWorkflowService.VERIFY_OR_RESET_EXPIRE_IN_SECONDS));
    }
    
    @Test(expected = UnsupportedOperationException.class)
    public void resendEmailVerificationTokenUnsupportedType() {
        when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenReturn(mockAccount);
        
        // Use null so we don't have to create an unsupported channel type
        service.resendVerificationToken(null, ACCOUNT_ID_WITH_EMAIL);
    }
    
    @Test
    public void resendEmailVerificationTokenFailsWithMissingStudy() {
        when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenThrow(new EntityNotFoundException(Study.class));
     // */when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenReturn(mockAccount);
     // */when(mockAccount.getId()).thenReturn(USER_ID);
        
        try {
            service.resendVerificationToken(ChannelType.EMAIL, ACCOUNT_ID_WITH_EMAIL);
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
            // expected exception
        }
        verify(service, never()).sendEmailVerificationToken(any(), any(), any());
        verifyNoMoreInteractions(mockCacheProvider);
    }
    
    @Test
    public void resendEmailVerificationTokenFailsQuietlyWithMissingAccount() {
     // */when(mockStudyService.getStudy(TEST_STUDY)).thenReturn(study);
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenReturn(null);
     // */when(mockAccount.getId()).thenReturn(USER_ID);
        
        service.resendVerificationToken(ChannelType.EMAIL, ACCOUNT_ID_WITH_EMAIL);
        
        verify(service, never()).sendEmailVerificationToken(any(), any(), any());
        verifyNoMoreInteractions(mockCacheProvider);
    }
    
    @Test
    public void resendPhoneVerificationToken() {
        when(service.getNextPhoneToken()).thenReturn(PHONE_TOKEN);
        when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_PHONE)).thenReturn(mockAccount);
        when(mockAccount.getId()).thenReturn(USER_ID);
        when(mockAccount.getPhone()).thenReturn(TestConstants.PHONE);
        
        service.resendVerificationToken(ChannelType.PHONE, ACCOUNT_ID_WITH_PHONE);
        
        verify(service).sendPhoneVerificationToken(study, USER_ID, TestConstants.PHONE);
        
        verify(mockCacheProvider).setObject(eq(PHONE_TOKEN_CACHE_KEY), stringCaptor.capture(),
                eq(AccountWorkflowService.VERIFY_OR_RESET_EXPIRE_IN_SECONDS));
    }
    
    @Test
    public void resendPhoneVerificationTokenFailsWithMissingStudy() {
        when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenThrow(new EntityNotFoundException(Study.class));
     // */when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_PHONE)).thenReturn(mockAccount);
     // */when(mockAccount.getId()).thenReturn(USER_ID);
        
        try {
            service.resendVerificationToken(ChannelType.EMAIL, ACCOUNT_ID_WITH_PHONE);
            fail("Should have thrown exception");
        } catch(EntityNotFoundException e) {
            // expected exception
        }
        verify(service, never()).sendPhoneVerificationToken(any(), any(), any());
        verifyNoMoreInteractions(mockCacheProvider);
    }
    
    @Test
    public void resendPhoneVerificationTokenFailsQuietlyWithMissingAccount() {
     // */when(mockStudyService.getStudy(TEST_STUDY)).thenReturn(study);
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_PHONE)).thenReturn(null);
     // */when(mockAccount.getId()).thenReturn(USER_ID);
        
        service.resendVerificationToken(ChannelType.EMAIL, ACCOUNT_ID_WITH_PHONE);
        
        verify(service, never()).sendPhoneVerificationToken(any(), any(), any());
        verifyNoMoreInteractions(mockCacheProvider);
    }
    
    @Test
    public void verifyEmailWithLegacyJson() {
        when(mockCacheProvider.getObject(SPTOKEN_CACHE_KEY, String.class)).thenReturn(
            TestUtils.createJson("{'studyId':'api','userId':'userId'}"));
        when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_ID)).thenReturn(mockAccount);
        when(mockAccount.getId()).thenReturn("accountId");
        
        Verification verification = new Verification(SPTOKEN);
        
        Account account = service.verifyChannel(ChannelType.EMAIL, verification);
        assertEquals("accountId",account.getId());
        verify(mockCacheProvider).getObject(SPTOKEN_CACHE_KEY, String.class);
        verify(mockCacheProvider).removeObject(SPTOKEN_CACHE_KEY);
    }
    
    @Test
    public void verifyEmail() {
        when(mockCacheProvider.getObject(SPTOKEN_CACHE_KEY, String.class)).thenReturn(
            TestUtils.createJson("{'studyId':'api','type':'email','userId':'userId'}"));
        when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_ID)).thenReturn(mockAccount);
        when(mockAccount.getId()).thenReturn("accountId");
        
        Verification verification = new Verification(SPTOKEN);
        
        Account account = service.verifyChannel(ChannelType.EMAIL, verification);
        assertEquals("accountId",account.getId());
        verify(mockCacheProvider).getObject(SPTOKEN_CACHE_KEY, String.class);
        verify(mockCacheProvider).removeObject(SPTOKEN_CACHE_KEY);
    }
    
    @Test(expected = BadRequestException.class)
    public void verifyEmailBadSptokenThrowsException() {
        when(mockCacheProvider.getObject(SPTOKEN_CACHE_KEY, String.class)).thenReturn(null);
        
        Verification verification = new Verification(SPTOKEN);
        
        service.verifyChannel(ChannelType.EMAIL, verification);
        verifyNoMoreInteractions(mockCacheProvider);
    }
    
    @Test
    public void verifyPhone() {
        when(mockCacheProvider.getObject(SPTOKEN_CACHE_KEY, String.class)).thenReturn(
                TestUtils.createJson("{'studyId':'api','type':'phone','userId':'userId'}"));
        when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_ID)).thenReturn(mockAccount);
        when(mockAccount.getId()).thenReturn("accountId");
        
        Verification verification = new Verification(SPTOKEN);
        
        Account account = service.verifyChannel(ChannelType.PHONE, verification);
        assertEquals("accountId",account.getId());
        verify(mockCacheProvider).getObject(SPTOKEN_CACHE_KEY, String.class);
        verify(mockCacheProvider).removeObject(SPTOKEN_CACHE_KEY);
    }
    
    @Test(expected = BadRequestException.class)
    public void verifyEmailViaPhoneFails() {
        when(mockCacheProvider.getObject(SPTOKEN_CACHE_KEY, String.class)).thenReturn(
                TestUtils.createJson("{'studyId':'api','type':'email','userId':'userId'}"));
     // */when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);
     // */when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_ID)).thenReturn(mockAccount);
     // */when(mockAccount.getId()).thenReturn("accountId");
        
        Verification verification = new Verification(SPTOKEN);
        
        service.verifyChannel(ChannelType.PHONE, verification);
        verifyNoMoreInteractions(mockCacheProvider);
    }
    
    @Test(expected = BadRequestException.class)
    public void verifyPhoneViaEmailFails() {
        when(mockCacheProvider.getObject(SPTOKEN_CACHE_KEY, String.class)).thenReturn(
                TestUtils.createJson("{'studyId':'api','type':'phone','userId':'userId'}"));
     // */when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);
     // */when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_ID)).thenReturn(mockAccount);
     // */when(mockAccount.getId()).thenReturn("accountId");
        
        Verification verification = new Verification(SPTOKEN);
        
        service.verifyChannel(ChannelType.EMAIL, verification);
        verifyNoMoreInteractions(mockCacheProvider);
    }
    
    @Test
    public void notifyAccountExistsForEmail() throws Exception {
        study.setEmailVerificationEnabled(true);
        // In this path email sign in is also enabled, so we will generate a link to sign in that can 
        // be used in lieu of directing the user to a password reset.
        study.setEmailSignInEnabled(true);
        AccountId accountId = AccountId.forId(TEST_STUDY_IDENTIFIER, USER_ID);
        when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);
        when(service.getNextToken()).thenReturn(SPTOKEN, TOKEN);
        when(mockAccount.getEmail()).thenReturn(EMAIL);
        when(mockAccount.getEmailVerified()).thenReturn(Boolean.TRUE);
        when(mockAccountDao.getAccount(accountId)).thenReturn(mockAccount);
        when(mockAccountDao.getAccount(AccountId.forEmail(TEST_STUDY_IDENTIFIER, EMAIL))).thenReturn(mockAccount);
        
        service.notifyAccountExists(study, accountId);
        
        verify(mockCacheProvider).setObject(PASSWORD_RESET_FOR_EMAIL, EMAIL, 60*60*2);
        verify(mockSendMailService).sendEmail(emailProviderCaptor.capture());
        
        BasicEmailProvider provider = emailProviderCaptor.getValue();
        
        assertEquals(TOKEN, provider.getTokenMap().get("token"));
        assertEquals(SPTOKEN, provider.getTokenMap().get("sptoken"));
        assertEquals("2 hours", provider.getTokenMap().get("expirationPeriod"));
        assertEquals(BridgeUtils.encodeURIComponent(EMAIL), provider.getTokenMap().get("email"));
        assertEquals("2", provider.getTokenMap().get("expirationWindow"));
        
        MimeTypeEmail email = provider.getMimeTypeEmail();
        assertEquals("\"This study name\" <support@support.com>", email.getSenderAddress());
        assertEquals(1, email.getRecipientAddresses().size());
        assertEquals(EMAIL, email.getRecipientAddresses().get(0));
        assertEquals("AE This study name", email.getSubject());
        assertEquals(EmailType.RESET_PASSWORD, email.getType());
        
        MimeBodyPart body = email.getMessageParts().get(0);
        String bodyString = (String)body.getContent();
        assertTrue(bodyString.contains("/mobile/resetPassword.html?study=api&sptoken="+SPTOKEN));
        assertTrue(bodyString.contains("/rp?study=api&sptoken="+SPTOKEN));
        // This was recently added and is only used in one study where we've hard-coded it. Remove it
        // so that ${url} continues to work for the reset password link. We're moving all links 
        // towad the short form, in stepped releases.
        //assertTrue(bodyString.contains("/mobile/api/startSession.html?email=email%40email.com&study=api&token="+TOKEN));
        assertTrue(bodyString.contains("/s/api?email=email%40email.com&token="+TOKEN));
        
        // All the template variables have been replaced
        assertFalse(bodyString.contains("${url}"));
        assertFalse(bodyString.contains("${resetPasswordUrl}"));
        assertFalse(bodyString.contains("${emailSignInUrl}"));
        assertFalse(bodyString.contains("${shortUrl}"));
        assertFalse(bodyString.contains("${shortResetPasswordUrl}"));
        assertFalse(bodyString.contains("${shortEmailSignInUrl}"));
    }

    @Test
    public void notifyAccountExistsForEmailNoSignIn() throws Exception {
        // In this path email sign in is also enabled, so we will generate a link to sign in that can 
        // be used in lieu of directing the user to a password reset.
        study.setEmailSignInEnabled(false);
        study.setEmailVerificationEnabled(true);
        AccountId accountId = AccountId.forId(TEST_STUDY_IDENTIFIER, USER_ID);
     // */when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);
        when(service.getNextToken()).thenReturn(SPTOKEN);
        when(mockAccount.getEmail()).thenReturn(EMAIL);
        when(mockAccount.getEmailVerified()).thenReturn(Boolean.TRUE);
        when(mockAccountDao.getAccount(accountId)).thenReturn(mockAccount);
     // */when(mockAccountDao.getAccount(AccountId.forEmail(TEST_STUDY_IDENTIFIER, EMAIL))).thenReturn(mockAccount);
        
        service.notifyAccountExists(study, accountId);
        
        verify(mockCacheProvider).setObject(PASSWORD_RESET_FOR_EMAIL, EMAIL, 60*60*2);
        verify(mockSendMailService).sendEmail(emailProviderCaptor.capture());
        
        BasicEmailProvider provider = emailProviderCaptor.getValue();
        
        // not set, no email sign in
        assertNull(provider.getTokenMap().get("token"));
        assertNull(provider.getTokenMap().get("email"));
        
        assertEquals(SPTOKEN, provider.getTokenMap().get("sptoken"));
        assertEquals("2 hours", provider.getTokenMap().get("expirationPeriod"));
        assertEquals("2", provider.getTokenMap().get("expirationWindow"));
        
        MimeTypeEmail email = provider.getMimeTypeEmail();
        assertEquals("\"This study name\" <support@support.com>", email.getSenderAddress());
        assertEquals(1, email.getRecipientAddresses().size());
        assertEquals(EMAIL, email.getRecipientAddresses().get(0));
        assertEquals("AE This study name", email.getSubject());
        
        MimeBodyPart body = email.getMessageParts().get(0);
        String bodyString = (String)body.getContent();
        assertTrue(bodyString.contains("/mobile/resetPassword.html?study=api&sptoken="+SPTOKEN));
        assertTrue(bodyString.contains("/rp?study=api&sptoken="+SPTOKEN));
        assertTrue(bodyString.contains("${emailSignInUrl}"));
        
        // The remaining template variables have been replaced.
        assertFalse(bodyString.contains("${url}"));
        assertFalse(bodyString.contains("${resetPasswordUrl}"));
        assertFalse(bodyString.contains("${shortUrl}"));
        assertFalse(bodyString.contains("${shortResetPasswordUrl}"));
        assertFalse(bodyString.contains("${shortEmailSignInUrl}"));
    }    
    @Test
    public void notifyAccountForEmailSignInDoesntThrottle() throws Exception {
        study.setEmailSignInEnabled(true);
        study.setEmailVerificationEnabled(true);
        AccountId accountId = AccountId.forId(TEST_STUDY_IDENTIFIER, USER_ID);
        when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);
        when(service.getNextToken()).thenReturn(SPTOKEN, TOKEN, SPTOKEN, TOKEN, SPTOKEN, TOKEN);
        when(mockAccount.getEmail()).thenReturn(EMAIL);
        when(mockAccount.getEmailVerified()).thenReturn(Boolean.TRUE);
        when(mockAccountDao.getAccount(any())).thenReturn(mockAccount);

        // Throttle limit is 2, but it doesn't apply to notifyAccount(). Call this 3 times, and expect 3 emails with
        // email sign-in URL.
        service.notifyAccountExists(study, accountId);
        service.notifyAccountExists(study, accountId);
        service.notifyAccountExists(study, accountId);

        verify(mockSendMailService, times(3)).sendEmail(emailProviderCaptor.capture());

        List<BasicEmailProvider> emailProviderList = emailProviderCaptor.getAllValues();
        for (BasicEmailProvider oneEmailProvider : emailProviderList) {
            assertEquals("2", oneEmailProvider.getTokenMap().get("expirationWindow"));
            assertEquals(SPTOKEN, oneEmailProvider.getTokenMap().get("sptoken"));
            assertEquals(TOKEN, oneEmailProvider.getTokenMap().get("token"));
            assertEquals("2 hours", oneEmailProvider.getTokenMap().get("expirationPeriod"));
            assertEquals(BridgeUtils.encodeURIComponent(EMAIL), oneEmailProvider.getTokenMap().get("email"));
            
            // Email content is verified in test above. Just verify email sign-in URL.
            MimeTypeEmail email = oneEmailProvider.getMimeTypeEmail();
            MimeBodyPart body = email.getMessageParts().get(0);
            String bodyString = (String)body.getContent();
            assertTrue(bodyString.contains("/s/api?email=email%40email.com&token="+TOKEN));
            assertFalse(bodyString.contains("${emailSignInUrl}"));
            assertFalse(bodyString.contains("${shortEmailSignInUrl}"));
        }
        verify(mockCacheProvider, times(3)).setObject(PASSWORD_RESET_FOR_EMAIL, EMAIL,
                AccountWorkflowService.VERIFY_OR_RESET_EXPIRE_IN_SECONDS);
    }

    @Test
    public void notifyAccountExistsForEmailWithoutEmailSignIn() throws Exception {
        study.setEmailVerificationEnabled(true);
        // A successful notification of an existing account where email sign in is not enabled. The 
        // emailSignIn template variable will not be replaced.
        AccountId accountId = AccountId.forId(TEST_STUDY_IDENTIFIER, USER_ID);
        when(service.getNextToken()).thenReturn(SPTOKEN);
        when(mockAccount.getEmail()).thenReturn(EMAIL);
        when(mockAccount.getEmailVerified()).thenReturn(Boolean.TRUE);
        when(mockAccountDao.getAccount(accountId)).thenReturn(mockAccount);
        
        service.notifyAccountExists(study, accountId);
        
        verify(mockSendMailService).sendEmail(emailProviderCaptor.capture());
        
        BasicEmailProvider provider = emailProviderCaptor.getValue();
        assertEquals(SPTOKEN, provider.getTokenMap().get("sptoken"));
        assertEquals("2", provider.getTokenMap().get("expirationWindow"));
        assertEquals("2 hours", provider.getTokenMap().get("expirationPeriod"));

        String bodyString = (String) provider.getMimeTypeEmail().getMessageParts().get(0).getContent();
        
        assertTrue(bodyString.contains("${emailSignInUrl}"));
        
        verify(mockCacheProvider).setObject(PASSWORD_RESET_FOR_EMAIL, EMAIL,
                AccountWorkflowService.VERIFY_OR_RESET_EXPIRE_IN_SECONDS);
    }    
    
    @Test
    public void notifyAccountExistsForPhone() throws Exception {
        when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);
        study.setPhoneSignInEnabled(true);
        AccountId accountId = AccountId.forPhone(TEST_STUDY_IDENTIFIER, TestConstants.PHONE);
        when(service.getNextToken()).thenReturn(SPTOKEN);
        when(service.getNextPhoneToken()).thenReturn(PHONE_TOKEN);
        when(mockAccount.getPhone()).thenReturn(TestConstants.PHONE);
        when(mockAccount.getPhoneVerified()).thenReturn(Boolean.TRUE);
        when(mockAccountDao.getAccount(accountId)).thenReturn(mockAccount);
        
        service.notifyAccountExists(study, accountId);
        
        verify(mockCacheProvider).setObject(PASSWORD_RESET_FOR_PHONE, 
                BridgeObjectMapper.get().writeValueAsString(TestConstants.PHONE), 
                AccountWorkflowService.VERIFY_OR_RESET_EXPIRE_IN_SECONDS);
        verify(mockSmsService).sendSmsMessage(eq(USER_ID), smsMessageProviderCaptor.capture());
        
        String message = smsMessageProviderCaptor.getValue().getSmsRequest().getMessage();
        assertTrue(message.contains("Account for ShortName already exists. Reset password: "));
        assertTrue(message.contains("/rp?study=api&sptoken="+SPTOKEN));
        assertTrue(message.contains(" or "+PHONE_TOKEN.substring(0,3) + "-" + PHONE_TOKEN.substring(3,6)));
        assertEquals("Transactional", smsMessageProviderCaptor.getValue().getSmsType());
    }
    
    @Test
    public void notifyAccountExistsForPhoneNoSignIn() throws Exception {
     // */when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);
        study.setPhoneSignInEnabled(false);
        AccountId accountId = AccountId.forPhone(TEST_STUDY_IDENTIFIER, TestConstants.PHONE);
        when(service.getNextToken()).thenReturn(SPTOKEN);
     // */when(service.getNextPhoneToken()).thenReturn(PHONE_TOKEN);
        when(mockAccount.getPhone()).thenReturn(TestConstants.PHONE);
        when(mockAccount.getPhoneVerified()).thenReturn(Boolean.TRUE);
        when(mockAccountDao.getAccount(accountId)).thenReturn(mockAccount);
        
        service.notifyAccountExists(study, accountId);
        
        verify(mockCacheProvider).setObject(PASSWORD_RESET_FOR_PHONE, 
                BridgeObjectMapper.get().writeValueAsString(TestConstants.PHONE), 
                AccountWorkflowService.VERIFY_OR_RESET_EXPIRE_IN_SECONDS);
        verify(mockSmsService).sendSmsMessage(eq(USER_ID), smsMessageProviderCaptor.capture());
        
        String message = smsMessageProviderCaptor.getValue().getSmsRequest().getMessage();
        assertTrue(message.contains("Account for ShortName already exists. Reset password: "));
        assertTrue(message.contains("/rp?study=api&sptoken="+SPTOKEN));
        assertTrue(message.contains(" or ${token}"));
        assertEquals("Transactional", smsMessageProviderCaptor.getValue().getSmsType());
        verify(service, never()).getNextPhoneToken();
    }
    
    @Test
    public void notifyAccountExistsWithPhoneAutoVerifySuppressed() {
     // */when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);
        study.setPhoneSignInEnabled(true);
        study.setAutoVerificationPhoneSuppressed(true);
        
        AccountId accountId = AccountId.forPhone(TEST_STUDY_IDENTIFIER, TestConstants.PHONE);
     // */when(service.getNextToken()).thenReturn(SPTOKEN);
     // */when(service.getNextPhoneToken()).thenReturn(PHONE_TOKEN);
        when(mockAccount.getPhone()).thenReturn(TestConstants.PHONE);
        when(mockAccount.getPhoneVerified()).thenReturn(Boolean.TRUE);
        when(mockAccountDao.getAccount(accountId)).thenReturn(mockAccount);
        
        service.notifyAccountExists(study, accountId);
        
        verify(mockCacheProvider, never()).setObject(any(), any(), anyInt());
        verify(mockSmsService, never()).sendSmsMessage(any(), any());
    }
    
    @Test
    public void notifyAccountExistsWithEmailAutoVerifySuppressed() {
        // In this path email sign in is also enabled, so we will generate a link to sign in that can 
        // be used in lieu of directing the user to a password reset.
        study.setEmailSignInEnabled(true);
        study.setAutoVerificationEmailSuppressed(true);
        
        AccountId accountId = AccountId.forId(TEST_STUDY_IDENTIFIER, USER_ID);
     // */when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);
     // */when(service.getNextToken()).thenReturn(SPTOKEN, TOKEN);
        when(mockAccount.getEmail()).thenReturn(EMAIL);
        when(mockAccount.getEmailVerified()).thenReturn(Boolean.TRUE);
        when(mockAccountDao.getAccount(accountId)).thenReturn(mockAccount);
     // */when(mockAccountDao.getAccount(AccountId.forEmail(TEST_STUDY_IDENTIFIER, EMAIL))).thenReturn(mockAccount);
        
        service.notifyAccountExists(study, accountId);
        
        verify(mockCacheProvider, never()).setObject(any(), any(), anyInt());
        verify(mockSendMailService, never()).sendEmail(any());
    }
    
    @Test
    public void notifyAccountExistsWithEmailVerifyOff() {
        // In this path email sign in is also enabled, so we will generate a link to sign in that can 
        // be used in lieu of directing the user to a password reset.
        study.setEmailSignInEnabled(true);
        study.setAutoVerificationEmailSuppressed(false);
        study.setEmailVerificationEnabled(false);
        
        AccountId accountId = AccountId.forId(TEST_STUDY_IDENTIFIER, USER_ID);
     // */when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);
     // */when(service.getNextToken()).thenReturn(SPTOKEN, TOKEN);
        when(mockAccount.getEmail()).thenReturn(EMAIL);
        when(mockAccount.getEmailVerified()).thenReturn(Boolean.TRUE);
        when(mockAccountDao.getAccount(accountId)).thenReturn(mockAccount);
     // */when(mockAccountDao.getAccount(AccountId.forEmail(TEST_STUDY_IDENTIFIER, EMAIL))).thenReturn(mockAccount);
        
        service.notifyAccountExists(study, accountId);
        
        verify(mockCacheProvider, never()).setObject(any(), any(), anyInt());
        verify(mockSendMailService, never()).sendEmail(any());
    }

    @Test
    public void notifyAccountExistsUnverifiedEmailUnverifiedPhone() {
        // Set study flags so that it would send emails/SMS if they were verified.
        study.setEmailVerificationEnabled(true);
        study.setAutoVerificationEmailSuppressed(false);
        study.setAutoVerificationPhoneSuppressed(false);

        // Mock account DAO.
        AccountId accountId = AccountId.forId(TEST_STUDY_IDENTIFIER, USER_ID);
        when(mockAccount.getEmail()).thenReturn(EMAIL);
        when(mockAccount.getEmailVerified()).thenReturn(Boolean.FALSE);
        when(mockAccount.getPhone()).thenReturn(TestConstants.PHONE);
        when(mockAccount.getPhoneVerified()).thenReturn(Boolean.FALSE);
        when(mockAccountDao.getAccount(accountId)).thenReturn(mockAccount);

        // Execute.
        service.notifyAccountExists(study, accountId);

        // We never send email nor SMS.
        verify(mockSendMailService, never()).sendEmail(any());
        verify(mockSmsService, never()).sendSmsMessage(any(), any());
    }

    @Test
    public void notifyAccountExistsEmailVerifiedNullPhoneVerifiedNull() {
        // Set study flags so that it would send emails/SMS if they were verified.
        study.setEmailVerificationEnabled(true);
        study.setAutoVerificationEmailSuppressed(false);
        study.setAutoVerificationPhoneSuppressed(false);

        // Mock account DAO.
        AccountId accountId = AccountId.forId(TEST_STUDY_IDENTIFIER, USER_ID);
        when(mockAccount.getEmail()).thenReturn(EMAIL);
        when(mockAccount.getEmailVerified()).thenReturn(null);
        when(mockAccount.getPhone()).thenReturn(TestConstants.PHONE);
        when(mockAccount.getPhoneVerified()).thenReturn(null);
        when(mockAccountDao.getAccount(accountId)).thenReturn(mockAccount);

        // Execute.
        service.notifyAccountExists(study, accountId);

        // We never send email nor SMS.
        verify(mockSendMailService, never()).sendEmail(any());
        verify(mockSmsService, never()).sendSmsMessage(any(), any());
    }

    @Test
    public void requestResetPasswordWithEmail() throws Exception {
        when(service.getNextToken()).thenReturn(SPTOKEN);
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenReturn(mockAccount);
     // */when(mockStudyService.getStudy(TEST_STUDY)).thenReturn(study);
        when(mockAccount.getEmail()).thenReturn(EMAIL);
        when(mockAccount.getEmailVerified()).thenReturn(Boolean.TRUE);        
     // */when(mockAccount.getStudyId()).thenReturn(TEST_STUDY_IDENTIFIER);
        
        service.requestResetPassword(study, false, ACCOUNT_ID_WITH_EMAIL);
        
        verify(mockCacheProvider).setObject(PASSWORD_RESET_FOR_EMAIL, EMAIL, 60*60*2);
        verify(mockSendMailService).sendEmail(emailProviderCaptor.capture());
        BasicEmailProvider provider = emailProviderCaptor.getValue();
        
        assertEquals(SPTOKEN, provider.getTokenMap().get("sptoken"));
        assertEquals("2", provider.getTokenMap().get("expirationWindow"));
        assertEquals("2 hours", provider.getTokenMap().get("expirationPeriod"));
        
        MimeTypeEmail email = provider.getMimeTypeEmail();
        assertEquals("\"This study name\" <support@support.com>", email.getSenderAddress());
        assertEquals(1, email.getRecipientAddresses().size());
        assertEquals(EMAIL, email.getRecipientAddresses().get(0));
        assertEquals("RP This study name", email.getSubject());
        MimeBodyPart body = email.getMessageParts().get(0);
        String bodyString = (String)body.getContent();
        assertTrue(bodyString.contains("/rp?study=api&sptoken="+SPTOKEN));
        assertEquals(EmailType.RESET_PASSWORD, email.getType());
        verifyNoMoreInteractions(mockCacheProvider);
    }
    
    @Test
    public void requestResetPasswordWithPhone() throws Exception {
        when(service.getNextToken()).thenReturn(SPTOKEN);
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_PHONE)).thenReturn(mockAccount);
     // */when(mockStudyService.getStudy(TEST_STUDY)).thenReturn(study);
        when(mockAccount.getPhone()).thenReturn(TestConstants.PHONE);
        when(mockAccount.getPhoneVerified()).thenReturn(Boolean.TRUE);        
     // */when(mockAccount.getStudyId()).thenReturn(TEST_STUDY_IDENTIFIER);
        
        service.requestResetPassword(study, false, ACCOUNT_ID_WITH_PHONE);
        
        verify(mockCacheProvider).setObject(eq(PASSWORD_RESET_FOR_PHONE), stringCaptor.capture(), eq(60*60*2));
        verify(mockSmsService).sendSmsMessage(eq(USER_ID), smsMessageProviderCaptor.capture());
        
        assertEquals(study, smsMessageProviderCaptor.getValue().getStudy());
        assertEquals(TestConstants.PHONE, smsMessageProviderCaptor.getValue().getPhone());
        assertEquals("Transactional", smsMessageProviderCaptor.getValue().getSmsType());
        String message = smsMessageProviderCaptor.getValue().getSmsRequest().getMessage();
        assertTrue(message.contains("Reset ShortName password: "));
        assertTrue(message.contains("/rp?study=api&sptoken="+SPTOKEN));
        
        Phone captured = BridgeObjectMapper.get().readValue(stringCaptor.getValue(), Phone.class);
        assertEquals(TestConstants.PHONE, captured); 
        verifyNoMoreInteractions(mockCacheProvider);
    }

    @Test
    public void requestResetPasswordFailsQuietlyIfEmailPhoneUnverifiedUsingEmail() {
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_PHONE)).thenReturn(mockAccount);
        when(mockAccount.getEmail()).thenReturn(EMAIL);
        when(mockAccount.getPhoneVerified()).thenReturn(Boolean.FALSE);
        when(mockAccount.getEmailVerified()).thenReturn(Boolean.FALSE);
     // */when(mockAccount.getStudyId()).thenReturn(TEST_STUDY_IDENTIFIER);

        service.requestResetPassword(study, false, ACCOUNT_ID_WITH_PHONE);
        
        verify(mockCacheProvider, never()).setObject(any(), any(), anyInt());
        verify(mockSmsService, never()).sendSmsMessage(any(), any());
        verifyNoMoreInteractions(mockCacheProvider);
    }

    @Test
    public void requestResetPasswordFailsQuietlyIfEmailPhoneUnverifiedUsingPhone() {
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_PHONE)).thenReturn(mockAccount);
        when(mockAccount.getPhone()).thenReturn(TestConstants.PHONE);
        when(mockAccount.getPhoneVerified()).thenReturn(Boolean.FALSE);
        when(mockAccount.getEmailVerified()).thenReturn(Boolean.FALSE);
     // */when(mockAccount.getStudyId()).thenReturn(TEST_STUDY_IDENTIFIER);
        
        service.requestResetPassword(study, false, ACCOUNT_ID_WITH_PHONE);
        
        verify(mockCacheProvider, never()).setObject(any(), any(), anyInt());
        verify(mockSmsService, never()).sendSmsMessage(any(), any());
        verifyNoMoreInteractions(mockCacheProvider);
    }
    
    @Test
    public void requestResetPasswordInvalidEmailFailsQuietly() {
     // */when(service.getNextToken()).thenReturn(TOKEN);
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenReturn(null);
        
        service.requestResetPassword(study, false, ACCOUNT_ID_WITH_EMAIL);
        
        verify(mockCacheProvider, never()).setObject(any(), any(), anyInt());
        verify(mockSendMailService, never()).sendEmail(any());
        verifyNoMoreInteractions(mockCacheProvider);
    }

    @Test
    public void requestRestPasswordUnverifiedEmailFailsQuietly() {
     // */when(service.getNextToken()).thenReturn(TOKEN);
        when(mockAccount.getEmail()).thenReturn(EMAIL);
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenReturn(mockAccount);
        
        service.requestResetPassword(study, false, ACCOUNT_ID_WITH_EMAIL);
        
        verifyNoMoreInteractions(mockSendMailService);
        verifyNoMoreInteractions(mockSmsService);
        verifyNoMoreInteractions(mockCacheProvider);
    }
    
    @Test
    public void requestRestPasswordUnverifiedPhoneFailsQuietly() {
     // */when(service.getNextToken()).thenReturn(TOKEN);
        when(mockAccount.getPhone()).thenReturn(TestConstants.PHONE);
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_PHONE)).thenReturn(mockAccount);
        
        service.requestResetPassword(study, false, ACCOUNT_ID_WITH_PHONE);
        
        verifyNoMoreInteractions(mockSendMailService);
        verifyNoMoreInteractions(mockSmsService);
        verifyNoMoreInteractions(mockCacheProvider);
    }
    
    @Test
    public void requestResetPasswordByAdminDoesNotRequireEmailVerification() {
        when(service.getNextToken()).thenReturn(SPTOKEN);
        when(mockAccount.getEmail()).thenReturn(EMAIL);
     // */when(mockAccount.getEmailVerified()).thenReturn(false);
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_PHONE)).thenReturn(mockAccount);
        
        service.requestResetPassword(study, true, ACCOUNT_ID_WITH_PHONE);
        
        verify(mockCacheProvider).setObject(PASSWORD_RESET_FOR_EMAIL, EMAIL, 60*60*2);
        verify(mockSendMailService).sendEmail(any());
    }
    
    @Test
    public void requestResetPasswordByAdminDoesNotRequirePhoneVerification() {
        when(service.getNextToken()).thenReturn(SPTOKEN);
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_PHONE)).thenReturn(mockAccount);
        when(mockAccount.getPhone()).thenReturn(TestConstants.PHONE);
     // */when(mockAccount.getPhoneVerified()).thenReturn(false);        
        
        service.requestResetPassword(study, true, ACCOUNT_ID_WITH_PHONE);
        
        verify(mockCacheProvider).setObject(eq(PASSWORD_RESET_FOR_PHONE), stringCaptor.capture(), eq(60*60*2));
        verify(mockSmsService).sendSmsMessage(any(), any());
    }
    
    @Test
    public void requestResetPasswordQuietlyFailsForDisabledAccount() {
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_PHONE)).thenReturn(mockAccount);
     // */when(mockAccount.getPhone()).thenReturn(TestConstants.PHONE);
     // */when(mockAccount.getPhoneVerified()).thenReturn(Boolean.TRUE);
     // */when(mockAccount.getEmailVerified()).thenReturn(Boolean.TRUE);
     // */when(mockAccount.getStudyId()).thenReturn(TEST_STUDY_IDENTIFIER);
        when(mockAccount.getStatus()).thenReturn(AccountStatus.DISABLED);
        
        service.requestResetPassword(study, false, ACCOUNT_ID_WITH_PHONE);
        
        verify(mockCacheProvider, never()).setObject(any(), any(), anyInt());
        verify(mockSmsService, never()).sendSmsMessage(any(), any());
        verifyNoMoreInteractions(mockCacheProvider);
    }
    
    @Test
    public void resetPasswordWithEmail() {
        when(mockCacheProvider.getObject(PASSWORD_RESET_FOR_EMAIL, String.class)).thenReturn(EMAIL);
        when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenReturn(mockAccount);

        PasswordReset passwordReset = new PasswordReset("newPassword", SPTOKEN, TEST_STUDY_IDENTIFIER);
        service.resetPassword(passwordReset);
        
        verify(mockCacheProvider).getObject(PASSWORD_RESET_FOR_EMAIL, String.class);
        verify(mockCacheProvider).removeObject(PASSWORD_RESET_FOR_EMAIL);
        verify(mockAccountDao).changePassword(mockAccount, ChannelType.EMAIL, "newPassword");
    }
    
    @Test
    public void resetPasswordWithPhone() {
        when(mockCacheProvider.getObject(PASSWORD_RESET_FOR_PHONE, Phone.class)).thenReturn(TestConstants.PHONE);
        when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_PHONE)).thenReturn(mockAccount);

        PasswordReset passwordReset = new PasswordReset("newPassword", SPTOKEN, TEST_STUDY_IDENTIFIER);
        service.resetPassword(passwordReset);
        
        verify(mockCacheProvider).getObject(PASSWORD_RESET_FOR_PHONE, Phone.class);
        verify(mockCacheProvider).removeObject(PASSWORD_RESET_FOR_PHONE);
        verify(mockAccountDao).changePassword(mockAccount, ChannelType.PHONE, "newPassword");
    }
    
    @Test
    public void resetPasswordInvalidSptokenThrowsException() {
        when(mockCacheProvider.getObject(PASSWORD_RESET_FOR_EMAIL, String.class)).thenReturn(null);

        PasswordReset passwordReset = new PasswordReset("newPassword", SPTOKEN, TEST_STUDY_IDENTIFIER);
        try {
            service.resetPassword(passwordReset);
            fail("Should have thrown exception");
        } catch(BadRequestException e) {
            assertEquals("Password reset token has expired (or already been used).", e.getMessage());
        }
        verify(mockCacheProvider).getObject(PASSWORD_RESET_FOR_EMAIL, String.class);
        verify(mockCacheProvider, never()).removeObject(any());
        verify(mockAccountDao, never()).changePassword(any(), any(ChannelType.class), any());
    }
    
    @Test
    public void resetPasswordInvalidAccount() {
     // */when(service.getNextToken()).thenReturn("777777");
        when(mockCacheProvider.getObject(PASSWORD_RESET_FOR_EMAIL, String.class)).thenReturn(EMAIL);
        when(mockStudyService.getStudy(TEST_STUDY_IDENTIFIER)).thenReturn(study);
        when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_EMAIL)).thenReturn(null);

        PasswordReset passwordReset = new PasswordReset("newPassword", SPTOKEN, TEST_STUDY_IDENTIFIER);
        
        try {
            service.resetPassword(passwordReset);
            fail("Should have thrown an exception");
        } catch(EntityNotFoundException e) {
            // expected exception
        }
        verify(mockCacheProvider).getObject(PASSWORD_RESET_FOR_EMAIL, String.class);
        verify(mockCacheProvider).removeObject(PASSWORD_RESET_FOR_EMAIL);
        verify(mockAccountDao, never()).changePassword(any(), any(ChannelType.class), any());
    }
    
    @Test
    public void requestEmailSignIn() throws Exception {
        // Mock.
        study.setEmailSignInEnabled(true);
        when(mockAccountDao.getAccount(SIGN_IN_REQUEST_WITH_EMAIL.getAccountId())).thenReturn(mockAccount);
        when(mockStudyService.getStudy(study.getIdentifier())).thenReturn(study);
        when(service.getNextToken()).thenReturn(TOKEN);

        // Execute.
        String userId = service.requestEmailSignIn(SIGN_IN_REQUEST_WITH_EMAIL);
        assertEquals(USER_ID, userId);

        // Verify dependent services.
        verify(mockCacheProvider).getObject(keyCaptor.capture(), eq(String.class));
        assertEquals(EMAIL_SIGNIN_CACHE_KEY, keyCaptor.getValue());
        
        verify(mockAccountDao).getAccount(SIGN_IN_REQUEST_WITH_EMAIL.getAccountId());
        
        verify(mockCacheProvider).setObject(eq(EMAIL_SIGNIN_CACHE_KEY), stringCaptor.capture(), eq(AccountWorkflowService.SIGNIN_EXPIRE_IN_SECONDS));
        assertNotNull(stringCaptor.getValue());

        verify(mockSendMailService).sendEmail(emailProviderCaptor.capture());
        
        BasicEmailProvider provider = emailProviderCaptor.getValue();
        assertEquals(BridgeUtils.encodeURIComponent(EMAIL), provider.getTokenMap().get("email"));
        assertEquals(TOKEN, provider.getTokenMap().get("token"));
        assertEquals("1 hour", provider.getTokenMap().get("emailSignInExpirationPeriod"));
        
        String token = provider.getTokenMap().get("token");
        
        // api exists in this portion of the URL, indicating variable substitution occurred
        assertTrue(provider.getTokenMap().get("url").contains("/mobile/api/startSession.html"));
        assertTrue(provider.getTokenMap().get("shortUrl").contains("/s/api"));
        assertTrue(provider.getTokenMap().get("url").contains(token));
        assertTrue(provider.getTokenMap().get("shortUrl").contains(token));
        assertEquals(study, provider.getStudy());
        assertEquals(EMAIL, Iterables.getFirst(provider.getRecipientEmails(), null));
        assertEquals("Body " + provider.getTokenMap().get("token"),
                provider.getMimeTypeEmail().getMessageParts().get(0).getContent());
        assertEquals(EmailType.EMAIL_SIGN_IN, provider.getType());
        verifyNoMoreInteractions(mockCacheProvider);
    }
    
    @Test
    public void requestEmailSignInFailureDelays() {
        study.setEmailSignInEnabled(true);
        service.getEmailSignInRequestInMillis().set(1000);
        when(mockAccountDao.getAccount(any())).thenReturn(null);
        when(mockStudyService.getStudy(study.getIdentifier())).thenReturn(study);
                 
        long start = System.currentTimeMillis();
        service.requestEmailSignIn(SIGN_IN_REQUEST_WITH_EMAIL);
        long total = System.currentTimeMillis()-start;
        assertTrue(total >= 1000);
        service.getEmailSignInRequestInMillis().set(0);
        verifyNoMoreInteractions(mockCacheProvider);
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
        when(mockStudyService.getStudy(study.getIdentifier())).thenReturn(study);
        
        service.requestEmailSignIn(SIGN_IN_REQUEST_WITH_EMAIL);
    }

    @Test(expected = UnauthorizedException.class)
    public void requestPhoneSignInDisabled() {
        study.setPhoneSignInEnabled(false);
        when(mockStudyService.getStudy(study.getIdentifier())).thenReturn(study);
        
        service.requestPhoneSignIn(SIGN_IN_REQUEST_WITH_PHONE);
    }
    
    @Test
    public void requestEmailSignInThrottles() {
        study.setEmailSignInEnabled(true);
        when(service.getNextToken()).thenReturn(SPTOKEN);
        when(mockAccountDao.getAccount(any())).thenReturn(mockAccount);
        when(mockStudyService.getStudy(study.getIdentifier())).thenReturn(study);

        // Throttle limit is 2. Request 3 times. Get 2 emails. (Each call should still return userId.
        String userId = service.requestEmailSignIn(SIGN_IN_REQUEST_WITH_EMAIL);
        assertEquals(USER_ID, userId);

        userId = service.requestEmailSignIn(SIGN_IN_REQUEST_WITH_EMAIL);
        assertEquals(USER_ID, userId);

        userId = service.requestEmailSignIn(SIGN_IN_REQUEST_WITH_EMAIL);
        assertEquals(USER_ID, userId);

        verify(mockSendMailService, times(2)).sendEmail(any());
    }
    

    @Test
    public void requestEmailSignInTwiceReturnsSameToken() throws Exception {
        // In this case, where there is a value and an account, we do't generate a new one,
        // we just send the message again.
        study.setEmailSignInEnabled(true);
        when(mockCacheProvider.getObject(EMAIL_SIGNIN_CACHE_KEY, String.class)).thenReturn(TOKEN);
        when(mockAccountDao.getAccount(any())).thenReturn(mockAccount);
        when(mockStudyService.getStudy(study.getIdentifier())).thenReturn(study);
        
        service.requestEmailSignIn(SIGN_IN_REQUEST_WITH_EMAIL);
        
        verify(mockCacheProvider, never()).setObject(any(), any(), anyInt());
        verify(mockSendMailService).sendEmail(emailProviderCaptor.capture());
        
        BasicEmailProvider provider = emailProviderCaptor.getValue();
        assertEquals(BridgeUtils.encodeURIComponent(EMAIL), provider.getTokenMap().get("email"));
        assertEquals(TOKEN, provider.getTokenMap().get("token"));
        assertEquals("1 hour", provider.getTokenMap().get("emailSignInExpirationPeriod"));
        
        assertEquals(EMAIL, provider.getMimeTypeEmail().getRecipientAddresses().get(0));
        assertEquals(SUPPORT_EMAIL, provider.getPlainSenderEmail());
        String bodyString = (String)provider.getMimeTypeEmail().getMessageParts().get(0).getContent();
        assertEquals("Body "+TOKEN, bodyString);
        
        verify(mockCacheProvider).getObject(EMAIL_SIGNIN_CACHE_KEY, String.class);
    }

    @Test
    public void requestEmailSignInEmailNotRegistered() {
        // Mock.
        study.setEmailSignInEnabled(true);
     // */when(mockAccountDao.getAccount(ACCOUNT_ID_WITH_ID)).thenReturn(null);
        when(mockStudyService.getStudy(study.getIdentifier())).thenReturn(study);

        // Execute. Returns null userId.
        String userId = service.requestEmailSignIn(SIGN_IN_REQUEST_WITH_EMAIL);
        assertNull(userId);

        // Verify dependent services are never called.
        verify(mockCacheProvider, never()).setObject(any(), any(), anyInt());
        verify(mockSendMailService, never()).sendEmail(any());
        verifyNoMoreInteractions(mockCacheProvider);
    }
    
    @Test
    public void requestPhoneSignIn() {
        // Mock.
        study.setPhoneSignInEnabled(true);
        study.setShortName("AppName");
        when(mockAccountDao.getAccount(SIGN_IN_WITH_PHONE.getAccountId())).thenReturn(mockAccount);
        when(service.getNextPhoneToken()).thenReturn("123456");
        when(mockStudyService.getStudy(study.getIdentifier())).thenReturn(study);

        // Execute.
        String userId = service.requestPhoneSignIn(SIGN_IN_REQUEST_WITH_PHONE);
        assertEquals(USER_ID, userId);

        // Verify dependent services.
        verify(mockCacheProvider).getObject(PHONE_SIGNIN_CACHE_KEY, String.class);
        verify(mockCacheProvider).setObject(PHONE_SIGNIN_CACHE_KEY, "123456", AccountWorkflowService.SIGNIN_EXPIRE_IN_SECONDS);
        verify(mockSmsService).sendSmsMessage(eq(USER_ID), smsMessageProviderCaptor.capture());

        assertEquals(study, smsMessageProviderCaptor.getValue().getStudy());
        assertEquals(TestConstants.PHONE, smsMessageProviderCaptor.getValue().getPhone());
        assertEquals("Transactional", smsMessageProviderCaptor.getValue().getSmsType());
        String message = smsMessageProviderCaptor.getValue().getSmsRequest().getMessage();
        assertEquals("Enter 123-456 to sign in to AppName", message);
        verifyNoMoreInteractions(mockCacheProvider);
    }
    
    @Test
    public void requestPhoneSignInFails() {
        study.setPhoneSignInEnabled(true);
        when(mockStudyService.getStudy(study.getIdentifier())).thenReturn(study);

        // This should fail silently, or we risk giving away information about accounts in the system.
        String userId = service.requestPhoneSignIn(SIGN_IN_REQUEST_WITH_PHONE);
        assertNull(userId);

        verify(mockCacheProvider, never()).setObject(any(), any(), anyInt());
        verify(mockSmsService, never()).sendSmsMessage(any(),any());
        verifyNoMoreInteractions(mockCacheProvider);
    }

    @Test
    public void requestPhoneSignInThrottles() {
        study.setPhoneSignInEnabled(true);
        when(mockAccountDao.getAccount(any())).thenReturn(mockAccount);
        when(mockStudyService.getStudy(study.getIdentifier())).thenReturn(study);

        // This is currently disabled. Request 3 times, get 3 texts. (Each call should still return the user ID.)
        String userId = service.requestPhoneSignIn(SIGN_IN_REQUEST_WITH_PHONE);
        assertEquals(USER_ID, userId);

        userId = service.requestPhoneSignIn(SIGN_IN_REQUEST_WITH_PHONE);
        assertEquals(USER_ID, userId);

        userId = service.requestPhoneSignIn(SIGN_IN_REQUEST_WITH_PHONE);
        assertEquals(USER_ID, userId);

        verify(mockSmsService, times(3)).sendSmsMessage(any(), any());
    }

    @Test
    public void emailChannelSignIn() {
        when(mockCacheProvider.getObject(EMAIL_SIGNIN_CACHE_KEY, String.class)).thenReturn(TOKEN);
        
        AccountId returnedAccount = service.channelSignIn(ChannelType.EMAIL, CONTEXT, SIGN_IN_WITH_EMAIL, SignInValidator.EMAIL_SIGNIN);
        
        verify(mockCacheProvider).getObject(EMAIL_SIGNIN_CACHE_KEY, String.class);
        verify(mockCacheProvider).removeObject(EMAIL_SIGNIN_CACHE_KEY);
        assertEquals(SIGN_IN_WITH_EMAIL.getAccountId(), returnedAccount);
        verifyNoMoreInteractions(mockCacheProvider);
    }
    
    @Test(expected = UnsupportedOperationException.class)
    public void channelSignInUnsupportedType() {
        // use null for this test so we don't have to create a dummy type
        service.channelSignIn(null, CONTEXT, SIGN_IN_WITH_EMAIL, SignInValidator.EMAIL_SIGNIN);
    }
    
    @Test
    public void phoneChannelSignIn() {
        when(mockCacheProvider.getObject(PHONE_CACHE_KEY, String.class)).thenReturn(TOKEN);
        
        AccountId returnedAccount = service.channelSignIn(ChannelType.PHONE, CONTEXT, SIGN_IN_WITH_PHONE, SignInValidator.PHONE_SIGNIN);
        
        verify(mockCacheProvider).getObject(PHONE_CACHE_KEY, String.class);
        verify(mockCacheProvider).removeObject(PHONE_CACHE_KEY);
        assertEquals(SIGN_IN_WITH_PHONE.getAccountId(), returnedAccount);
        verifyNoMoreInteractions(mockCacheProvider);
    }
    
    @Test
    public void phoneChannelSignInWithFormattingDashWorks() {
        when(mockCacheProvider.getObject(PHONE_CACHE_KEY, String.class)).thenReturn(TOKEN);
        
        SignIn signIn = new SignIn.Builder().withStudy(STUDY_ID)
                .withPhone(TestConstants.PHONE).withToken("ABC-DEF").build();
        
        AccountId returnedAccount = service.channelSignIn(ChannelType.PHONE, CONTEXT, signIn, SignInValidator.PHONE_SIGNIN);
        
        verify(mockCacheProvider).getObject(PHONE_CACHE_KEY, String.class);
        verify(mockCacheProvider).removeObject(PHONE_CACHE_KEY);
        assertEquals(SIGN_IN_WITH_PHONE.getAccountId(), returnedAccount);
        verifyNoMoreInteractions(mockCacheProvider);
    }
    
    @Test
    public void phoneChannelSignInWithFormattingSpaceWorks() {
        when(mockCacheProvider.getObject(PHONE_CACHE_KEY, String.class)).thenReturn(TOKEN);
        
        SignIn signIn = new SignIn.Builder().withStudy(STUDY_ID)
                .withPhone(TestConstants.PHONE).withToken("ABC DEF").build();
        
        AccountId returnedAccount = service.channelSignIn(ChannelType.PHONE, CONTEXT, signIn, SignInValidator.PHONE_SIGNIN);
        
        verify(mockCacheProvider).getObject(PHONE_CACHE_KEY, String.class);
        verify(mockCacheProvider).removeObject(PHONE_CACHE_KEY);
        assertEquals(SIGN_IN_WITH_PHONE.getAccountId(), returnedAccount);
        verifyNoMoreInteractions(mockCacheProvider);
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
     // */when(mockCacheProvider.getObject(EMAIL_SIGNIN_CACHE_KEY, String.class)).thenReturn(TOKEN);

        SignIn wrongEmailSignIn = new SignIn.Builder().withStudy(STUDY_ID)
                .withEmail("wrong-email@email.com").withToken(TOKEN).build();

        service.channelSignIn(ChannelType.EMAIL, CONTEXT, wrongEmailSignIn, SignInValidator.EMAIL_SIGNIN);
    }

    @Test(expected = AuthenticationFailedException.class)
    public void channelSignInWrongPhoneThrowsException() {
     // */when(mockCacheProvider.getObject(PHONE_CACHE_KEY, String.class)).thenReturn(TOKEN);

        SignIn wrongPhoneSignIn = new SignIn.Builder().withStudy(STUDY_ID)
                .withPhone(new Phone("4082588569", "US")).withToken(TOKEN).build();

        service.channelSignIn(ChannelType.PHONE, CONTEXT, wrongPhoneSignIn, SignInValidator.PHONE_SIGNIN);
    }
}
