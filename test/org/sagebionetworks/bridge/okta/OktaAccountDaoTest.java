package org.sagebionetworks.bridge.okta;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.mail.internet.MimeBodyPart;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.crypto.BridgeEncryptor;
import org.sagebionetworks.bridge.dynamodb.DynamoHealthId;
import org.sagebionetworks.bridge.exceptions.AccountDisabledException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountStatus;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.Email;
import org.sagebionetworks.bridge.models.accounts.EmailVerification;
import org.sagebionetworks.bridge.models.accounts.HealthId;
import org.sagebionetworks.bridge.models.accounts.PasswordReset;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.MimeType;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.HealthCodeService;
import org.sagebionetworks.bridge.services.SendMailService;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.services.SubpopulationService;
import org.sagebionetworks.bridge.services.email.MimeTypeEmail;
import org.sagebionetworks.bridge.services.email.MimeTypeEmailProvider;

import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.okta.sdk.clients.AuthApiClient;
import com.okta.sdk.clients.UserApiClient;
import com.okta.sdk.framework.FilterBuilder;
import com.okta.sdk.framework.PagedResults;
import com.okta.sdk.models.auth.AuthResult;
import com.okta.sdk.models.users.LoginCredentials;
import com.okta.sdk.models.users.Password;
import com.okta.sdk.models.users.User;
import com.okta.sdk.models.users.UserProfile;

@RunWith(MockitoJUnitRunner.class)
public class OktaAccountDaoTest {

    private static final String SP_TOKEN = "random-sptoken";
    private static final String PASSWORD = "password";
    private static final String NEW_PASSWORD = "new-password";
    private static final String USER_ID = "abc";
    private static final String EMAIL = "test@test.com";
    private static final String STUDY_ID = "test-study";
    private static final StudyIdentifier STUDY_ID_OBJ = new StudyIdentifierImpl(STUDY_ID);
    private static final Email EMAIL_OBJ = new Email(STUDY_ID, EMAIL);
    private static final SignIn SIGN_IN = new SignIn(STUDY_ID, EMAIL, PASSWORD, null);

    @Mock
    private StudyService mockStudyService;
    @Mock
    private SubpopulationService mockSubpopService;
    @Mock
    private HealthCodeService mockHealthCodeService;
    @Mock
    private CacheProvider mockCacheProvider;
    @Mock
    private SendMailService mockSendMailService;
    @Mock
    private OktaAccount mockAccount;
    @Mock
    private AuthApiClient mockAuthApiClient;
    @Mock
    private UserApiClient mockUserApiClient;
    @Mock
    private AuthResult mockAuthResult;
    @Mock
    private BridgeEncryptor bridgeEncryptor; 
    
    @Captor
    ArgumentCaptor<MimeTypeEmailProvider> emailProviderCaptor;
    @Captor
    ArgumentCaptor<FilterBuilder> filterCaptor;

    private List<BridgeEncryptor> encryptors = Lists.newArrayList();

    private User user;

    private UserProfile userProfile;

    private Map<String, Object> userProfileUnmapped;
    
    private Study study;

    private OktaAccountDao accountDao;

    @Before
    public void before() {
        encryptors.add(bridgeEncryptor);
        
        accountDao = new OktaAccountDao();
        accountDao.setStudyService(mockStudyService);
        accountDao.setSubpopulationService(mockSubpopService);
        accountDao.setHealthCodeService(mockHealthCodeService);
        accountDao.setCacheProvider(mockCacheProvider);
        accountDao.setSendMailService(mockSendMailService);
        accountDao.setAuthApiClient(mockAuthApiClient);
        accountDao.setUserApiClient(mockUserApiClient);
        accountDao.setEncryptors(encryptors);
        
        study = Study.create();
        when(mockStudyService.getStudy(STUDY_ID)).thenReturn(study);
        when(mockStudyService.getStudy(STUDY_ID_OBJ)).thenReturn(study);
        
        user = new User();
        user.setId(USER_ID);
        userProfile = new UserProfile();
        user.setProfile(userProfile);
        userProfileUnmapped = userProfile.getUnmapped();
    }

