package org.sagebionetworks.bridge.hibernate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.runners.MockitoJUnitRunner;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.exceptions.AccountDisabledException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.AccountSummarySearch;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.AccountStatus;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.PasswordAlgorithm;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.substudies.AccountSubstudy;
import org.sagebionetworks.bridge.services.AuthenticationService;
import org.sagebionetworks.bridge.services.AuthenticationService.ChannelType;

@RunWith(MockitoJUnitRunner.class)
public class HibernateAccountDaoTest {
    private static final String ACCOUNT_ID = "account-id";
    private static final DateTime CREATED_ON = DateTime.parse("2017-05-19T11:03:50.224-0700");
    private static final String DUMMY_PASSWORD = "Aa!Aa!Aa!Aa!1";
    private static final String EMAIL = "eggplant@example.com";
    private static final Phone PHONE = TestConstants.PHONE;
    private static final Phone OTHER_PHONE = new Phone("+12065881469", "US");
    private static final String OTHER_EMAIL = "other-email@example.com";
    private static final String HEALTH_CODE = "health-code";
    private static final DateTime MOCK_DATETIME = DateTime.parse("2017-05-19T14:45:27.593-0700");
    private static final String FIRST_NAME = "Eggplant";
    private static final String LAST_NAME = "McTester";
    private static final String REAUTH_TOKEN = "reauth-token";
    private static final String EXTERNAL_ID = "an-external-id";
    private static final AccountId ACCOUNT_ID_WITH_ID = AccountId.forId(TestConstants.TEST_STUDY_IDENTIFIER, ACCOUNT_ID);
    private static final AccountId ACCOUNT_ID_WITH_EMAIL = AccountId.forEmail(TestConstants.TEST_STUDY_IDENTIFIER, EMAIL);
    private static final AccountId ACCOUNT_ID_WITH_PHONE = AccountId.forPhone(TestConstants.TEST_STUDY_IDENTIFIER, TestConstants.PHONE);
    private static final AccountId ACCOUNT_ID_WITH_HEALTHCODE = AccountId.forHealthCode(TestConstants.TEST_STUDY_IDENTIFIER, HEALTH_CODE);
    private static final AccountId ACCOUNT_ID_WITH_EXTID = AccountId.forExternalId(TestConstants.TEST_STUDY_IDENTIFIER, EXTERNAL_ID);
    
    private static final String SUBSTUDY_A = "substudyA";
    private static final String SUBSTUDY_B = "substudyB";
    private static final Set<AccountSubstudy> ACCOUNT_SUBSTUDIES = ImmutableSet
            .of(AccountSubstudy.create(TestConstants.TEST_STUDY_IDENTIFIER, SUBSTUDY_A, ACCOUNT_ID));
    private static final ImmutableSet<String> CALLER_SUBSTUDIES = ImmutableSet.of(SUBSTUDY_B);

    private static final SignIn REAUTH_SIGNIN = new SignIn.Builder().withStudy(TestConstants.TEST_STUDY_IDENTIFIER)
            .withEmail(EMAIL).withReauthToken(REAUTH_TOKEN).build();
    private static final SignIn PASSWORD_SIGNIN = new SignIn.Builder().withStudy(TestConstants.TEST_STUDY_IDENTIFIER)
            .withEmail(EMAIL).withPassword(DUMMY_PASSWORD).build();

    private static final Map<String,Object> STUDY_QUERY_PARAMS = new ImmutableMap.Builder<String,Object>()
            .put("studyId", TestConstants.TEST_STUDY_IDENTIFIER).build();
    private static final Map<String,Object> EMAIL_QUERY_PARAMS = new ImmutableMap.Builder<String,Object>()
            .put("studyId", TestConstants.TEST_STUDY_IDENTIFIER)
            .put("email", EMAIL).build();
    private static final Map<String,Object> HEALTHCODE_QUERY_PARAMS = new ImmutableMap.Builder<String,Object>()
            .put("studyId", TestConstants.TEST_STUDY_IDENTIFIER)
            .put("healthCode", HEALTH_CODE).build();
    private static final Map<String,Object> PHONE_QUERY_PARAMS = new ImmutableMap.Builder<String,Object>()
            .put("studyId", TestConstants.TEST_STUDY_IDENTIFIER)
            .put("number", TestConstants.PHONE.getNumber())
            .put("regionCode", TestConstants.PHONE.getRegionCode()).build();
    private static final Map<String,Object> EXTID_QUERY_PARAMS = new ImmutableMap.Builder<String,Object>()
            .put("studyId", TestConstants.TEST_STUDY_IDENTIFIER)
            .put("externalId", EXTERNAL_ID).build();
    
    @Captor
    ArgumentCaptor<Map<String,Object>> paramCaptor;
    
    private Study study;
    private HibernateAccountDao dao;
    private HibernateHelper mockHibernateHelper;
    private CacheProvider mockCacheProvider;

    @BeforeClass
    public static void mockNow() {
        DateTimeUtils.setCurrentMillisFixed(MOCK_DATETIME.getMillis());
    }

    @AfterClass
    public static void unmockNow() {
        DateTimeUtils.setCurrentMillisSystem();
    }