    @Test
    public void verifyEmail() throws Exception {
        userProfileUnmapped.put(OktaAccount.STATUS, AccountStatus.UNVERIFIED.name());
        when(mockCacheProvider.getString(SP_TOKEN)).thenReturn(USER_ID);
        when(mockUserApiClient.getUser(USER_ID)).thenReturn(user);

        EmailVerification verification = new EmailVerification(SP_TOKEN);
        accountDao.verifyEmail(verification);

        verify(mockUserApiClient).getUser(USER_ID);
        verify(mockUserApiClient).updateUser(user);
        assertEquals(AccountStatus.ENABLED.name(), userProfileUnmapped.get(OktaAccount.STATUS));
    }
    
    @Test
    public void verifyEmailNoToken() throws Exception {
        when(mockCacheProvider.getString(SP_TOKEN)).thenReturn(null);
        when(mockUserApiClient.getUser(USER_ID)).thenReturn(user);

        EmailVerification verification = new EmailVerification(SP_TOKEN);
        try {
            accountDao.verifyEmail(verification);    
        } catch(BridgeServiceException e) {
            assertEquals("Email verification token not found. You may have already enabled this account.", e.getMessage());
        }
    }
    
    @Test
    public void verifyEmailNoUser() throws Exception {
        when(mockCacheProvider.getString(SP_TOKEN)).thenReturn(SP_TOKEN);
        when(mockUserApiClient.getUser(USER_ID)).thenReturn(null);

        EmailVerification verification = new EmailVerification(SP_TOKEN);
        try {
            accountDao.verifyEmail(verification);            
        } catch(EntityNotFoundException e) {
            assertEquals("Account not found.", e.getMessage());
        }
    }
    
    @Test
    public void resendEmailVerificationToken() throws Exception {
        EmailTemplate template = new EmailTemplate("subject", "body ${url}", MimeType.HTML);
        study.setIdentifier(STUDY_ID);
        study.setVerifyEmailTemplate(template);
        study.setName("Study Name");
        study.setSupportEmail("sponsor@sponsor.com");
        
        List<User> users = Lists.newArrayList(user);
        when(mockUserApiClient.getUsersWithAdvancedSearch(any())).thenReturn(users);
        
        accountDao = spy(accountDao);
        when(accountDao.createTimeLimitedToken()).thenReturn(SP_TOKEN);
        
        accountDao.resendEmailVerificationToken(STUDY_ID_OBJ, EMAIL_OBJ);
        
        verify(mockCacheProvider).setString(SP_TOKEN, USER_ID, 60*5);
        verify(mockSendMailService).sendEmail(emailProviderCaptor.capture());
        
        MimeTypeEmailProvider provider = emailProviderCaptor.getValue();
        MimeTypeEmail email = provider.getMimeTypeEmail();
        
        assertEquals("subject", email.getSubject());
        assertEquals("\"Study Name\" <sponsor@sponsor.com>", email.getSenderAddress());
        assertEquals("test@test.com", email.getRecipientAddresses().get(0));
        MimeBodyPart body = email.getMessageParts().get(0);
        
        String bodyString = (String)body.getContent();
        assertTrue(bodyString.contains("/mobile/verifyEmail.html?study=test-study&sptoken=random-sptoken"));
    }

    @Test
    public void requestResetPassword() throws Exception {
        EmailTemplate template = new EmailTemplate("subject", "body ${url}", MimeType.HTML);
        study.setIdentifier(STUDY_ID);
        study.setResetPasswordTemplate(template);
        study.setName("Study Name");
        study.setSupportEmail("sponsor@sponsor.com");
        
        List<User> users = Lists.newArrayList(user);
        when(mockUserApiClient.getUsersWithAdvancedSearch(any())).thenReturn(users);
        
        accountDao = spy(accountDao);
        when(accountDao.createTimeLimitedToken()).thenReturn(SP_TOKEN);

        accountDao.requestResetPassword(study, EMAIL_OBJ);
        
        verify(mockCacheProvider).setString(SP_TOKEN+":"+STUDY_ID, EMAIL, 60*5);
        verify(mockSendMailService).sendEmail(emailProviderCaptor.capture());
        
        MimeTypeEmailProvider provider = emailProviderCaptor.getValue();
        MimeTypeEmail email = provider.getMimeTypeEmail();
        
        assertEquals("subject", email.getSubject());
        assertEquals("\"Study Name\" <sponsor@sponsor.com>", email.getSenderAddress());
        assertEquals("test@test.com", email.getRecipientAddresses().get(0));
        MimeBodyPart body = email.getMessageParts().get(0);
        
        String bodyString = (String)body.getContent();
        assertTrue(bodyString.contains("/mobile/resetPassword.html?study=test-study&sptoken=random-sptoken"));
    }
    
    @Test
    public void requestResetPasswordInvalidEmail() throws Exception {
        EmailTemplate template = new EmailTemplate("subject", "body ${url}", MimeType.HTML);
        study.setIdentifier(STUDY_ID);
        study.setResetPasswordTemplate(template);
        study.setName("Study Name");
        study.setSupportEmail("sponsor@sponsor.com");
        
        // NO USER RETURNED
        List<User> users = Lists.newArrayList();
        when(mockUserApiClient.getUsersWithAdvancedSearch(any())).thenReturn(users);

        accountDao.requestResetPassword(study, EMAIL_OBJ);
        
        verify(mockCacheProvider, never()).setString(SP_TOKEN+":"+STUDY_ID, EMAIL, 60*5);
        verify(mockSendMailService, never()).sendEmail(emailProviderCaptor.capture());
    }

    @Test
    public void resetPassword() throws Exception {
        PasswordReset passwordReset = new PasswordReset(PASSWORD, SP_TOKEN, STUDY_ID);
        String cacheKey = passwordReset.getSptoken() + ":" + passwordReset.getStudyIdentifier();
        when(mockCacheProvider.getString(cacheKey)).thenReturn(EMAIL);
        
        List<User> users = Lists.newArrayList(user);
        when(mockUserApiClient.getUsersWithAdvancedSearch(any())).thenReturn(users);
        
        accountDao.resetPassword(passwordReset);
        
        verify(mockCacheProvider).getString(cacheKey);
        verify(mockCacheProvider).removeString(cacheKey);
        verify(mockUserApiClient).setPassword(USER_ID, PASSWORD);
    }
    
    @Test
    public void resetPasswordInvalidToken() throws Exception {
        PasswordReset passwordReset = new PasswordReset(PASSWORD, SP_TOKEN, STUDY_ID);
        String cacheKey = passwordReset.getSptoken() + ":" + passwordReset.getStudyIdentifier();
        when(mockCacheProvider.getString(cacheKey)).thenReturn(null);
        
        List<User> users = Lists.newArrayList(user);
        when(mockUserApiClient.getUsersWithAdvancedSearch(any())).thenReturn(users);
        
        accountDao.resetPassword(passwordReset);
        
        verify(mockCacheProvider).getString(cacheKey);
        verify(mockCacheProvider, never()).removeString(cacheKey);
        verify(mockUserApiClient, never()).setPassword(USER_ID, PASSWORD);
    }

    @Test
    public void changePassword() throws Exception {
        when(mockAccount.getId()).thenReturn("ACCOUNT_ID");
        accountDao.changePassword(mockAccount, NEW_PASSWORD);
        
        verify(mockUserApiClient).setPassword("ACCOUNT_ID", NEW_PASSWORD);
    }

    @Test
    public void authenticate() throws Exception {
        Account account = setupAuthenticationTest("ENABLED");
        
        verify(mockAuthApiClient).authenticate(EMAIL, PASSWORD, null);
        assertEquals(AccountStatus.ENABLED, account.getStatus());
    }
    