    @Before
    public void before() {
        mockHibernateHelper = mock(HibernateHelper.class);
        mockCacheProvider = mock(CacheProvider.class);
        
        // Mock successful update.
        when(mockHibernateHelper.update(any())).thenAnswer(invocation -> {
            HibernateAccount account = invocation.getArgumentAt(0, HibernateAccount.class);
            if (account != null) {
                account.setVersion(account.getVersion()+1);    
            }
            return account;
        });
        
        dao = spy(new HibernateAccountDao());
        dao.setHibernateHelper(mockHibernateHelper);
        dao.setCacheProvider(mockCacheProvider);
        when(dao.generateGUID()).thenReturn(HEALTH_CODE);
        
        study = Study.create();
        study.setIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);
        study.setReauthenticationEnabled(true);
        study.setEmailVerificationEnabled(true);
    }
    
    @After
    public void after() { 
        BridgeUtils.setRequestContext(null);
    }

    @Test
    public void verifyEmailUsingToken() {
        HibernateAccount hibernateAccount = new HibernateAccount();
        hibernateAccount.setStatus(AccountStatus.UNVERIFIED);
        hibernateAccount.setEmailVerified(Boolean.FALSE);
        
        Account account = Account.create();
        account.setId(ACCOUNT_ID);
        account.setStatus(AccountStatus.UNVERIFIED);
        account.setEmailVerified(Boolean.FALSE);
        
        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(hibernateAccount);
        
        dao.verifyChannel(ChannelType.EMAIL, account);
        
        assertEquals(AccountStatus.ENABLED, hibernateAccount.getStatus());
        assertEquals(Boolean.TRUE, hibernateAccount.getEmailVerified());
        assertNotNull(hibernateAccount.getModifiedOn());
        assertEquals(AccountStatus.ENABLED, account.getStatus());
        assertEquals(Boolean.TRUE, account.getEmailVerified());
        verify(mockHibernateHelper).update(hibernateAccount);
    }

    @Test
    public void verifyEmailUsingAccount() {
        HibernateAccount hibernateAccount = new HibernateAccount();
        hibernateAccount.setStatus(AccountStatus.UNVERIFIED);
        hibernateAccount.setEmailVerified(Boolean.FALSE);
        
        Account account = Account.create();
        account.setId(ACCOUNT_ID);
        account.setStatus(AccountStatus.UNVERIFIED);
        account.setEmailVerified(Boolean.FALSE);
        
        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(hibernateAccount);
        
        dao.verifyChannel(AuthenticationService.ChannelType.EMAIL, account);
        
        assertEquals(AccountStatus.ENABLED, hibernateAccount.getStatus());
        assertEquals(Boolean.TRUE, hibernateAccount.getEmailVerified());
        assertNotNull(hibernateAccount.getModifiedOn());
        assertEquals(AccountStatus.ENABLED, account.getStatus());
        assertEquals(Boolean.TRUE, account.getEmailVerified());
        verify(mockHibernateHelper).update(hibernateAccount);
    }
    
    @Test
    public void verifyEmailUsingAccountNoChangeNecessary() {
        HibernateAccount hibernateAccount = new HibernateAccount();
        hibernateAccount.setStatus(AccountStatus.ENABLED);
        hibernateAccount.setEmailVerified(Boolean.TRUE);
        
        Account account = Account.create();
        account.setId(ACCOUNT_ID);
        account.setStatus(AccountStatus.ENABLED);
        account.setEmailVerified(Boolean.TRUE);
        
        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(hibernateAccount);
        
        dao.verifyChannel(AuthenticationService.ChannelType.EMAIL, account);
        verify(mockHibernateHelper, never()).update(hibernateAccount);
    }
    
    @Test
    public void verifyEmailWithDisabledAccountMakesNoChanges() {
        Account account = Account.create();
        account.setStatus(AccountStatus.DISABLED);
        
        dao.verifyChannel(AuthenticationService.ChannelType.EMAIL, account);
        verify(mockHibernateHelper, never()).update(any());
        assertEquals(AccountStatus.DISABLED, account.getStatus());
    }
    
    @Test
    public void verifyEmailFailsIfHibernateAccountNotFound() {
        Account account = Account.create();
        account.setId(ACCOUNT_ID);
        account.setStatus(AccountStatus.UNVERIFIED);
        account.setEmailVerified(null);
        
        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(null);
        try {
            dao.verifyChannel(AuthenticationService.ChannelType.EMAIL, account);
            fail("Should have thrown an exception");
        } catch (EntityNotFoundException e) {
            // expected exception
        }
        verify(mockHibernateHelper, never()).update(any());
        assertEquals(AccountStatus.UNVERIFIED, account.getStatus());
        assertNull(account.getEmailVerified());
    }
    
    @Test
    public void verifyPhoneUsingToken() {
        HibernateAccount hibernateAccount = new HibernateAccount();
        hibernateAccount.setStatus(AccountStatus.UNVERIFIED);
        hibernateAccount.setPhoneVerified(Boolean.FALSE);
        
        Account account = Account.create();
        account.setId(ACCOUNT_ID);
        account.setStatus(AccountStatus.UNVERIFIED);
        account.setPhoneVerified(Boolean.FALSE);
        
        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(hibernateAccount);
        
        dao.verifyChannel(ChannelType.PHONE, account);
        
        assertEquals(AccountStatus.ENABLED, hibernateAccount.getStatus());
        assertEquals(Boolean.TRUE, hibernateAccount.getPhoneVerified());
        assertNotNull(hibernateAccount.getModifiedOn());
        assertEquals(AccountStatus.ENABLED, account.getStatus());
        assertEquals(Boolean.TRUE, account.getPhoneVerified());
        verify(mockHibernateHelper).update(hibernateAccount);
    }
    
    @Test
    public void verifyPhoneUsingAccount() {
        HibernateAccount hibernateAccount = new HibernateAccount();
        hibernateAccount.setStatus(AccountStatus.UNVERIFIED);
        hibernateAccount.setPhoneVerified(Boolean.FALSE);
        
        Account account = Account.create();
        account.setId(ACCOUNT_ID);
        account.setStatus(AccountStatus.UNVERIFIED);
        account.setPhoneVerified(Boolean.FALSE);
        
        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(hibernateAccount);
        
        dao.verifyChannel(AuthenticationService.ChannelType.PHONE, account);
        
        assertEquals(AccountStatus.ENABLED, hibernateAccount.getStatus());
        assertEquals(Boolean.TRUE, hibernateAccount.getPhoneVerified());
        assertNotNull(hibernateAccount.getModifiedOn());
        assertEquals(AccountStatus.ENABLED, account.getStatus());
        assertEquals(Boolean.TRUE, account.getPhoneVerified());
        verify(mockHibernateHelper).update(hibernateAccount);
    }
    
    @Test
    public void verifyPhoneUsingAccountNoChangeNecessary() {
        HibernateAccount hibernateAccount = new HibernateAccount();
        hibernateAccount.setStatus(AccountStatus.ENABLED);
        hibernateAccount.setPhoneVerified(Boolean.TRUE);
        
        Account account = Account.create();
        account.setId(ACCOUNT_ID);
        account.setStatus(AccountStatus.ENABLED);
        account.setPhoneVerified(Boolean.TRUE);
        
        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(hibernateAccount);
        
        dao.verifyChannel(AuthenticationService.ChannelType.PHONE, account);
        verify(mockHibernateHelper, never()).update(hibernateAccount);
    }
    
    @Test
    public void verifyPhoneWithDisabledAccountMakesNoChanges() {
        Account account = Account.create();
        account.setStatus(AccountStatus.DISABLED);
        
        dao.verifyChannel(AuthenticationService.ChannelType.PHONE, account);
        verify(mockHibernateHelper, never()).update(any());
        assertEquals(AccountStatus.DISABLED, account.getStatus());
    }
    
    @Test
    public void verifyPhoneFailsIfHibernateAccountNotFound() {
        Account account = Account.create();
        account.setId(ACCOUNT_ID);
        account.setStatus(AccountStatus.UNVERIFIED);
        account.setPhoneVerified(null);
        
        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(null);
        try {
            dao.verifyChannel(AuthenticationService.ChannelType.PHONE, account);
            fail("Should have thrown an exception");
        } catch (EntityNotFoundException e) {
            // expected exception
        }
        verify(mockHibernateHelper, never()).update(any());
        assertEquals(AccountStatus.UNVERIFIED, account.getStatus());
        assertNull(account.getPhoneVerified());
    }    
    
    @Test
    public void changePasswordSuccess() throws Exception {
        // mock hibernate
        HibernateAccount hibernateAccount = new HibernateAccount();
        hibernateAccount.setId(ACCOUNT_ID);
        hibernateAccount.setStatus(AccountStatus.UNVERIFIED);
        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(hibernateAccount);

        // Set up test account
        Account account = Account.create();
        account.setId(ACCOUNT_ID);

        // execute and verify
        dao.changePassword(account, ChannelType.EMAIL, DUMMY_PASSWORD);
        ArgumentCaptor<HibernateAccount> updatedAccountCaptor = ArgumentCaptor.forClass(HibernateAccount.class);
        verify(mockHibernateHelper).update(updatedAccountCaptor.capture());

        HibernateAccount updatedAccount = updatedAccountCaptor.getValue();
        assertEquals(ACCOUNT_ID, updatedAccount.getId());
        assertEquals(MOCK_DATETIME.getMillis(), updatedAccount.getModifiedOn().getMillis());
        assertEquals(PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM, updatedAccount.getPasswordAlgorithm());
        assertEquals(MOCK_DATETIME.getMillis(), updatedAccount.getPasswordModifiedOn().getMillis());
        assertTrue(updatedAccount.getEmailVerified());
        assertNull(updatedAccount.getPhoneVerified());
        assertEquals(AccountStatus.ENABLED, updatedAccount.getStatus());

        // validate password hash
        assertTrue(PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM.checkHash(updatedAccount.getPasswordHash(),
                DUMMY_PASSWORD));
    }

    @Test
    public void changePasswordForPhone() throws Exception {
        // mock hibernate
        HibernateAccount hibernateAccount = new HibernateAccount();
        hibernateAccount.setId(ACCOUNT_ID);
        hibernateAccount.setStatus(AccountStatus.UNVERIFIED);
        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(hibernateAccount);

        // Set up test account
        Account account = Account.create();
        account.setId(ACCOUNT_ID);

        // execute and verify
        dao.changePassword(account, ChannelType.PHONE, DUMMY_PASSWORD);
        ArgumentCaptor<HibernateAccount> updatedAccountCaptor = ArgumentCaptor.forClass(HibernateAccount.class);
        verify(mockHibernateHelper).update(updatedAccountCaptor.capture());

        // Simpler than changePasswordSuccess() test as we're only verifying phone is verified
        HibernateAccount updatedAccount = updatedAccountCaptor.getValue();
        assertNull(updatedAccount.getEmailVerified());
        assertTrue(updatedAccount.getPhoneVerified());
        assertEquals(AccountStatus.ENABLED, updatedAccount.getStatus());
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void changePasswordAccountNotFound() {
        // mock hibernate
        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(null);

        // Set up test account
        Account account = Account.create();
        account.setId(ACCOUNT_ID);

        // execute
        dao.changePassword(account, ChannelType.EMAIL, DUMMY_PASSWORD);
    }
    
    @Test
    public void changePasswordForExternalId() {
        // mock hibernate
        HibernateAccount hibernateAccount = new HibernateAccount();
        hibernateAccount.setId(ACCOUNT_ID);
        hibernateAccount.setStatus(AccountStatus.UNVERIFIED);
        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(hibernateAccount);

        // Set up test account
        Account account = Account.create();
        account.setId(ACCOUNT_ID);

        // execute and verify
        dao.changePassword(account, null, DUMMY_PASSWORD);
        ArgumentCaptor<HibernateAccount> updatedAccountCaptor = ArgumentCaptor.forClass(HibernateAccount.class);
        verify(mockHibernateHelper).update(updatedAccountCaptor.capture());

        // Simpler than changePasswordSuccess() test as we're only verifying phone is verified
        HibernateAccount updatedAccount = updatedAccountCaptor.getValue();
        assertNull(updatedAccount.getEmailVerified());
        assertNull(updatedAccount.getPhoneVerified());
        assertEquals(AccountStatus.ENABLED, updatedAccount.getStatus());
    }

    @Test
    public void authenticateSuccessWithHealthCode() throws Exception {
        String expQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.accountSubstudies "+
                "AS acctSubstudy WITH acct.id = acctSubstudy.accountId WHERE acct.studyId = :studyId AND "+
                "acct.email=:email GROUP BY acct.id";
        // mock hibernate
        HibernateAccount hibernateAccount = makeValidHibernateAccount(true, false);
        hibernateAccount.setHealthCode("original-" + HEALTH_CODE);
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any())).thenReturn(ImmutableList.of(hibernateAccount));

        String originalReauthTokenHash = hibernateAccount.getReauthTokenHash();
        
        // execute and verify - Verify just ID, study, and email, and health code mapping is enough.
        Account account = dao.authenticate(study, PASSWORD_SIGNIN);
        assertEquals(ACCOUNT_ID, account.getId());
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, account.getStudyId());
        assertEquals(EMAIL, account.getEmail());
        assertEquals("original-" + HEALTH_CODE, account.getHealthCode());
        assertNotNull(account.getReauthToken());
        assertEquals(2, account.getVersion()); // version was incremented by reauthentication
        assertNotEquals(originalReauthTokenHash, account.getReauthToken());
        
        // verify query
        verify(mockHibernateHelper).queryGet(expQuery, EMAIL_QUERY_PARAMS, null, null, HibernateAccount.class);
        
        ArgumentCaptor<HibernateAccount> accountCaptor = ArgumentCaptor.forClass(HibernateAccount.class);
        
        // We don't create a new health code mapping nor update the account.
        verify(mockHibernateHelper, times(1)).update(accountCaptor.capture());
        
        // healthCodes have not been changed
        assertEquals("original-" + HEALTH_CODE, accountCaptor.getValue().getHealthCode());
        assertNotEquals(originalReauthTokenHash, accountCaptor.getValue().getReauthTokenHash());
    }

    @Test
    public void authenticateSuccessCreateNewHealthCode() throws Exception {
        String expQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.accountSubstudies "+
                "AS acctSubstudy WITH acct.id = acctSubstudy.accountId WHERE acct.studyId = :studyId AND "+
                "acct.email=:email GROUP BY acct.id";
        
        // mock hibernate
        HibernateAccount hibernateAccount = makeValidHibernateAccount(true, false);
        // Clear these fields to verify that they are created
        hibernateAccount.setHealthCode(null);
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any()))
                .thenReturn(ImmutableList.of(hibernateAccount));

        // execute and verify - Verify just ID, study, and email, and health code mapping is enough.
        Account account = dao.authenticate(study, PASSWORD_SIGNIN);
        assertEquals(ACCOUNT_ID, account.getId());
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, account.getStudyId());
        assertEquals(EMAIL, account.getEmail());
        assertEquals(HEALTH_CODE, account.getHealthCode());

        // verify query
        verify(mockHibernateHelper).queryGet(expQuery, EMAIL_QUERY_PARAMS, null, null, HibernateAccount.class);
        verifyCreatedHealthCode();
    }
    
    @Test
    public void authenticateSuccessNoReauthentication() throws Exception {
        study.setReauthenticationEnabled(false);
        
        HibernateAccount hibernateAccount = makeValidHibernateAccount(true, true);
        String originalReauthHash = hibernateAccount.getReauthTokenHash();
        
        // mock hibernate
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any()))
                .thenReturn(ImmutableList.of(hibernateAccount));

        // execute and verify - Verify just ID, study, and email, and health code mapping is enough.
        Account account = dao.authenticate(study, PASSWORD_SIGNIN);
        // not incremented by reauthentication
        assertEquals(1, account.getVersion());
        
        // No reauthentication token rotation occurs
        verify(mockHibernateHelper, never()).update(any());
        assertNull(account.getReauthToken());
        assertEquals(originalReauthHash, hibernateAccount.getReauthTokenHash());
    }
    
    @Test
    public void authenticateWithCachedReauthentication() throws Exception {
        study.setReauthenticationEnabled(true);
        
        HibernateAccount hibernateAccount = makeValidHibernateAccount(true, true);
        String originalReauthHash = hibernateAccount.getReauthTokenHash();
        
        // mock hibernate
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any()))
                .thenReturn(ImmutableList.of(hibernateAccount));
        
        CacheKey key = CacheKey.reauthTokenLookupKey(ACCOUNT_ID, TestConstants.TEST_STUDY);
        when(mockCacheProvider.getObject(key, String.class)).thenReturn(REAUTH_TOKEN);
        
        Account account = dao.authenticate(study, PASSWORD_SIGNIN);
        verify(mockCacheProvider).getObject(key, String.class);
        verify(mockHibernateHelper, never()).update(any());
        assertEquals(REAUTH_TOKEN, account.getReauthToken());
        assertEquals(originalReauthHash, hibernateAccount.getReauthTokenHash());
    }

    @Test(expected = EntityNotFoundException.class)
    public void authenticateAccountNotFound() throws Exception {
        // mock hibernate
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any())).thenReturn(ImmutableList.of());

        // execute
        dao.authenticate(study, PASSWORD_SIGNIN);
    }

    @Test(expected = UnauthorizedException.class)
    public void authenticateAccountUnverified() throws Exception {
        // mock hibernate
        HibernateAccount hibernateAccount = makeValidHibernateAccount(true, false);
        hibernateAccount.setStatus(AccountStatus.UNVERIFIED);
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any())).thenReturn(ImmutableList.of(hibernateAccount));

        // execute
        dao.authenticate(study, PASSWORD_SIGNIN);
    }

    @Test(expected = AccountDisabledException.class)
    public void authenticateAccountDisabled() throws Exception {
        // mock hibernate
        HibernateAccount hibernateAccount = makeValidHibernateAccount(true, false);
        hibernateAccount.setStatus(AccountStatus.DISABLED);
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any())).thenReturn(ImmutableList.of(hibernateAccount));

        // execute
        dao.authenticate(study, PASSWORD_SIGNIN);
    }

    @Test(expected = EntityNotFoundException.class)
    public void authenticateAccountHasNoPassword() throws Exception {
        // mock hibernate
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any())).thenReturn(
                ImmutableList.of(makeValidHibernateAccount(false, false)));

        // execute
        dao.authenticate(study, PASSWORD_SIGNIN);
    }

    // branch coverage
    @Test(expected = EntityNotFoundException.class)
    public void authenticateAccountHasPasswordAlgorithmNoHash() throws Exception {
        // mock hibernate
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false, false);
        hibernateAccount.setPasswordAlgorithm(PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM);
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any())).thenReturn(ImmutableList.of(hibernateAccount));

        // execute
        dao.authenticate(study, PASSWORD_SIGNIN);
    }

    @Test(expected = EntityNotFoundException.class)
    public void authenticateBadPassword() throws Exception {
        // mock hibernate
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any())).thenReturn(ImmutableList.of(
                makeValidHibernateAccount(true, false)));

        // execute
        dao.authenticate(study, new SignIn.Builder().withStudy(TestConstants.TEST_STUDY_IDENTIFIER).withEmail(EMAIL)
                .withPassword("wrong password").build());
    }

    @Test
    public void reauthenticateSuccess() throws Exception {
        String expQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.accountSubstudies AS "+
                "acctSubstudy WITH acct.id = acctSubstudy.accountId WHERE acct.studyId = :studyId AND "+
                "acct.email=:email GROUP BY acct.id";
        // mock hibernate
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false, true);
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any())).thenReturn(ImmutableList.of(hibernateAccount));
        String originalReauthTokenHash = hibernateAccount.getReauthTokenHash();

        // execute and verify - Verify just ID, study, and email, and health code mapping is enough.
        Account account = dao.reauthenticate(study, REAUTH_SIGNIN);
        assertEquals(ACCOUNT_ID, account.getId());
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, account.getStudyId());
        assertEquals(EMAIL, account.getEmail());
        assertNotEquals(originalReauthTokenHash, account.getReauthToken());
        // This has been incremented by the reauth token update
        assertEquals(2, account.getVersion());
        
        // verify query
        verify(mockHibernateHelper).queryGet(expQuery, EMAIL_QUERY_PARAMS, null, null, HibernateAccount.class);

        // We update the account with a reauthentication token
        verify(mockHibernateHelper).update(hibernateAccount);
        // The hash has been changed
        assertNotEquals(originalReauthTokenHash, hibernateAccount.getReauthTokenHash());
        // This has been hashed
        assertNotEquals(account.getReauthToken(), hibernateAccount.getReauthTokenHash());
    }
    
    @Test
    public void reauthenticationDisabled() throws Exception {
        study.setReauthenticationEnabled(false);
        
        try {
            dao.reauthenticate(study, REAUTH_SIGNIN);
            fail("Should have thrown exception");
        } catch(UnauthorizedException e) {
            // expected exception
        }
        verify(mockHibernateHelper, never()).queryGet(any(), any(), any(), any(), eq(HibernateAccount.class));
        verify(mockHibernateHelper, never()).update(any());
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void reauthenticateAccountNotFound() throws Exception {
        // mock hibernate
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any())).thenReturn(ImmutableList.of());

        // execute
        dao.reauthenticate(study, REAUTH_SIGNIN);
    }
    
    @Test(expected = UnauthorizedException.class)
    public void reauthenticateAccountUnverified() throws Exception {
        // mock hibernate
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false, true);
        hibernateAccount.setStatus(AccountStatus.UNVERIFIED);
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any())).thenReturn(ImmutableList.of(hibernateAccount));

        // execute
        dao.reauthenticate(study, REAUTH_SIGNIN);
    }
    
    @Test(expected = AccountDisabledException.class)
    public void reauthenticateAccountDisabled() throws Exception {
        // mock hibernate
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false, true);
        hibernateAccount.setStatus(AccountStatus.DISABLED);
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any())).thenReturn(ImmutableList.of(hibernateAccount));

        // execute
        dao.reauthenticate(study, REAUTH_SIGNIN);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void reauthenticateAccountHasNoReauthToken() throws Exception {
        // mock hibernate
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any())).thenReturn(
                ImmutableList.of(makeValidHibernateAccount(false, false)));

        // execute
        dao.reauthenticate(study, REAUTH_SIGNIN);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void failedSignInOfDisabledAccountDoesNotIndicateAccountExists() throws Exception {
        HibernateAccount hibernateAccount = makeValidHibernateAccount(true, false);
        hibernateAccount.setStatus(AccountStatus.DISABLED);
        
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any())).thenReturn(
                ImmutableList.of(hibernateAccount));
        
        SignIn signIn = new SignIn.Builder().withStudy(TestConstants.TEST_STUDY_IDENTIFIER)
                .withEmail(EMAIL).withPassword("bad password").build();
        dao.authenticate(study, signIn);
    }
    
    // branch coverage
    @Test(expected = EntityNotFoundException.class)
    public void reauthenticateAccountHasReauthTokenAlgorithmNoHash() throws Exception {
        // mock hibernate
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false, false);
        hibernateAccount.setReauthTokenAlgorithm(PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM);
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any()))
                .thenReturn(ImmutableList.of(hibernateAccount));

        // execute
        dao.authenticate(study, REAUTH_SIGNIN);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void reauthenticateBadPassword() throws Exception {
        // mock hibernate
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any()))
                .thenReturn(ImmutableList.of(makeValidHibernateAccount(false, true)));

        // execute
        dao.authenticate(study, new SignIn.Builder().withStudy(TestConstants.TEST_STUDY_IDENTIFIER).withEmail(EMAIL)
                .withReauthToken("wrong reauth token").build());
    }

    @Test
    public void getAccountAsAuthenticated() throws Exception {
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false, true);
        String originalReauthTokenHash = hibernateAccount.getReauthTokenHash();
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any()))
                .thenReturn(ImmutableList.of(hibernateAccount));

        Account account = dao.getAccountAfterAuthentication(ACCOUNT_ID_WITH_EMAIL);
        
        String newHash = PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM.generateHash(account.getReauthToken());
        assertNotEquals(originalReauthTokenHash, newHash);
        
        ArgumentCaptor<HibernateAccount> accountCaptor = ArgumentCaptor.forClass(HibernateAccount.class);
        
        verify(mockHibernateHelper).update(accountCaptor.capture());
        
        HibernateAccount captured = accountCaptor.getValue();
        
        assertTrue(PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM.checkHash(captured.getReauthTokenHash(),
                account.getReauthToken()));
    }

    @Test
    public void deleteReauthToken() throws Exception {
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false, false);
        hibernateAccount.setReauthTokenAlgorithm(PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM);
        hibernateAccount.setReauthTokenHash("AAA");
        hibernateAccount.setReauthTokenModifiedOn(DateTime.now());
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any()))
                .thenReturn(ImmutableList.of(hibernateAccount));
        
        ArgumentCaptor<HibernateAccount> accountCaptor = ArgumentCaptor.forClass(HibernateAccount.class);
        
        dao.deleteReauthToken(ACCOUNT_ID_WITH_EMAIL);
        
        verify(mockHibernateHelper).update(accountCaptor.capture());
        HibernateAccount captured = accountCaptor.getValue();
        
        assertNull(captured.getReauthTokenAlgorithm());
        assertNull(captured.getReauthTokenHash());
        assertNull(captured.getReauthTokenModifiedOn());
    }

    @Test
    public void deleteReauthTokenNoToken() throws Exception {
        // Return an account with no reauth token.
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false, false);
        hibernateAccount.setReauthTokenAlgorithm(null);
        hibernateAccount.setReauthTokenHash(null);
        hibernateAccount.setReauthTokenModifiedOn(null);
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any()))
                .thenReturn(ImmutableList.of(hibernateAccount));

        // Just quietly succeeds without doing any work.
        dao.deleteReauthToken(ACCOUNT_ID_WITH_EMAIL);
        verify(mockHibernateHelper, never()).update(any());
    }

    @Test
    public void deleteReauthTokenAccountNotFound() throws Exception {
        // Just quietly succeeds without doing any work.
        dao.deleteReauthToken(ACCOUNT_ID_WITH_EMAIL);
        
        verify(mockHibernateHelper, never()).update(any());
    }

    @Test
    public void getAccountAfterAuthentication() throws Exception {
        DateTime originalTimestamp = DateTime.now().minusMinutes(2);
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false, true);
        hibernateAccount.setReauthTokenAlgorithm(PasswordAlgorithm.BCRYPT);
        hibernateAccount.setReauthTokenHash("AAA");
        hibernateAccount.setReauthTokenModifiedOn(originalTimestamp);
        hibernateAccount.setMigrationVersion(AccountDao.MIGRATION_VERSION);
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any()))
                .thenReturn(ImmutableList.of(hibernateAccount));
        
        ArgumentCaptor<HibernateAccount> accountCaptor = ArgumentCaptor.forClass(HibernateAccount.class);
        
        dao.getAccountAfterAuthentication(ACCOUNT_ID_WITH_EMAIL);
        
        verify(mockHibernateHelper).update(accountCaptor.capture());
        HibernateAccount captured = accountCaptor.getValue();
        
        assertNotEquals(PasswordAlgorithm.BCRYPT, captured.getReauthTokenAlgorithm());
        assertNotEquals("AAA", captured.getReauthTokenHash());
        assertNotEquals(originalTimestamp, captured.getReauthTokenModifiedOn());
        // version has been incremented because reauth token was rotated
        assertEquals(2, captured.getVersion()); 
    }
    
    @Test
    public void getAccountAfterAuthenticateReturnsNull() throws Exception {
        Account account = dao.getAccountAfterAuthentication(ACCOUNT_ID_WITH_EMAIL);
        assertNull(account);
    }
    
    @Test
    public void constructAccount() throws Exception {
        // execute and validate
        Account account = dao.constructAccount(study, EMAIL, PHONE, EXTERNAL_ID, DUMMY_PASSWORD);
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, account.getStudyId());
        assertEquals(EMAIL, account.getEmail());
        assertEquals(PHONE.getNationalFormat(), account.getPhone().getNationalFormat());
        assertEquals(Boolean.FALSE, account.getEmailVerified());
        assertEquals(Boolean.FALSE, account.getPhoneVerified());
        // These are the same because we've mocked the GUID-creation method to always return
        // this value.
        assertEquals(HEALTH_CODE, account.getHealthCode());
        assertEquals(HEALTH_CODE, account.getId());
        assertEquals(EXTERNAL_ID, account.getExternalId());
        assertEquals(PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM, account.getPasswordAlgorithm());

        // validate password hash
        assertTrue(PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM.checkHash(account.getPasswordHash(), DUMMY_PASSWORD));
    }
    
    @Test
    public void constructAccountWithoutPasswordWorks() throws Exception {
        // execute and validate
        Account account = dao.constructAccount(study, EMAIL, PHONE, EXTERNAL_ID, null);
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, account.getStudyId());
        assertEquals(EMAIL, account.getEmail());
        assertEquals(PHONE.getNationalFormat(), account.getPhone().getNationalFormat());
        assertEquals(Boolean.FALSE, account.getEmailVerified());
        assertEquals(Boolean.FALSE, account.getPhoneVerified());
        assertEquals(HEALTH_CODE, account.getHealthCode());
        assertEquals(EXTERNAL_ID, account.getExternalId());
        assertNull(account.getPasswordHash());
        assertNull(account.getPasswordAlgorithm());
    }

    @Test
    public void createAccountSuccess() {
        // Study passed into createAccount() takes precedence over StudyId in the Account object. To test this, make
        // the account have a different study.
        Account account = makeValidGenericAccount();
        account.setStatus(AccountStatus.ENABLED);
        account.setStudyId("wrong-study");
        account.setId(ACCOUNT_ID);

        // execute - We generate a new account ID.
        dao.createAccount(study, account);

        // verify hibernate call
        ArgumentCaptor<HibernateAccount> createdHibernateAccountCaptor = ArgumentCaptor.forClass(
                HibernateAccount.class);
        verify(mockHibernateHelper).create(createdHibernateAccountCaptor.capture());

        HibernateAccount createdHibernateAccount = createdHibernateAccountCaptor.getValue();
        assertEquals(ACCOUNT_ID, createdHibernateAccount.getId());
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, createdHibernateAccount.getStudyId());
        assertEquals(MOCK_DATETIME.getMillis(), createdHibernateAccount.getCreatedOn().getMillis());
        assertEquals(MOCK_DATETIME.getMillis(), createdHibernateAccount.getModifiedOn().getMillis());
        assertEquals(MOCK_DATETIME.getMillis(), createdHibernateAccount.getPasswordModifiedOn().getMillis());
        assertEquals(AccountStatus.ENABLED, createdHibernateAccount.getStatus());
        assertEquals(AccountDao.MIGRATION_VERSION, createdHibernateAccount.getMigrationVersion());
    }
    
    @Test
    public void updateSuccess() {
        // Some fields can't be modified. Create the persisted account and set the base fields so we can verify they
        // weren't modified.
        HibernateAccount persistedAccount = new HibernateAccount();
        persistedAccount.setStudyId("persisted-study");
        persistedAccount.setEmail("persisted@example.com");
        persistedAccount.setCreatedOn(new DateTime(1234L));
        persistedAccount.setPasswordModifiedOn(new DateTime(5678L));
        persistedAccount.setPhone(PHONE);
        persistedAccount.setEmailVerified(Boolean.TRUE);
        persistedAccount.setPhoneVerified(Boolean.TRUE);

        // Set a dummy modifiedOn to make sure we're overwriting it.
        persistedAccount.setModifiedOn(new DateTime(5678L));

        // mock hibernate
        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(persistedAccount);

        Account account = makeValidGenericAccount();
        account.setEmail(OTHER_EMAIL);
        account.setPhone(OTHER_PHONE);
        account.setEmailVerified(Boolean.FALSE);
        account.setPhoneVerified(Boolean.FALSE);
        account.setExternalId(EXTERNAL_ID);
        
        // Execute. Identifiers not allows to change.
        dao.updateAccount(account);

        // verify hibernate update
        ArgumentCaptor<HibernateAccount> updatedHibernateAccountCaptor = ArgumentCaptor.forClass(
                HibernateAccount.class);
        verify(mockHibernateHelper).update(updatedHibernateAccountCaptor.capture());

        HibernateAccount updatedHibernateAccount = updatedHibernateAccountCaptor.getValue();
        assertEquals(ACCOUNT_ID, updatedHibernateAccount.getId());
        assertEquals("persisted-study", updatedHibernateAccount.getStudyId());
        assertEquals(OTHER_EMAIL, updatedHibernateAccount.getEmail());
        assertEquals(OTHER_PHONE.getNationalFormat(),
                updatedHibernateAccount.getPhone().getNationalFormat());
        assertEquals(Boolean.FALSE, updatedHibernateAccount.getEmailVerified());
        assertEquals(Boolean.FALSE, updatedHibernateAccount.getPhoneVerified());
        assertEquals(1234, updatedHibernateAccount.getCreatedOn().getMillis());
        assertEquals(5678, updatedHibernateAccount.getPasswordModifiedOn().getMillis());
        assertEquals(MOCK_DATETIME.getMillis(), updatedHibernateAccount.getModifiedOn().getMillis());
        assertEquals(EXTERNAL_ID, updatedHibernateAccount.getExternalId());
    }
    
    @Test
    public void updateDoesNotChangePasswordOrReauthToken() throws Exception {
        HibernateAccount persistedAccount = makeValidHibernateAccount(true, true);
        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(persistedAccount);
        
        Account account = Account.create();
        account.setId(ACCOUNT_ID);
        account.setPasswordAlgorithm(PasswordAlgorithm.STORMPATH_HMAC_SHA_256);
        account.setPasswordHash("bad password hash");
        account.setPasswordModifiedOn(MOCK_DATETIME);
        account.setReauthTokenAlgorithm(PasswordAlgorithm.STORMPATH_HMAC_SHA_256);
        account.setReauthTokenHash("bad reauth token hash");
        account.setReauthTokenModifiedOn(MOCK_DATETIME);
        
        dao.updateAccount(account);
        
        ArgumentCaptor<HibernateAccount> updatedHibernateAccountCaptor = ArgumentCaptor.forClass(
                HibernateAccount.class);

        verify(mockHibernateHelper).update(updatedHibernateAccountCaptor.capture());
        
        // These values were loaded, have not been changed, and were persisted as is.
        HibernateAccount captured = updatedHibernateAccountCaptor.getValue();
        assertEquals(persistedAccount.getPasswordAlgorithm(), captured.getPasswordAlgorithm());
        assertEquals(persistedAccount.getPasswordHash(), captured.getPasswordHash());
        assertEquals(persistedAccount.getPasswordModifiedOn(), captured.getPasswordModifiedOn());
        assertEquals(persistedAccount.getReauthTokenAlgorithm(), captured.getReauthTokenAlgorithm());
        assertEquals(persistedAccount.getReauthTokenHash(), captured.getReauthTokenHash());
        assertEquals(persistedAccount.getReauthTokenModifiedOn(), captured.getReauthTokenModifiedOn());
    }
    
    @Test
    public void updateAccountNotFound() {
        // mock hibernate
        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(null);

        // execute
        try {
            dao.updateAccount(makeValidGenericAccount());
            fail("expected exception");
        } catch (EntityNotFoundException ex) {
            assertEquals("Account " + ACCOUNT_ID + " not found", ex.getMessage());
        }
    }
    
    @Test
    public void updateAccountAllowsIdentifierUpdate() {
        // This call will allow identifiers/verification status to be updated.
        HibernateAccount persistedAccount = new HibernateAccount();
        persistedAccount.setStudyId("persisted-study");
        persistedAccount.setEmail("persisted@example.com");
        persistedAccount.setCreatedOn(new DateTime(1234L));
        persistedAccount.setPasswordModifiedOn(new DateTime(5678L));
        persistedAccount.setPhone(PHONE);
        persistedAccount.setEmailVerified(Boolean.TRUE);
        persistedAccount.setPhoneVerified(Boolean.TRUE);
        persistedAccount.setExternalId("some-other-extid");

        // Set a dummy modifiedOn to make sure we're overwriting it.
        persistedAccount.setModifiedOn(new DateTime(5678L));

        // mock hibernate
        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(persistedAccount);

        // execute
        Account account = makeValidGenericAccount();
        account.setEmail(OTHER_EMAIL);
        account.setPhone(OTHER_PHONE);
        account.setEmailVerified(Boolean.FALSE);
        account.setPhoneVerified(Boolean.FALSE);
        account.setExternalId(EXTERNAL_ID);
        
        // Identifiers ARE allowed to change here.
        dao.updateAccount(account);

        // Capture the update
        ArgumentCaptor<HibernateAccount> updatedHibernateAccountCaptor = ArgumentCaptor.forClass(
                HibernateAccount.class);
        verify(mockHibernateHelper).update(updatedHibernateAccountCaptor.capture());

        HibernateAccount updatedHibernateAccount = updatedHibernateAccountCaptor.getValue();
        
        assertEquals(OTHER_EMAIL, updatedHibernateAccount.getEmail());
        assertEquals(OTHER_PHONE.getNationalFormat(),
                updatedHibernateAccount.getPhone().getNationalFormat());
        assertEquals(Boolean.FALSE, updatedHibernateAccount.getEmailVerified());
        assertEquals(Boolean.FALSE, updatedHibernateAccount.getPhoneVerified());
        assertEquals(EXTERNAL_ID, updatedHibernateAccount.getExternalId());
    }

    @Test
    public void getByIdSuccessWithHealthCode() throws Exception {
        // mock hibernate
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false, false);
        hibernateAccount.setHealthCode("original-" + HEALTH_CODE);
        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(hibernateAccount);

        // execute and validate - just validate ID, study, and email, and health code mapping
        Account account = (Account) dao.getAccount(ACCOUNT_ID_WITH_ID);
        assertEquals(ACCOUNT_ID, account.getId());
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, account.getStudyId());
        assertEquals(EMAIL, account.getEmail());
        assertEquals("original-" + HEALTH_CODE, account.getHealthCode());
        verify(mockHibernateHelper, never()).update(any());
    }

    @Test
    public void getByIdSuccessCreateNewHealthCode() throws Exception {
        // mock hibernate
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false, false);
        // Clear these fields to verify that they are created
        hibernateAccount.setHealthCode(null);
        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(
                hibernateAccount);

        // execute and validate - just validate ID, study, and email, and health code mapping
        Account account = dao.getAccount(ACCOUNT_ID_WITH_ID);
        assertEquals(ACCOUNT_ID, account.getId());
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, account.getStudyId());
        assertEquals(EMAIL, account.getEmail());
        assertEquals(HEALTH_CODE, account.getHealthCode());

        // Verify we create the new health code mapping
        verifyCreatedHealthCode();
    }

    @Test
    public void getByIdNotFound() {
        // mock hibernate
        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(null);

        // execute and validate
        Account account = dao.getAccount(ACCOUNT_ID_WITH_ID);
        assertNull(account);
    }

    @Test
    public void getByEmailSuccessWithHealthCode() throws Exception {
        String expQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.accountSubstudies "+
                "AS acctSubstudy WITH acct.id = acctSubstudy.accountId WHERE acct.studyId = :studyId AND "+
                "acct.email=:email GROUP BY acct.id";
        
        // mock hibernate
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false, false);
        hibernateAccount.setHealthCode("original-" + HEALTH_CODE);
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any())).thenReturn(ImmutableList.of(hibernateAccount));

        // execute and validate - just validate ID, study, and email, and health code mapping
        Account account = dao.getAccount(ACCOUNT_ID_WITH_EMAIL);
        assertEquals(ACCOUNT_ID, account.getId());
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, account.getStudyId());
        assertEquals(EMAIL, account.getEmail());
        assertEquals("original-" + HEALTH_CODE, account.getHealthCode());

        // verify hibernate query
        verify(mockHibernateHelper).queryGet(expQuery, EMAIL_QUERY_PARAMS, null, null, HibernateAccount.class);

        // We don't create a new health code mapping nor update the account.
        verify(mockHibernateHelper, never()).update(any());
    }

    @Test
    public void getByEmailSuccessCreateNewHealthCode() throws Exception {
        String expQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN "+
                "acct.accountSubstudies AS acctSubstudy WITH acct.id = acctSubstudy.accountId "+
                "WHERE acct.studyId = :studyId AND acct.email=:email GROUP BY acct.id";
        
        // mock hibernate
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false, false);
        // Clear these fields to verify that they are created
        hibernateAccount.setHealthCode(null);
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any()))
                .thenReturn(ImmutableList.of(hibernateAccount));

        // execute and validate - just validate ID, study, and email, and health code mapping
        Account account = dao.getAccount(ACCOUNT_ID_WITH_EMAIL);
        assertEquals(ACCOUNT_ID, account.getId());
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, account.getStudyId());
        assertEquals(EMAIL, account.getEmail());
        assertEquals(HEALTH_CODE, account.getHealthCode());

        // verify hibernate query
        verify(mockHibernateHelper).queryGet(expQuery, EMAIL_QUERY_PARAMS, null, null, HibernateAccount.class);

        // Verify we create the new health code mapping
        verifyCreatedHealthCode();
    }

    @Test
    public void getByEmailNotFound() {
        // mock hibernate
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any())).thenReturn(ImmutableList.of());

        // execute and validate
        Account account = dao.getAccount(ACCOUNT_ID_WITH_EMAIL);
        assertNull(account);
    }
    
    @Test
    public void getByPhone() throws Exception {
        String expQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.accountSubstudies "+
                "AS acctSubstudy WITH acct.id = acctSubstudy.accountId WHERE acct.studyId = :studyId AND "+
                "acct.phone.number=:number AND acct.phone.regionCode=:regionCode GROUP BY acct.id";
        
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false, false);
        // mock hibernate
        when(mockHibernateHelper.queryGet(expQuery, PHONE_QUERY_PARAMS, null, null, HibernateAccount.class))
                .thenReturn(ImmutableList.of(hibernateAccount));

        // execute and validate
        Account account = dao.getAccount(ACCOUNT_ID_WITH_PHONE);
        assertEquals(hibernateAccount.getEmail(), account.getEmail());
    }
    
    @Test
    public void getByPhoneNotFound() {
        when(mockHibernateHelper.queryGet(
                "from HibernateAccount where studyId=:studyId and phone.number=:number and phone.regionCode=:regionCode",
                PHONE_QUERY_PARAMS, null, null, HibernateAccount.class)).thenReturn(ImmutableList.of());
        
        Account account = dao.getAccount(ACCOUNT_ID_WITH_PHONE);
        assertNull(account);
    }

    @Test
    public void getByPhoneAfterAuthentication() throws Exception {
        String expQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN "+
                "acct.accountSubstudies AS acctSubstudy WITH acct.id = "+
                "acctSubstudy.accountId WHERE acct.studyId = :studyId AND "+
                "acct.phone.number=:number AND acct.phone.regionCode=:regionCode GROUP BY acct.id";

        HibernateAccount hibernateAccount = makeValidHibernateAccount(false, false);
        // mock hibernate
        when(mockHibernateHelper.queryGet(expQuery, PHONE_QUERY_PARAMS, 
                null, null, HibernateAccount.class)).thenReturn(ImmutableList.of(hibernateAccount));

        // execute and validate
        Account account = dao.getAccountAfterAuthentication(ACCOUNT_ID_WITH_PHONE);
        assertEquals(hibernateAccount.getEmail(), account.getEmail());
    }
    
    @Test
    public void getByPhoneNotFoundAfterAuthentication() {
        when(mockHibernateHelper.queryGet(
                "from HibernateAccount where studyId=:studyId and phone.number=:number and phone.regionCode=:regionCode",
                PHONE_QUERY_PARAMS, null, null, HibernateAccount.class)).thenReturn(ImmutableList.of());
        
        Account account = dao.getAccountAfterAuthentication(ACCOUNT_ID_WITH_PHONE);
        assertNull(account);
    }
    
    // ACCOUNT_ID_WITH_HEALTHCODE
    @Test
    public void getByHealthCode() throws Exception {
        String expQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.accountSubstudies AS "+
                "acctSubstudy WITH acct.id = acctSubstudy.accountId WHERE acct.studyId = :studyId AND "+
                "acct.healthCode=:healthCode GROUP BY acct.id";
        
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false, false);
        // mock hibernate
        when(mockHibernateHelper.queryGet(expQuery, HEALTHCODE_QUERY_PARAMS, null, null, HibernateAccount.class))
                .thenReturn(ImmutableList.of(hibernateAccount));
        
        // execute and validate
        Account account = dao.getAccount(ACCOUNT_ID_WITH_HEALTHCODE);
        assertEquals(hibernateAccount.getEmail(), account.getEmail());
    }
    
    @Test
    public void getByHealthCodeNotFound() {
        when(mockHibernateHelper.queryGet("from HibernateAccount where studyId=:studyId and healthCode=:healthCode",
                HEALTHCODE_QUERY_PARAMS, null, null, HibernateAccount.class)).thenReturn(ImmutableList.of());
        
        Account account = dao.getAccount(ACCOUNT_ID_WITH_HEALTHCODE);
        assertNull(account);
    }

    @Test
    public void getByHealthCodeAfterAuthentication() throws Exception {
        String expQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.accountSubstudies AS "+
                "acctSubstudy WITH acct.id = acctSubstudy.accountId WHERE acct.studyId = :studyId AND "+
                "acct.healthCode=:healthCode GROUP BY acct.id";
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false, false);
        // mock hibernate
        when(mockHibernateHelper.queryGet(expQuery, HEALTHCODE_QUERY_PARAMS, null, null, HibernateAccount.class))
                .thenReturn(ImmutableList.of(hibernateAccount));
        
        // execute and validate
        Account account = dao.getAccountAfterAuthentication(ACCOUNT_ID_WITH_HEALTHCODE);
        assertEquals(hibernateAccount.getEmail(), account.getEmail());
    }
    
    @Test
    public void getByHealthCodeNotFoundAfterAuthentication() {
        when(mockHibernateHelper.queryGet("from HibernateAccount where studyId=:studyId and healthCode=:healthCode",
                HEALTHCODE_QUERY_PARAMS, null, null, HibernateAccount.class)).thenReturn(ImmutableList.of());
        
        Account account = dao.getAccountAfterAuthentication(ACCOUNT_ID_WITH_HEALTHCODE);
        assertNull(account);
    }    
    
    // ACCOUNT_ID_WITH_EXTID
    @Test
    public void getByExternalId() throws Exception {
        String expQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.accountSubstudies AS "+
                "acctSubstudy WITH acct.id = acctSubstudy.accountId WHERE acct.studyId = :studyId AND "+
                "acct.externalId=:externalId GROUP BY acct.id";
        
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false, false);
        // mock hibernate
        when(mockHibernateHelper.queryGet(expQuery,
                EXTID_QUERY_PARAMS, null, null, HibernateAccount.class)).thenReturn(ImmutableList.of(hibernateAccount));

        // execute and validate
        Account account = dao.getAccount(ACCOUNT_ID_WITH_EXTID);
        assertEquals(hibernateAccount.getEmail(), account.getEmail());
    }
    
    @Test
    public void getByExternalIdNotFound() {
        when(mockHibernateHelper.queryGet("from HibernateAccount where studyId=:studyId and externalId=:externalId",
                EXTID_QUERY_PARAMS, null, null, HibernateAccount.class)).thenReturn(ImmutableList.of());
        
        Account account = dao.getAccount(ACCOUNT_ID_WITH_EXTID);
        assertNull(account);
    }

    @Test
    public void getByExternalIdAfterAuthentication() throws Exception {
        String expQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.accountSubstudies "+
                "AS acctSubstudy WITH acct.id = acctSubstudy.accountId WHERE acct.studyId = :studyId AND "+
                "acct.externalId=:externalId GROUP BY acct.id";
        
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false, false);
        // mock hibernate
        when(mockHibernateHelper.queryGet(expQuery, EXTID_QUERY_PARAMS, null, null, HibernateAccount.class))
                .thenReturn(ImmutableList.of(hibernateAccount));

        // execute and validate
        Account account = dao.getAccountAfterAuthentication(ACCOUNT_ID_WITH_EXTID);
        assertEquals(hibernateAccount.getEmail(), account.getEmail());
    }
    
    @Test
    public void getByExternalIdNotFoundAfterAuthentication() {
        when(mockHibernateHelper.queryGet("from HibernateAccount where studyId=:studyId and externalId=:externalId",
                EXTID_QUERY_PARAMS, null, null, HibernateAccount.class)).thenReturn(ImmutableList.of());
        
        Account account = dao.getAccountAfterAuthentication(ACCOUNT_ID_WITH_EXTID);
        assertNull(account);
    }    
    
    @Test
    public void deleteWithoutId() throws Exception {
        // Can't use email, so it will do a lookup of the account
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false, false);
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any())).thenReturn(ImmutableList.of(hibernateAccount));
        
        dao.deleteAccount(ACCOUNT_ID_WITH_EMAIL);
        
        verify(mockHibernateHelper).deleteById(HibernateAccount.class, ACCOUNT_ID);
    }
    
    @Test
    public void deleteWithId() throws Exception {
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false, false);
        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(hibernateAccount);
        
        // Directly deletes with the ID it has
        dao.deleteAccount(ACCOUNT_ID_WITH_ID);
        
        verify(mockHibernateHelper).deleteById(HibernateAccount.class, ACCOUNT_ID);
    }

    @Test
    public void getPaged() throws Exception {
        String expQuery = "SELECT new HibernateAccount(acct.createdOn, acct.studyId, "+
                "acct.firstName, acct.lastName, acct.email, acct.phone, acct.externalId, "+
                "acct.id, acct.status) FROM HibernateAccount AS acct LEFT JOIN "+
                "acct.accountSubstudies AS acctSubstudy WITH acct.id = acctSubstudy.accountId "+
                "WHERE acct.studyId = :studyId GROUP BY acct.id";
        
        String expCountQuery = "SELECT COUNT(DISTINCT acct.id) FROM HibernateAccount AS acct "+
                "LEFT JOIN acct.accountSubstudies AS acctSubstudy WITH acct.id = "+
                "acctSubstudy.accountId WHERE acct.studyId = :studyId";
        // mock hibernate
        HibernateAccount hibernateAccount1 = makeValidHibernateAccount(false, false);
        hibernateAccount1.setId("account-1");
        hibernateAccount1.setEmail("email1@example.com");

        HibernateAccount hibernateAccount2 = makeValidHibernateAccount(false, false);
        hibernateAccount2.setId("account-2");
        hibernateAccount2.setEmail("email2@example.com");

        when(mockHibernateHelper.queryGet(eq(expQuery), any(), any(), any(), any())).thenReturn(ImmutableList.of(hibernateAccount1,
                hibernateAccount2));
        when(mockHibernateHelper.queryCount(eq(expCountQuery), any())).thenReturn(12);

        // execute and validate
        AccountSummarySearch search = new AccountSummarySearch.Builder().withOffsetBy(10).withPageSize(5).build();
        
        PagedResourceList<AccountSummary> accountSummaryResourceList = dao.getPagedAccountSummaries(study, search);
        assertEquals(10, accountSummaryResourceList.getRequestParams().get("offsetBy"));
        assertEquals(5, accountSummaryResourceList.getRequestParams().get("pageSize"));
        assertEquals((Integer)12, accountSummaryResourceList.getTotal());

        Map<String, Object> paramsMap = accountSummaryResourceList.getRequestParams();
        assertEquals(10, paramsMap.get("offsetBy"));
        assertEquals(5, paramsMap.get("pageSize"));

        // just ID, study, and email is sufficient
        List<AccountSummary> accountSummaryList = accountSummaryResourceList.getItems();
        assertEquals(2, accountSummaryList.size());

        assertEquals("account-1", accountSummaryList.get(0).getId());
        assertEquals(TestConstants.TEST_STUDY, accountSummaryList.get(0).getStudyIdentifier());
        assertEquals("email1@example.com", accountSummaryList.get(0).getEmail());

        assertEquals("account-2", accountSummaryList.get(1).getId());
        assertEquals(TestConstants.TEST_STUDY, accountSummaryList.get(1).getStudyIdentifier());
        assertEquals("email2@example.com", accountSummaryList.get(1).getEmail());

        // verify hibernate calls
        verify(mockHibernateHelper).queryGet(expQuery, STUDY_QUERY_PARAMS, 10, 5, HibernateAccount.class);
        verify(mockHibernateHelper).queryCount(expCountQuery, STUDY_QUERY_PARAMS);
    }

    @Test
    public void getPagedWithOptionalParams() throws Exception {
        String expQuery = "SELECT new HibernateAccount(acct.createdOn, acct.studyId, acct.firstName, "+
                "acct.lastName, acct.email, acct.phone, acct.externalId, acct.id, acct.status) FROM "+
                "HibernateAccount AS acct LEFT JOIN acct.accountSubstudies AS acctSubstudy WITH "+
                "acct.id = acctSubstudy.accountId WHERE acct.studyId = :studyId AND acct.email LIKE "+
                ":email AND acct.phone.number LIKE :number AND acct.createdOn >= :startTime AND acct.createdOn "+
                "<= :endTime AND :language IN ELEMENTS(acct.languages) AND (:IN1 IN elements(acct.dataGroups) "+
                "AND :IN2 IN elements(acct.dataGroups)) AND (:NOTIN1 NOT IN elements(acct.dataGroups) AND "+
                ":NOTIN2 NOT IN elements(acct.dataGroups)) GROUP BY acct.id";
        
        String expCountQuery = "SELECT COUNT(DISTINCT acct.id) FROM HibernateAccount AS acct LEFT JOIN "+
                "acct.accountSubstudies AS acctSubstudy WITH acct.id = acctSubstudy.accountId WHERE "+
                "acct.studyId = :studyId AND acct.email LIKE :email AND acct.phone.number LIKE :number AND "+
                "acct.createdOn >= :startTime AND acct.createdOn <= :endTime AND :language IN "+
                "ELEMENTS(acct.languages) AND (:IN1 IN elements(acct.dataGroups) AND :IN2 IN "+
                "elements(acct.dataGroups)) AND (:NOTIN1 NOT IN elements(acct.dataGroups) AND :NOTIN2 NOT "+
                "IN elements(acct.dataGroups))";
        
        // Setup start and end dates.
        DateTime startDate = DateTime.parse("2017-05-19T11:40:06.247-0700");
        DateTime endDate = DateTime.parse("2017-05-19T18:32:03.434-0700");

        // mock hibernate
        when(mockHibernateHelper.queryGet(eq(expQuery), any(), any(), any(), any())).thenReturn(ImmutableList.of(
                makeValidHibernateAccount(false, false)));
        when(mockHibernateHelper.queryCount(eq(expCountQuery), any())).thenReturn(11);

        // execute and validate - Just validate filters and query, since everything else is tested in getPaged().
        AccountSummarySearch search = new AccountSummarySearch.Builder()
                .withOffsetBy(10)
                .withPageSize(5)
                .withEmailFilter(EMAIL)
                .withPhoneFilter(PHONE.getNationalFormat())
                .withAllOfGroups(Sets.newHashSet("a", "b"))
                .withNoneOfGroups(Sets.newHashSet("c", "d"))
                .withLanguage("de")
                .withStartTime(startDate)
                .withEndTime(endDate).build();
        
        PagedResourceList<AccountSummary> accountSummaryResourceList = dao.getPagedAccountSummaries(study, search);

        Map<String, Object> paramsMap = accountSummaryResourceList.getRequestParams();
        assertEquals(10, paramsMap.size());
        assertEquals(5, paramsMap.get("pageSize"));
        assertEquals(10, paramsMap.get("offsetBy"));
        assertEquals(EMAIL, paramsMap.get("emailFilter"));
        assertEquals(PHONE.getNationalFormat(), paramsMap.get("phoneFilter"));
        assertEquals(startDate.toString(), paramsMap.get("startTime"));
        assertEquals(endDate.toString(), paramsMap.get("endTime"));
        assertEquals(Sets.newHashSet("a","b"), paramsMap.get("allOfGroups"));
        assertEquals(Sets.newHashSet("c","d"), paramsMap.get("noneOfGroups"));
        assertEquals("de", paramsMap.get("language"));
        assertEquals(ResourceList.REQUEST_PARAMS, paramsMap.get(ResourceList.TYPE));

        String phoneString = PHONE.getNationalFormat().replaceAll("\\D*","");

        // verify hibernate calls
        Map<String,Object> params = new HashMap<>();
        params.put("studyId", TestConstants.TEST_STUDY_IDENTIFIER);
        params.put("email", "%"+EMAIL+"%");
        params.put("number", "%"+phoneString+"%");
        params.put("startTime", startDate);
        params.put("endTime", endDate);
        params.put("in1", "a");
        params.put("in2", "b");
        params.put("notin1", "c");
        params.put("notin2", "d");
        params.put("language", "de");
        
        verify(mockHibernateHelper).queryGet(eq(expQuery), paramCaptor.capture(), eq(10), eq(5), eq(HibernateAccount.class));
        verify(mockHibernateHelper).queryCount(eq(expCountQuery), paramCaptor.capture());
        
        Map<String,Object> capturedParams = paramCaptor.getAllValues().get(0);
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, capturedParams.get("studyId"));
        assertEquals("%"+EMAIL+"%", capturedParams.get("email"));
        assertEquals("%"+phoneString+"%", capturedParams.get("number"));
        assertEquals(startDate, capturedParams.get("startTime"));
        assertEquals(endDate, capturedParams.get("endTime"));
        assertEquals("a", capturedParams.get("IN1"));
        assertEquals("b", capturedParams.get("IN2"));
        assertEquals("d", capturedParams.get("NOTIN1"));
        assertEquals("c", capturedParams.get("NOTIN2"));
        assertEquals("de", capturedParams.get("language"));
        
        capturedParams = paramCaptor.getAllValues().get(1);
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, capturedParams.get("studyId"));
        assertEquals("%"+EMAIL+"%", capturedParams.get("email"));
        assertEquals("%"+phoneString+"%", capturedParams.get("number"));
        assertEquals(startDate, capturedParams.get("startTime"));
        assertEquals(endDate, capturedParams.get("endTime"));
        assertEquals("a", capturedParams.get("IN1"));
        assertEquals("b", capturedParams.get("IN2"));
        assertEquals("d", capturedParams.get("NOTIN1"));
        assertEquals("c", capturedParams.get("NOTIN2"));
        assertEquals("de", capturedParams.get("language"));
    }
    
    @Test
    public void getPagedWithOptionalEmptySetParams() throws Exception {
        String expQuery = "SELECT new HibernateAccount(acct.createdOn, acct.studyId, acct.firstName, "+
                "acct.lastName, acct.email, acct.phone, acct.externalId, acct.id, acct.status) FROM "+
                "HibernateAccount AS acct LEFT JOIN acct.accountSubstudies AS acctSubstudy WITH "+
                "acct.id = acctSubstudy.accountId WHERE acct.studyId = :studyId AND acct.email LIKE "+
                ":email AND acct.phone.number LIKE :number AND acct.createdOn >= :startTime AND "+
                "acct.createdOn <= :endTime AND :language IN ELEMENTS(acct.languages) GROUP BY acct.id";
        
        String expCountQuery = "SELECT COUNT(DISTINCT acct.id) FROM HibernateAccount AS acct LEFT JOIN "+
                "acct.accountSubstudies AS acctSubstudy WITH acct.id = acctSubstudy.accountId WHERE "+
                "acct.studyId = :studyId AND acct.email LIKE :email AND acct.phone.number LIKE "+
                ":number AND acct.createdOn >= :startTime AND acct.createdOn <= :endTime AND :language "+
                "IN ELEMENTS(acct.languages)";
        
        // Setup start and end dates.
        DateTime startDate = DateTime.parse("2017-05-19T11:40:06.247-0700");
        DateTime endDate = DateTime.parse("2017-05-19T18:32:03.434-0700");

        // mock hibernate
        when(mockHibernateHelper.queryGet(eq(expQuery), any(), any(), any(), any()))
                .thenReturn(ImmutableList.of(makeValidHibernateAccount(false, false)));
        when(mockHibernateHelper.queryCount(any(), any())).thenReturn(11);

        // execute and validate - Just validate filters and query, since everything else is tested in getPaged().
        AccountSummarySearch search = new AccountSummarySearch.Builder()
                .withOffsetBy(10)
                .withPageSize(5)
                .withEmailFilter(EMAIL)
                .withPhoneFilter(PHONE.getNationalFormat())
                .withLanguage("de")
                .withStartTime(startDate)
                .withEndTime(endDate).build();
        PagedResourceList<AccountSummary> accountSummaryResourceList = dao.getPagedAccountSummaries(study, search);

        Map<String, Object> paramsMap = accountSummaryResourceList.getRequestParams();
        assertEquals(10, paramsMap.size());
        assertEquals(5, paramsMap.get("pageSize"));
        assertEquals(10, paramsMap.get("offsetBy"));
        assertEquals(EMAIL, paramsMap.get("emailFilter"));
        assertEquals(PHONE.getNationalFormat(), paramsMap.get("phoneFilter"));
        assertEquals(startDate.toString(), paramsMap.get("startTime"));
        assertEquals(endDate.toString(), paramsMap.get("endTime"));
        assertEquals(Sets.newHashSet(), paramsMap.get("allOfGroups"));
        assertEquals(Sets.newHashSet(), paramsMap.get("noneOfGroups"));
        assertEquals("de", paramsMap.get("language"));
        assertEquals(ResourceList.REQUEST_PARAMS, paramsMap.get(ResourceList.TYPE));

        String phoneString = PHONE.getNationalFormat().replaceAll("\\D*","");

        // verify hibernate calls
        Map<String,Object> params = new HashMap<>();
        params.put("studyId", TestConstants.TEST_STUDY);
        params.put("email", "%"+EMAIL+"%");
        params.put("number", "%"+phoneString+"%");
        params.put("startTime", startDate);
        params.put("endTime", endDate);
        params.put("language", "de");
        
        verify(mockHibernateHelper).queryGet(eq(expQuery), paramCaptor.capture(), eq(10), eq(5), eq(HibernateAccount.class));
        verify(mockHibernateHelper).queryCount(eq(expCountQuery), paramCaptor.capture());
        
        Map<String,Object> capturedParams = paramCaptor.getAllValues().get(0);
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, capturedParams.get("studyId"));
        assertEquals("%"+EMAIL+"%", capturedParams.get("email"));
        assertEquals("%"+phoneString+"%", capturedParams.get("number"));
        assertEquals(startDate, capturedParams.get("startTime"));
        assertEquals(endDate, capturedParams.get("endTime"));
        assertEquals("de", capturedParams.get("language"));
        
        capturedParams = paramCaptor.getAllValues().get(1);
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, capturedParams.get("studyId"));
        assertEquals("%"+EMAIL+"%", capturedParams.get("email"));
        assertEquals("%"+phoneString+"%", capturedParams.get("number"));
        assertEquals(startDate, capturedParams.get("startTime"));
        assertEquals(endDate, capturedParams.get("endTime"));
        assertEquals("de", capturedParams.get("language"));
    }
    
    @Test
    public void getHealthCode() throws Exception {
        String expQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.accountSubstudies "+
                "AS acctSubstudy WITH acct.id = acctSubstudy.accountId WHERE acct.studyId = :studyId AND "+
                "acct.email=:email GROUP BY acct.id";
        
        // mock hibernate
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false, false);
        hibernateAccount.setHealthCode(HEALTH_CODE);
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any())).thenReturn(ImmutableList.of(hibernateAccount));

        // execute and validate
        String healthCode = dao.getHealthCodeForAccount(ACCOUNT_ID_WITH_EMAIL);
        assertEquals(HEALTH_CODE, healthCode);

        // verify hibernate query
        verify(mockHibernateHelper).queryGet(expQuery, EMAIL_QUERY_PARAMS, null, null, HibernateAccount.class);
    }

    @Test
    public void getHealthCodeNoAccount() {
        // mock hibernate
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any())).thenReturn(ImmutableList.of());

        // execute and validate
        String healthCode = dao.getHealthCodeForAccount(ACCOUNT_ID_WITH_EMAIL);
        assertNull(healthCode);
    }

    @Test
    public void unmarshallAccountSummarySuccess() {
        // Create HibernateAccount. Only fill in values needed for AccountSummary.
        HibernateAccount hibernateAccount = new HibernateAccount();
        hibernateAccount.setId(ACCOUNT_ID);
        hibernateAccount.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        hibernateAccount.setEmail(EMAIL);
        hibernateAccount.setPhone(TestConstants.PHONE);
        hibernateAccount.setExternalId(EXTERNAL_ID);
        hibernateAccount.setFirstName(FIRST_NAME);
        hibernateAccount.setLastName(LAST_NAME);
        hibernateAccount.setCreatedOn(CREATED_ON);
        hibernateAccount.setStatus(AccountStatus.ENABLED);

        // Unmarshall
        AccountSummary accountSummary = dao.unmarshallAccountSummary(hibernateAccount);
        assertEquals(ACCOUNT_ID, accountSummary.getId());
        assertEquals(TestConstants.TEST_STUDY, accountSummary.getStudyIdentifier());
        assertEquals(EMAIL, accountSummary.getEmail());
        assertEquals(TestConstants.PHONE, accountSummary.getPhone());
        assertEquals(EXTERNAL_ID, accountSummary.getExternalId());
        assertEquals(FIRST_NAME, accountSummary.getFirstName());
        assertEquals(LAST_NAME, accountSummary.getLastName());
        assertEquals(AccountStatus.ENABLED, accountSummary.getStatus());

        // createdOn is stored as a long, so just compare epoch milliseconds.
        assertEquals(CREATED_ON.getMillis(), accountSummary.getCreatedOn().getMillis());
    }

    // branch coverage, to make sure nothing crashes.
    @Test
    public void unmarshallAccountSummaryBlankAccount() throws Exception {
        AccountSummary accountSummary = dao.unmarshallAccountSummary(makeValidHibernateAccount(false, false));
        assertNotNull(accountSummary);
    }
    
    @Test
    public void editAccountSuccess() throws Exception {
        String expQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.accountSubstudies AS "+
                "acctSubstudy WITH acct.id = acctSubstudy.accountId WHERE acct.studyId = :studyId AND "+
                "acct.healthCode=:healthCode GROUP BY acct.id";
        
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false, false);
        hibernateAccount.setHealthCode("A");
        // mock hibernate
        when(mockHibernateHelper.queryGet(expQuery,
                HEALTHCODE_QUERY_PARAMS, null, null, HibernateAccount.class))
                        .thenReturn(ImmutableList.of(hibernateAccount));
        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(hibernateAccount);

        // execute and validate
        dao.editAccount(TestConstants.TEST_STUDY, HEALTH_CODE, account -> account.setFirstName("ChangedFirstName"));
        
        ArgumentCaptor<HibernateAccount> updatedAccountCaptor = ArgumentCaptor.forClass(HibernateAccount.class);
        verify(mockHibernateHelper).update(updatedAccountCaptor.capture());
        
        assertEquals("ChangedFirstName", updatedAccountCaptor.getValue().getFirstName());
    }
    
    @Test
    public void editAccountWhenAccountNotFound() throws Exception {
        when(mockHibernateHelper.queryGet("from HibernateAccount where studyId=:studyId and healthCode=:healthCode",
                HEALTHCODE_QUERY_PARAMS, null, null, HibernateAccount.class)).thenReturn(ImmutableList.of());
        
        dao.editAccount(TestConstants.TEST_STUDY, "bad-health-code", account -> account.setEmail("JUNK"));
        
        verify(mockHibernateHelper, never()).update(any());
    }

    @Test
    public void noLanguageQueryCorrect() throws Exception {
        AccountSummarySearch search = new AccountSummarySearch.Builder().build();
        
        QueryBuilder builder = dao.makeQuery(HibernateAccountDao.FULL_QUERY, TestConstants.TEST_STUDY_IDENTIFIER, null, search, false);
        
        String finalQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.accountSubstudies AS acctSubstudy "+
                "WITH acct.id = acctSubstudy.accountId WHERE acct.studyId = :studyId GROUP BY acct.id";
        
        assertEquals(finalQuery, builder.getQuery());
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, builder.getParameters().get("studyId"));
    }

    @Test
    public void languageQueryCorrect() throws Exception {
        AccountSummarySearch search = new AccountSummarySearch.Builder().withLanguage("en").build();
        
        QueryBuilder builder = dao.makeQuery(HibernateAccountDao.FULL_QUERY, TestConstants.TEST_STUDY_IDENTIFIER, null,
                search, false);
        String finalQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.accountSubstudies AS acctSubstudy "+
                "WITH acct.id = acctSubstudy.accountId WHERE acct.studyId = :studyId AND "+
                ":language IN ELEMENTS(acct.languages) GROUP BY acct.id";
        assertEquals(finalQuery, builder.getQuery());
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, builder.getParameters().get("studyId"));
        assertEquals("en", builder.getParameters().get("language"));
    }

    @Test
    public void groupClausesGroupedCorrectly() throws Exception {
        AccountSummarySearch search = new AccountSummarySearch.Builder()
                .withNoneOfGroups(Sets.newHashSet("sdk-int-1"))
                .withAllOfGroups(Sets.newHashSet("group1")).build();
        
        QueryBuilder builder = dao.makeQuery(HibernateAccountDao.FULL_QUERY, TestConstants.TEST_STUDY_IDENTIFIER, 
                null, search, false);
        
        String finalQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.accountSubstudies "+
                "AS acctSubstudy WITH acct.id = acctSubstudy.accountId WHERE acct.studyId = :studyId AND "+
                "(:IN1 IN elements(acct.dataGroups)) AND (:NOTIN1 NOT IN elements(acct.dataGroups)) "+
                "GROUP BY acct.id";
        
        assertEquals(finalQuery, builder.getQuery());
        assertEquals("sdk-int-1", builder.getParameters().get("NOTIN1"));
        assertEquals("group1", builder.getParameters().get("IN1"));
        assertEquals("api", builder.getParameters().get("studyId"));
    }

    @Test
    public void oneAllOfGroupsQueryCorrect() throws Exception {
        AccountSummarySearch search = new AccountSummarySearch.Builder()
                .withAllOfGroups(Sets.newHashSet("group1")).build();
        
        QueryBuilder builder = dao.makeQuery(HibernateAccountDao.FULL_QUERY, TestConstants.TEST_STUDY_IDENTIFIER, 
                null, search, false);
        
        String finalQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.accountSubstudies "+
                "AS acctSubstudy WITH acct.id = acctSubstudy.accountId WHERE acct.studyId = :studyId AND "+
                "(:IN1 IN elements(acct.dataGroups)) GROUP BY acct.id";
        
        assertEquals(finalQuery, builder.getQuery());
        assertEquals("group1", builder.getParameters().get("IN1"));
        assertEquals("api", builder.getParameters().get("studyId"));
    }

    @Test
    public void twoAllOfGroupsQueryCorrect() throws Exception {
        AccountSummarySearch search = new AccountSummarySearch.Builder()
                .withAllOfGroups(Sets.newHashSet("sdk-int-1", "group1")).build();
        
        QueryBuilder builder = dao.makeQuery(HibernateAccountDao.FULL_QUERY, TestConstants.TEST_STUDY_IDENTIFIER, 
                null, search, false);
        
        String finalQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.accountSubstudies "+
                "AS acctSubstudy WITH acct.id = acctSubstudy.accountId WHERE acct.studyId = :studyId AND "+
                "(:IN1 IN elements(acct.dataGroups) AND :IN2 IN elements(acct.dataGroups)) GROUP BY acct.id";
        
        assertEquals(finalQuery, builder.getQuery());
        assertEquals("sdk-int-1", builder.getParameters().get("IN1"));
        assertEquals("group1", builder.getParameters().get("IN2"));
        assertEquals("api", builder.getParameters().get("studyId"));
    }

    @Test
    public void oneNoneOfGroupsQueryCorrect() throws Exception {
        AccountSummarySearch search = new AccountSummarySearch.Builder()
                .withNoneOfGroups(Sets.newHashSet("group1")).build();
        
        QueryBuilder builder = dao.makeQuery(HibernateAccountDao.FULL_QUERY, TestConstants.TEST_STUDY_IDENTIFIER, 
                null, search, false);
        
        String finalQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.accountSubstudies "+
                "AS acctSubstudy WITH acct.id = acctSubstudy.accountId WHERE acct.studyId = :studyId AND "+
                "(:NOTIN1 NOT IN elements(acct.dataGroups)) GROUP BY acct.id";
        
        assertEquals(finalQuery, builder.getQuery());
        assertEquals("group1", builder.getParameters().get("NOTIN1"));
        assertEquals("api", builder.getParameters().get("studyId"));
    }

    @Test
    public void twoNoneOfGroupsQueryCorrect() throws Exception {
        AccountSummarySearch search = new AccountSummarySearch.Builder()
                .withNoneOfGroups(Sets.newHashSet("sdk-int-1", "group1")).build();
        
        QueryBuilder builder = dao.makeQuery(HibernateAccountDao.FULL_QUERY, TestConstants.TEST_STUDY_IDENTIFIER, 
                null, search, false);
        
        String finalQuery = "SELECT acct FROM HibernateAccount AS acct LEFT JOIN acct.accountSubstudies "+
                "AS acctSubstudy WITH acct.id = acctSubstudy.accountId WHERE acct.studyId = :studyId AND "+
                "(:NOTIN1 NOT IN elements(acct.dataGroups) AND :NOTIN2 NOT IN elements(acct.dataGroups)) "+
                "GROUP BY acct.id";
        
        assertEquals(finalQuery, builder.getQuery());
        assertEquals("sdk-int-1", builder.getParameters().get("NOTIN1"));
        assertEquals("group1", builder.getParameters().get("NOTIN2"));
        assertEquals("api", builder.getParameters().get("studyId"));
    }

    @Test(expected = UnauthorizedException.class)
    public void authenticateAccountUnverifiedEmailFails() throws Exception {
        study.setVerifyChannelOnSignInEnabled(true);
        
        HibernateAccount hibernateAccount = makeValidHibernateAccount(true, true);
        hibernateAccount.setEmailVerified(false);
        
        // mock hibernate
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any()))
                .thenReturn(ImmutableList.of(hibernateAccount));

        dao.authenticate(study, PASSWORD_SIGNIN);
    }
    
    @Test(expected = UnauthorizedException.class)
    public void authenticateAccountUnverifiedPhoneFails() throws Exception {
        study.setVerifyChannelOnSignInEnabled(true);
        
        HibernateAccount hibernateAccount = makeValidHibernateAccount(true, true);
        hibernateAccount.setPhoneVerified(null);
        
        // mock hibernate
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any()))
                .thenReturn(ImmutableList.of(hibernateAccount));

        // execute and verify - Verify just ID, study, and email, and health code mapping is enough.
        SignIn phoneSignIn = new SignIn.Builder().withStudy(TestConstants.TEST_STUDY_IDENTIFIER)
                .withPhone(PHONE).withPassword(DUMMY_PASSWORD).build();
        
        dao.authenticate(study, phoneSignIn);
    }
    
    @Test
    public void authenticateAccountEmailUnverifiedWithoutEmailVerificationOK() throws Exception {
        study.setEmailVerificationEnabled(false);
        
        HibernateAccount hibernateAccount = makeValidHibernateAccount(true, true);
        hibernateAccount.setEmailVerified(false);
        
        // mock hibernate
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any()))
                .thenReturn(ImmutableList.of(hibernateAccount));

        dao.authenticate(study, PASSWORD_SIGNIN);
    }
    
    @Test
    public void authenticateAccountUnverifiedEmailSucceedsForLegacy() throws Exception {
        study.setVerifyChannelOnSignInEnabled(false);

        HibernateAccount hibernateAccount = makeValidHibernateAccount(true, true);
        hibernateAccount.setEmailVerified(false);
        
        // mock hibernate
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any()))
                .thenReturn(ImmutableList.of(hibernateAccount));

        dao.authenticate(study, PASSWORD_SIGNIN);
    }

    @Test
    public void authenticateAccountUnverifiedPhoneSucceedsForLegacy() throws Exception {
        study.setVerifyChannelOnSignInEnabled(false);
        
        HibernateAccount hibernateAccount = makeValidHibernateAccount(true, true);
        hibernateAccount.setPhoneVerified(null);
        
        // mock hibernate
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any()))
                .thenReturn(ImmutableList.of(hibernateAccount));

        // execute and verify - Verify just ID, study, and email, and health code mapping is enough.
        SignIn phoneSignIn = new SignIn.Builder().withStudy(TestConstants.TEST_STUDY_IDENTIFIER)
                .withPhone(PHONE).withPassword(DUMMY_PASSWORD).build();
        
        dao.authenticate(study, phoneSignIn);
    }
    
    @Test
    public void deleteAccountFailsAcrossSubstudies() throws Exception {
        testSubstudyMatch(CALLER_SUBSTUDIES, ACCOUNT_SUBSTUDIES, (accountId) -> {
            dao.deleteAccount(accountId);
            verify(mockHibernateHelper, never()).deleteById(any(), any());
        });
    }
    
    @Test
    public void deleteAccountSucceedsOnSubstudyMatch() throws Exception {
        testSubstudyMatch(ImmutableSet.of(SUBSTUDY_A), ACCOUNT_SUBSTUDIES, (accountId) -> {
            dao.deleteAccount(accountId);
            verify(mockHibernateHelper).deleteById(any(), any());
        });
    }
    
    @Test
    public void deleteReauthTokenFailsAcrossSubstudies() throws Exception {
        testSubstudyMatch(CALLER_SUBSTUDIES, ACCOUNT_SUBSTUDIES, (accountId) -> {
            dao.deleteReauthToken(accountId);
            verify(mockHibernateHelper, never()).update(any());
        });
    }

    @Test
    public void deleteReauthTokenSucceedsOnSubstudyMatch() throws Exception {
        testSubstudyMatch(ImmutableSet.of(SUBSTUDY_A), ACCOUNT_SUBSTUDIES, (accountId) -> {
            dao.deleteReauthToken(accountId);
            verify(mockHibernateHelper).update(any());
        });
    }
    
    @Test
    public void editAccountFailsAcrossSubstudies() throws Exception { 
        BridgeUtils.setRequestContext(
                new RequestContext.Builder().withCallerSubstudies(CALLER_SUBSTUDIES).build());        
        
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false, false);
        hibernateAccount.setAccountSubstudies(ACCOUNT_SUBSTUDIES);
        when(mockHibernateHelper.getById(any(), any())).thenReturn(hibernateAccount);

        dao.editAccount(TestConstants.TEST_STUDY, HEALTH_CODE, (account) -> {});
        
        verify(mockHibernateHelper, never()).update(any());
        BridgeUtils.setRequestContext(null);
    }
    
    @Test
    public void getAccountFailsAcrossSubstudies() throws Exception {
        testSubstudyMatch(CALLER_SUBSTUDIES, ACCOUNT_SUBSTUDIES, (accountId) -> {
            Account account = dao.getAccount(accountId);
            assertNull(account);
        });
    }
    
    @Test
    public void getAccountSucceedsOnSubstudyMatch() throws Exception {
        testSubstudyMatch(ImmutableSet.of(SUBSTUDY_A), ACCOUNT_SUBSTUDIES, (accountId) -> {
            Account account = dao.getAccount(accountId);
            assertNotNull(account);
        });
    }
    
    @Test
    public void getAccountAfterAuthenticationFailsAcrossSubstudies() throws Exception { 
        testSubstudyMatch(CALLER_SUBSTUDIES, ACCOUNT_SUBSTUDIES, (accountId) -> {
            Account account = dao.getAccountAfterAuthentication(accountId);
            assertNull(account);
        });
    }
    
    @Test
    public void getAccountAfterAuthenticationSucceedsOnSubstudyMatch() throws Exception { 
        testSubstudyMatch(ImmutableSet.of(SUBSTUDY_A), ACCOUNT_SUBSTUDIES, (accountId) -> {
            Account account = dao.getAccountAfterAuthentication(accountId);
            assertNotNull(account);
        });
    }
    
    private void testSubstudyMatch(Set<String> callerSubstudies, Set<AccountSubstudy> accountSubstudies,
            Consumer<AccountId> supplier) throws Exception {
        BridgeUtils.setRequestContext(
                new RequestContext.Builder().withCallerSubstudies(callerSubstudies).build());        
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false, true);
        hibernateAccount.setAccountSubstudies(accountSubstudies);
        when(mockHibernateHelper.getById(any(), any())).thenReturn(hibernateAccount);

        AccountId accountId = AccountId.forId(TestConstants.TEST_STUDY_IDENTIFIER, ACCOUNT_ID);
        supplier.accept(accountId);
        
        BridgeUtils.setRequestContext(null);
    }
    
    private void verifyCreatedHealthCode() {
        ArgumentCaptor<HibernateAccount> updatedAccountCaptor = ArgumentCaptor.forClass(HibernateAccount.class);
        verify(mockHibernateHelper).update(updatedAccountCaptor.capture());

        HibernateAccount updatedAccount = updatedAccountCaptor.getValue();
        assertEquals(ACCOUNT_ID, updatedAccount.getId());
        assertEquals(HEALTH_CODE, updatedAccount.getHealthCode());
        assertEquals(MOCK_DATETIME.getMillis(), updatedAccount.getModifiedOn().getMillis());
    }

    // Create minimal generic account for everything that will be used by HibernateAccountDao.
    private static Account makeValidGenericAccount() {
        Account genericAccount = Account.create();
        genericAccount.setId(ACCOUNT_ID);
        genericAccount.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        genericAccount.setEmail(EMAIL);
        genericAccount.setStatus(AccountStatus.UNVERIFIED);
        return genericAccount;
    }

    // Create minimal Hibernate account for everything that will be used by HibernateAccountDao.
    private static HibernateAccount makeValidHibernateAccount(boolean generatePasswordHash, boolean generateReauthHash) throws Exception {
        HibernateAccount hibernateAccount = new HibernateAccount();
        hibernateAccount.setId(ACCOUNT_ID);
        hibernateAccount.setHealthCode(HEALTH_CODE);
        hibernateAccount.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        hibernateAccount.setPhone(TestConstants.PHONE);
        hibernateAccount.setPhoneVerified(true);
        hibernateAccount.setExternalId(EXTERNAL_ID);
        hibernateAccount.setEmail(EMAIL);
        hibernateAccount.setEmailVerified(true);
        hibernateAccount.setStatus(AccountStatus.ENABLED);
        hibernateAccount.setMigrationVersion(AccountDao.MIGRATION_VERSION);
        hibernateAccount.setVersion(1);
        
        if (generatePasswordHash) {
            // Password hashes are expensive to generate. Only generate them if the test actually needs them.
            hibernateAccount.setPasswordAlgorithm(PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM);
            hibernateAccount.setPasswordHash(PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM.generateHash(
                    DUMMY_PASSWORD));
        }
        if (generateReauthHash) {
            // Hashes are expensive to generate. Only generate them if the test actually needs them.
            hibernateAccount.setReauthTokenAlgorithm(PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM);
            hibernateAccount
                    .setReauthTokenHash(PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM.generateHash(REAUTH_TOKEN));
        }
        return hibernateAccount;
    }
}