    @Test(expected = AccountDisabledException.class)
    public void authenticateAccountDisabled() throws Exception {
        setupAuthenticationTest("DISABLED");
        
        verify(mockAuthApiClient).authenticate(EMAIL, PASSWORD, null);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void authenticateAccountUnverified() throws Exception {
        setupAuthenticationTest("UNVERIFIED");
        
        verify(mockAuthApiClient).authenticate(EMAIL, PASSWORD, null);
    }
    
    private Account setupAuthenticationTest(String userStatus) throws Exception {
        study.setIdentifier(STUDY_ID);
        
        Map<String,Object> userMap = Maps.newHashMap();
        userMap.put("id", USER_ID);
        
        Map<String,Object> resultMap = Maps.newHashMap();
        resultMap.put("user", userMap);
        
        userProfileUnmapped.put(OktaAccount.STATUS, userStatus);

        when(mockUserApiClient.getUser(USER_ID)).thenReturn(user);
        
        when(mockAuthResult.getEmbedded()).thenReturn(resultMap);
        when(mockAuthApiClient.authenticate(EMAIL, PASSWORD, null)).thenReturn(mockAuthResult);
        when(mockAccount.getStatus()).thenReturn(AccountStatus.ENABLED);
        
        return accountDao.authenticate(study, SIGN_IN);        
    }

    @Test
    public void constructAccount() throws Exception {
        study.setIdentifier(STUDY_ID);
        
        HealthId healthId = new DynamoHealthId("healthId", "healthCode");
        when(mockHealthCodeService.createMapping(any())).thenReturn(healthId);
        
        accountDao = spy(accountDao);
        List<SubpopulationGuid> subpopGuids = Lists.newArrayList();
        when(accountDao.getSubpopulationGuids(STUDY_ID_OBJ)).thenReturn(subpopGuids);
        
        Account account = accountDao.constructAccount(study, EMAIL, PASSWORD);

        assertEquals("test@test.com", account.getEmail());
        assertEquals("healthCode", account.getHealthCode());
        assertEquals(STUDY_ID_OBJ, account.getStudyIdentifier());
        
        User user = ((OktaAccount)account).getUser();
        
        UserProfile userProfile = user.getProfile();
        assertEquals("test@test.com", userProfile.getLogin());
        assertEquals("test@test.com", userProfile.getEmail());
        
        LoginCredentials login = user.getCredentials();
        Password password = login.getPassword();
        assertEquals("password", password.getValue());
    }
    
    @Test
    public void createAccount() throws Exception {
        when(mockAccount.getUser()).thenReturn(user);
        when(mockUserApiClient.createUser(user, true)).thenReturn(user);
        
        List<User> users = Lists.newArrayList(user);
        when(mockUserApiClient.getUsersWithAdvancedSearch(any())).thenReturn(users);
        
        user.setId(USER_ID);
        when(mockAccount.getEmail()).thenReturn(EMAIL);
        
        accountDao = spy(accountDao);
        
        accountDao.createAccount(study, mockAccount, true);
        
        verify(mockAccount).setStatus(AccountStatus.UNVERIFIED);
        verify(mockUserApiClient).createUser(user, true);
        verify(mockAccount).setUser(user);
        // We've verified the email production in a test above, verify the right data is marshalled here
        verify(accountDao).sendEmailVerificationToken(study, USER_ID, EMAIL);
    }

    @Test
    public void updateAccount() throws Exception {
        when(mockAccount.getUser()).thenReturn(user);
        when(mockUserApiClient.updateUser(user)).thenReturn(user);
        
        accountDao.updateAccount(mockAccount);
        
        verify(mockAccount).getUser();
        verify(mockUserApiClient).updateUser(user);
        verify(mockAccount).setUser(user);
    }

    @Test
    public void getAccount() throws Exception {
        study.setIdentifier(STUDY_ID);
        user.setId(USER_ID);
        
        HealthId healthId = new DynamoHealthId("healthId", "healthCode");
        when(mockHealthCodeService.createMapping(any())).thenReturn(healthId);
        
        List<SubpopulationGuid> subpopGuids = Lists.newArrayList();
        when(accountDao.getSubpopulationGuids(STUDY_ID_OBJ)).thenReturn(subpopGuids);
        when(mockUserApiClient.updateUser(user)).thenReturn(user);
        when(mockUserApiClient.getUser(USER_ID)).thenReturn(user);
        
        OktaAccount response = (OktaAccount)accountDao.getAccount(study, USER_ID);
        assertEquals(user, response.getUser());
    }

    // getAccountNoUser <-- not sure if Okta API throws exception or returns null.
    
    @Test
    public void getAccountWithEmail() throws Exception {
        study.setIdentifier(STUDY_ID);
        user.setId(USER_ID);

        when(mockUserApiClient.getUsersWithAdvancedSearch(any())).thenReturn(Lists.newArrayList(user));
        
        HealthId healthId = new DynamoHealthId("healthId", "healthCode");
        when(mockHealthCodeService.createMapping(any())).thenReturn(healthId);
        
        List<SubpopulationGuid> subpopGuids = Lists.newArrayList();
        when(accountDao.getSubpopulationGuids(STUDY_ID_OBJ)).thenReturn(subpopGuids);
        when(mockUserApiClient.updateUser(user)).thenReturn(user);
        when(mockUserApiClient.getUser(USER_ID)).thenReturn(user);
        
        OktaAccount response = (OktaAccount)accountDao.getAccountWithEmail(study, EMAIL);
        assertEquals(user, response.getUser());
    }

    // Should just return null if there is no account
    @Test
    public void getAccountWithEmailInvalidEmail() throws Exception {
        study.setIdentifier(STUDY_ID);
        user.setId(USER_ID);

        when(mockUserApiClient.getUsersWithAdvancedSearch(any())).thenReturn(Lists.newArrayList());
        
        assertNull(accountDao.getAccountWithEmail(study, EMAIL));
    }
    
    
    @Test
    public void deleteAccount() throws Exception {
        when(mockUserApiClient.getUser(USER_ID)).thenReturn(user);
        
        accountDao.deleteAccount(study, USER_ID);
        
        verify(mockUserApiClient).deactivateUser(USER_ID);
        verify(mockUserApiClient).deleteUser(USER_ID);
    }
    
    @Test
    public void deleteAccountInvalidUserId() throws Exception {
        when(mockUserApiClient.getUser(USER_ID)).thenReturn(null);
        
        accountDao.deleteAccount(study, USER_ID);
        
        verify(mockUserApiClient, never()).deactivateUser(USER_ID);
        verify(mockUserApiClient, never()).deleteUser(USER_ID);
    }    

    @SuppressWarnings("unchecked")
    @Test
    public void getAllAccounts() throws Exception {
        // two studies of a test page each are combined in an iterator of 6 records. 
        Study study1 = Study.create();
        study1.setIdentifier("study1");
        Study study2 = Study.create();
        study1.setIdentifier("study2");
        // This will get called in two studies
        List<Study> studies = Lists.newArrayList(study1, study2);
        when(mockStudyService.getStudies()).thenReturn(studies);
        
        PagedResults<User> page1 = TestUtils.makeOktaPagedResults(0, true);
        PagedResults<User> page2 = TestUtils.makeOktaPagedResults(4, true);
        when(mockUserApiClient.getUsersPagedResultsWithLimit(100)).thenReturn(page1, page2);
        
        Iterator<AccountSummary> summaries = accountDao.getAllAccounts();
        assertEquals(6, Iterators.size(summaries));
    }

    @Test
    public void getStudyAccounts() throws Exception {
        PagedResults<User> page1 = TestUtils.makeOktaPagedResults(0, true);
        when(mockUserApiClient.getUsersPagedResultsWithLimit(100)).thenReturn(page1);
        
        Iterator<AccountSummary> summaries = accountDao.getStudyAccounts(study);
        assertEquals(3, Iterators.size(summaries));
    }

    @Test
    public void getPagedAccountSummaries() throws Exception {
        DateTime startsOn = DateTime.now().minusHours(2);
        DateTime endsOn = DateTime.now();
        
        PagedResults<User> pagedResults = TestUtils.makeOktaPagedResults(0, true);
        
        when(mockUserApiClient.getUsersPagedResultsWithAdvancedSearchAndLimitAndAfterCursor(any(FilterBuilder.class), anyInt(), anyString()))
                .thenReturn(pagedResults);
        
        accountDao.getPagedAccountSummaries(study, "offsetBy", 10, "emailFilter", startsOn, endsOn);
        
        verify(mockUserApiClient).getUsersPagedResultsWithAdvancedSearchAndLimitAndAfterCursor(
                filterCaptor.capture(), eq(10), eq("offsetBy"));
        
        FilterBuilder builder = filterCaptor.getValue();
        String query = builder.toString();
        
        // TODO: This looks right to me given the docs; I haven't tested it manually against Okta yet
        String exp = "profile.email sw \"emailFilter\" and user.created ge \"" + startsOn.toString()
        + "\" and user.created le \"" + endsOn.toString() + "\"";
        
        assertEquals(exp, query);
    }

    @Test
    public void getHealthCodeForEmail() throws Exception {
        study.setIdentifier(STUDY_ID);
        when(mockAccount.getHealthCode()).thenReturn("healthCode");
        
        List<User> users = Lists.newArrayList(user);
        when(mockUserApiClient.getUsersWithAdvancedSearch(any())).thenReturn(users);
        
        HealthId healthId = new DynamoHealthId("healthId", "healthCode");
        when(mockHealthCodeService.createMapping(any())).thenReturn(healthId);
        
        String healthCode = accountDao.getHealthCodeForEmail(study, EMAIL);
        
        assertEquals("healthCode", healthCode);
    }

    @Test
    public void getHealthCodeForEmailInvalidEmail() throws Exception {
        study.setIdentifier(STUDY_ID);
        
        List<User> users = Lists.newArrayList();
        when(mockUserApiClient.getUsersWithAdvancedSearch(any())).thenReturn(users);
        
        String healthCode = accountDao.getHealthCodeForEmail(study, EMAIL);
        assertNull(healthCode);
    }
    
}
