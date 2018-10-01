package org.sagebionetworks.bridge.hibernate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.persistence.PersistenceException;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import org.hibernate.exception.ConstraintViolationException;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeZone;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.runners.MockitoJUnitRunner;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.exceptions.AccountDisabledException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.AccountSummarySearch;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.AccountStatus;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.GenericAccount;
import org.sagebionetworks.bridge.models.accounts.HealthIdImpl;
import org.sagebionetworks.bridge.models.accounts.PasswordAlgorithm;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.AuthenticationService;
import org.sagebionetworks.bridge.services.AuthenticationService.ChannelType;
import org.sagebionetworks.bridge.services.HealthCodeService;

@RunWith(MockitoJUnitRunner.class)
public class HibernateAccountDaoTest {
    private static final String ACCOUNT_ID = "account-id";
    private static final DateTime CREATED_ON = DateTime.parse("2017-05-19T11:03:50.224-0700");
    private static final String DUMMY_PASSWORD = "Aa!Aa!Aa!Aa!1";
    private static final String DUMMY_PASSWORD_HASH = "dummy-password-hash";
    private static final String DUMMY_REAUTH_TOKEN_HASH = "dummy-reauth-token-hash";
    private static final String EMAIL = "eggplant@example.com";
    private static final Phone PHONE = TestConstants.PHONE;
    private static final Phone OTHER_PHONE = TestConstants.OTHER_PHONE;
    private static final String OTHER_EMAIL = "other-email@example.com";
    private static final String HEALTH_CODE = "health-code";
    private static final String HEALTH_ID = "health-id";
    private static final long MOCK_NOW_MILLIS = DateTime.parse("2017-05-19T14:45:27.593-0700").getMillis();
    private static final String FIRST_NAME = "Eggplant";
    private static final String LAST_NAME = "McTester";
    private static final String REAUTH_TOKEN = "reauth-token";
    private static final String EXTERNAL_ID = "an-external-id";
    private static final int VERSION = 7;
    private static final AccountId ACCOUNT_ID_WITH_ID = AccountId.forId(TestConstants.TEST_STUDY_IDENTIFIER, ACCOUNT_ID);
    private static final AccountId ACCOUNT_ID_WITH_EMAIL = AccountId.forEmail(TestConstants.TEST_STUDY_IDENTIFIER, EMAIL);
    private static final AccountId ACCOUNT_ID_WITH_PHONE = AccountId.forPhone(TestConstants.TEST_STUDY_IDENTIFIER, TestConstants.PHONE);
    private static final AccountId ACCOUNT_ID_WITH_HEALTHCODE = AccountId.forHealthCode(TestConstants.TEST_STUDY_IDENTIFIER, HEALTH_CODE);
    private static final AccountId ACCOUNT_ID_WITH_EXTID = AccountId.forExternalId(TestConstants.TEST_STUDY_IDENTIFIER, EXTERNAL_ID);
    
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
    private HealthCodeService mockHealthCodeService;
    private HibernateAccountDao dao;
    private HibernateHelper mockHibernateHelper;
    private CacheProvider mockCacheProvider;

    @BeforeClass
    public static void mockNow() {
        DateTimeUtils.setCurrentMillisFixed(MOCK_NOW_MILLIS);
    }

    @AfterClass
    public static void unmockNow() {
        DateTimeUtils.setCurrentMillisSystem();
    }

    @Before
    public void before() {
        mockHealthCodeService = mock(HealthCodeService.class);
        mockHibernateHelper = mock(HibernateHelper.class);
        mockCacheProvider = mock(CacheProvider.class);
        
        // Mock successful update.
        when(mockHibernateHelper.update(any())).thenAnswer(invocation -> {
            HibernateAccount account = invocation.getArgumentAt(0, HibernateAccount.class);
            account.setVersion(account.getVersion()+1);
            return account;
        });
        
        dao = new HibernateAccountDao();
        dao.setHealthCodeService(mockHealthCodeService);
        dao.setHibernateHelper(mockHibernateHelper);
        dao.setCacheProvider(mockCacheProvider);
        
        when(mockHealthCodeService.createMapping(TestConstants.TEST_STUDY)).thenReturn(new HealthIdImpl(HEALTH_ID,
                HEALTH_CODE));
        
        study = Study.create();
        study.setIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);
        study.setReauthenticationEnabled(true);
        study.setEmailVerificationEnabled(true);
    }

    @Test
    public void verifyEmailUsingToken() {
        HibernateAccount hibernateAccount = new HibernateAccount();
        hibernateAccount.setStatus(AccountStatus.UNVERIFIED);
        hibernateAccount.setEmailVerified(Boolean.FALSE);
        
        GenericAccount account = new GenericAccount();
        account.setId(ACCOUNT_ID);
        account.setStatus(AccountStatus.UNVERIFIED);
        account.setEmailVerified(Boolean.FALSE);
        
        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(hibernateAccount);
        
        dao.verifyChannel(ChannelType.EMAIL, account);
        
        assertEquals(AccountStatus.ENABLED, hibernateAccount.getStatus());
        assertEquals(Boolean.TRUE, hibernateAccount.getEmailVerified());
        assertTrue(hibernateAccount.getModifiedOn() > 0L);
        assertEquals(AccountStatus.ENABLED, account.getStatus());
        assertEquals(Boolean.TRUE, account.getEmailVerified());
        verify(mockHibernateHelper).update(hibernateAccount);
    }

    @Test
    public void verifyEmailUsingAccount() {
        HibernateAccount hibernateAccount = new HibernateAccount();
        hibernateAccount.setStatus(AccountStatus.UNVERIFIED);
        hibernateAccount.setEmailVerified(Boolean.FALSE);
        
        GenericAccount account = new GenericAccount();
        account.setId(ACCOUNT_ID);
        account.setStatus(AccountStatus.UNVERIFIED);
        account.setEmailVerified(Boolean.FALSE);
        
        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(hibernateAccount);
        
        dao.verifyChannel(AuthenticationService.ChannelType.EMAIL, account);
        
        assertEquals(AccountStatus.ENABLED, hibernateAccount.getStatus());
        assertEquals(Boolean.TRUE, hibernateAccount.getEmailVerified());
        assertTrue(hibernateAccount.getModifiedOn() > 0L);
        assertEquals(AccountStatus.ENABLED, account.getStatus());
        assertEquals(Boolean.TRUE, account.getEmailVerified());
        verify(mockHibernateHelper).update(hibernateAccount);
    }
    
    @Test
    public void verifyEmailUsingAccountNoChangeNecessary() {
        HibernateAccount hibernateAccount = new HibernateAccount();
        hibernateAccount.setStatus(AccountStatus.ENABLED);
        hibernateAccount.setEmailVerified(Boolean.TRUE);
        
        GenericAccount account = new GenericAccount();
        account.setId(ACCOUNT_ID);
        account.setStatus(AccountStatus.ENABLED);
        account.setEmailVerified(Boolean.TRUE);
        
        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(hibernateAccount);
        
        dao.verifyChannel(AuthenticationService.ChannelType.EMAIL, account);
        verify(mockHibernateHelper, never()).update(hibernateAccount);
    }
    
    @Test
    public void verifyEmailWithDisabledAccountMakesNoChanges() {
        GenericAccount account = new GenericAccount();
        account.setStatus(AccountStatus.DISABLED);
        
        dao.verifyChannel(AuthenticationService.ChannelType.EMAIL, account);
        verify(mockHibernateHelper, never()).update(any());
        assertEquals(AccountStatus.DISABLED, account.getStatus());
    }
    
    @Test
    public void verifyEmailFailsIfHibernateAccountNotFound() {
        GenericAccount account = new GenericAccount();
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
        
        GenericAccount account = new GenericAccount();
        account.setId(ACCOUNT_ID);
        account.setStatus(AccountStatus.UNVERIFIED);
        account.setPhoneVerified(Boolean.FALSE);
        
        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(hibernateAccount);
        
        dao.verifyChannel(ChannelType.PHONE, account);
        
        assertEquals(AccountStatus.ENABLED, hibernateAccount.getStatus());
        assertEquals(Boolean.TRUE, hibernateAccount.getPhoneVerified());
        assertTrue(hibernateAccount.getModifiedOn() > 0L);
        assertEquals(AccountStatus.ENABLED, account.getStatus());
        assertEquals(Boolean.TRUE, account.getPhoneVerified());
        verify(mockHibernateHelper).update(hibernateAccount);
    }
    
    @Test
    public void verifyPhoneUsingAccount() {
        HibernateAccount hibernateAccount = new HibernateAccount();
        hibernateAccount.setStatus(AccountStatus.UNVERIFIED);
        hibernateAccount.setPhoneVerified(Boolean.FALSE);
        
        GenericAccount account = new GenericAccount();
        account.setId(ACCOUNT_ID);
        account.setStatus(AccountStatus.UNVERIFIED);
        account.setPhoneVerified(Boolean.FALSE);
        
        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(hibernateAccount);
        
        dao.verifyChannel(AuthenticationService.ChannelType.PHONE, account);
        
        assertEquals(AccountStatus.ENABLED, hibernateAccount.getStatus());
        assertEquals(Boolean.TRUE, hibernateAccount.getPhoneVerified());
        assertTrue(hibernateAccount.getModifiedOn() > 0L);
        assertEquals(AccountStatus.ENABLED, account.getStatus());
        assertEquals(Boolean.TRUE, account.getPhoneVerified());
        verify(mockHibernateHelper).update(hibernateAccount);
    }
    
    @Test
    public void verifyPhoneUsingAccountNoChangeNecessary() {
        HibernateAccount hibernateAccount = new HibernateAccount();
        hibernateAccount.setStatus(AccountStatus.ENABLED);
        hibernateAccount.setPhoneVerified(Boolean.TRUE);
        
        GenericAccount account = new GenericAccount();
        account.setId(ACCOUNT_ID);
        account.setStatus(AccountStatus.ENABLED);
        account.setPhoneVerified(Boolean.TRUE);
        
        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(hibernateAccount);
        
        dao.verifyChannel(AuthenticationService.ChannelType.PHONE, account);
        verify(mockHibernateHelper, never()).update(hibernateAccount);
    }
    
    @Test
    public void verifyPhoneWithDisabledAccountMakesNoChanges() {
        GenericAccount account = new GenericAccount();
        account.setStatus(AccountStatus.DISABLED);
        
        dao.verifyChannel(AuthenticationService.ChannelType.PHONE, account);
        verify(mockHibernateHelper, never()).update(any());
        assertEquals(AccountStatus.DISABLED, account.getStatus());
    }
    
    @Test
    public void verifyPhoneFailsIfHibernateAccountNotFound() {
        GenericAccount account = new GenericAccount();
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
        GenericAccount account = new GenericAccount();
        account.setId(ACCOUNT_ID);

        // execute and verify
        dao.changePassword(account, ChannelType.EMAIL, DUMMY_PASSWORD);
        ArgumentCaptor<HibernateAccount> updatedAccountCaptor = ArgumentCaptor.forClass(HibernateAccount.class);
        verify(mockHibernateHelper).update(updatedAccountCaptor.capture());

        HibernateAccount updatedAccount = updatedAccountCaptor.getValue();
        assertEquals(ACCOUNT_ID, updatedAccount.getId());
        assertEquals(MOCK_NOW_MILLIS, updatedAccount.getModifiedOn().longValue());
        assertEquals(PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM, updatedAccount.getPasswordAlgorithm());
        assertEquals(MOCK_NOW_MILLIS, updatedAccount.getPasswordModifiedOn().longValue());
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
        GenericAccount account = new GenericAccount();
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
        GenericAccount account = new GenericAccount();
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
        GenericAccount account = new GenericAccount();
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
        // mock hibernate
        HibernateAccount hibernateAccount = makeValidHibernateAccount(true, false);
        hibernateAccount.setHealthCode("original-" + HEALTH_CODE);
        hibernateAccount.setHealthId("original-" + HEALTH_ID);
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any())).thenReturn(ImmutableList.of(hibernateAccount));

        String originalReauthTokenHash = hibernateAccount.getReauthTokenHash();
        
        // execute and verify - Verify just ID, study, and email, and health code mapping is enough.
        GenericAccount account = (GenericAccount) dao.authenticate(study, PASSWORD_SIGNIN);
        assertEquals(ACCOUNT_ID, account.getId());
        assertEquals(TestConstants.TEST_STUDY, account.getStudyIdentifier());
        assertEquals(EMAIL, account.getEmail());
        assertEquals("original-" + HEALTH_CODE, account.getHealthCode());
        assertEquals("original-" + HEALTH_ID, account.getHealthId());
        assertNotNull(account.getReauthToken());
        assertEquals(2, account.getVersion()); // version was incremented by reauthentication
        assertNotEquals(originalReauthTokenHash, account.getReauthToken());
        
        // verify query
        verify(mockHibernateHelper).queryGet("from HibernateAccount where studyId=:studyId and email=:email",
                EMAIL_QUERY_PARAMS, null, null, HibernateAccount.class);
        
        ArgumentCaptor<HibernateAccount> accountCaptor = ArgumentCaptor.forClass(HibernateAccount.class);
        
        // We don't create a new health code mapping nor update the account.
        verify(mockHealthCodeService, never()).createMapping(any());
        verify(mockHibernateHelper, times(1)).update(accountCaptor.capture());
        
        // healthCodes have not been changed
        assertEquals("original-" + HEALTH_CODE, accountCaptor.getValue().getHealthCode());
        assertEquals("original-" + HEALTH_ID, accountCaptor.getValue().getHealthId());
        assertNotEquals(originalReauthTokenHash, accountCaptor.getValue().getReauthTokenHash());
    }

    @Test
    public void authenticateSuccessCreateNewHealthCode() throws Exception {
        // mock hibernate
        HibernateAccount hibernateAccount = makeValidHibernateAccount(true, false);
        // Clear these fields to verify that they are created
        hibernateAccount.setHealthId(null);
        hibernateAccount.setHealthCode(null);
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any()))
                .thenReturn(ImmutableList.of(hibernateAccount));

        // execute and verify - Verify just ID, study, and email, and health code mapping is enough.
        GenericAccount account = (GenericAccount) dao.authenticate(study, PASSWORD_SIGNIN);
        assertEquals(ACCOUNT_ID, account.getId());
        assertEquals(TestConstants.TEST_STUDY, account.getStudyIdentifier());
        assertEquals(EMAIL, account.getEmail());
        assertEquals(HEALTH_CODE, account.getHealthCode());
        assertEquals(HEALTH_ID, account.getHealthId());

        // verify query
        verify(mockHibernateHelper).queryGet("from HibernateAccount where studyId=:studyId and email=:email",
                EMAIL_QUERY_PARAMS, null, null, HibernateAccount.class);
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
        GenericAccount account = (GenericAccount) dao.authenticate(study, PASSWORD_SIGNIN);
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
        // mock hibernate
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false, true);
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any())).thenReturn(ImmutableList.of(hibernateAccount));
        String originalReauthTokenHash = hibernateAccount.getReauthTokenHash();

        // execute and verify - Verify just ID, study, and email, and health code mapping is enough.
        GenericAccount account = (GenericAccount) dao.reauthenticate(study, REAUTH_SIGNIN);
        assertEquals(ACCOUNT_ID, account.getId());
        assertEquals(TestConstants.TEST_STUDY, account.getStudyIdentifier());
        assertEquals(EMAIL, account.getEmail());
        assertNotEquals(originalReauthTokenHash, account.getReauthToken());
        // This has been incremented by the reauth token update
        assertEquals(2, account.getVersion());
        
        // verify query
        verify(mockHibernateHelper).queryGet("from HibernateAccount where studyId=:studyId and email=:email",
                EMAIL_QUERY_PARAMS, null, null, HibernateAccount.class);

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
        hibernateAccount.setReauthTokenModifiedOn(DateTime.now().getMillis());
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
        Long originalTimestamp = DateTime.now().minusMinutes(2).getMillis();
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
        GenericAccount account = (GenericAccount) dao.constructAccount(study, EMAIL, PHONE, EXTERNAL_ID, DUMMY_PASSWORD);
        assertEquals(TestConstants.TEST_STUDY, account.getStudyIdentifier());
        assertEquals(EMAIL, account.getEmail());
        assertEquals(PHONE.getNationalFormat(), account.getPhone().getNationalFormat());
        assertEquals(Boolean.FALSE, account.getEmailVerified());
        assertEquals(Boolean.FALSE, account.getPhoneVerified());
        assertEquals(HEALTH_CODE, account.getHealthCode());
        assertEquals(HEALTH_ID, account.getHealthId());
        assertEquals(EXTERNAL_ID, account.getExternalId());
        assertEquals(PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM, account.getPasswordAlgorithm());

        // validate password hash
        assertTrue(PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM.checkHash(account.getPasswordHash(), DUMMY_PASSWORD));
    }
    
    @Test
    public void constructAccountWithoutPasswordWorks() throws Exception {
        // execute and validate
        GenericAccount account = (GenericAccount) dao.constructAccount(study, EMAIL, PHONE, EXTERNAL_ID, null);
        assertEquals(TestConstants.TEST_STUDY, account.getStudyIdentifier());
        assertEquals(EMAIL, account.getEmail());
        assertEquals(PHONE.getNationalFormat(), account.getPhone().getNationalFormat());
        assertEquals(Boolean.FALSE, account.getEmailVerified());
        assertEquals(Boolean.FALSE, account.getPhoneVerified());
        assertEquals(HEALTH_CODE, account.getHealthCode());
        assertEquals(HEALTH_ID, account.getHealthId());
        assertEquals(EXTERNAL_ID, account.getExternalId());
        assertNull(account.getPasswordHash());
        assertNull(account.getPasswordAlgorithm());
    }

    @Test
    public void createAccountSuccess() {
        // Study passed into createAccount() takes precedence over StudyId in the Account object. To test this, make
        // the account have a different study.
        GenericAccount account = makeValidGenericAccount();
        account.setStatus(AccountStatus.ENABLED);
        account.setStudyId(new StudyIdentifierImpl("wrong-study"));

        // execute - We generate a new account ID.
        String daoOutputAcountId = dao.createAccount(study, account);
        assertNotNull(daoOutputAcountId);
        assertNotEquals(ACCOUNT_ID, daoOutputAcountId);

        // verify hibernate call
        ArgumentCaptor<HibernateAccount> createdHibernateAccountCaptor = ArgumentCaptor.forClass(
                HibernateAccount.class);
        verify(mockHibernateHelper).create(createdHibernateAccountCaptor.capture());

        HibernateAccount createdHibernateAccount = createdHibernateAccountCaptor.getValue();
        assertEquals(daoOutputAcountId, createdHibernateAccount.getId());
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, createdHibernateAccount.getStudyId());
        assertEquals(MOCK_NOW_MILLIS, createdHibernateAccount.getCreatedOn().longValue());
        assertEquals(MOCK_NOW_MILLIS, createdHibernateAccount.getModifiedOn().longValue());
        assertEquals(MOCK_NOW_MILLIS, createdHibernateAccount.getPasswordModifiedOn().longValue());
        assertEquals(AccountStatus.ENABLED, createdHibernateAccount.getStatus());
        assertEquals(AccountDao.MIGRATION_VERSION, createdHibernateAccount.getMigrationVersion());
    }

    @Test
    public void createAccountAlreadyExists() {
        // mock hibernate
        String otherAccountId = "other-account-id";
        HibernateAccount otherHibernateAccount = new HibernateAccount();
        otherHibernateAccount.setId(otherAccountId);
        otherHibernateAccount.setMigrationVersion(AccountDao.MIGRATION_VERSION);
        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        when(mockHibernateHelper.queryGet(queryCaptor.capture(), eq(EMAIL_QUERY_PARAMS), any(), any(), any()))
                .thenReturn(ImmutableList.of(otherHibernateAccount));

        ConstraintViolationException cve = new ConstraintViolationException("Duplicate entry for key 'Accounts-StudyId-Email-Index'", null, null);
        doThrow(new PersistenceException(cve)).when(mockHibernateHelper).create(any());

        // execute
        try {
            dao.createAccount(study, makeValidGenericAccount());
            fail("expected exception");
        } catch (EntityAlreadyExistsException ex) {
            assertEquals(otherAccountId, ex.getEntity().get("userId"));
            assertTrue(queryCaptor.getValue().contains("email=:email"));
        }
    }

    @Test(expected = BridgeServiceException.class)
    public void createAccountAlreadyExistsButNotFound() {
        // mock hibernate
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any())).thenReturn(ImmutableList.of());
        doThrow(ConcurrentModificationException.class).when(mockHibernateHelper).create(any());

        // execute
        dao.createAccount(study, makeValidGenericAccount());
    }

    @Test
    public void createAccountAlreadyExistsForPhoneAccount() {
        String otherAccountId = "other-account-id";
        HibernateAccount otherHibernateAccount = new HibernateAccount();
        otherHibernateAccount.setId(otherAccountId);
        otherHibernateAccount.setMigrationVersion(AccountDao.MIGRATION_VERSION);
        
        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        when(mockHibernateHelper.queryGet(queryCaptor.capture(), eq(PHONE_QUERY_PARAMS), any(), any(), any()))
                .thenReturn(ImmutableList.of(otherHibernateAccount));

        ConstraintViolationException cve = new ConstraintViolationException("Duplicate entry for key 'Accounts-StudyId-Phone-Index'", null, null);
        doThrow(new PersistenceException(cve)).when(mockHibernateHelper).create(any());

        // execute
        try {
            GenericAccount account = makeValidGenericAccount();
            account.setEmail(null);
            account.setPhone(TestConstants.PHONE);
            dao.createAccount(study, account);
            fail("expected exception");
        } catch (EntityAlreadyExistsException ex) {
            assertEquals(otherAccountId, ex.getEntity().get("userId"));
            assertTrue(queryCaptor.getValue().contains("phone.number=:number and phone.regionCode=:regionCode"));
        }
    }
    
    @Test
    public void createAccountAlreadyExistsForExternalIdAccount() {
        String externalId = "other-account-id";
        HibernateAccount otherHibernateAccount = new HibernateAccount();
        otherHibernateAccount.setId("userId");
        otherHibernateAccount.setExternalId(externalId);
        
        Map<String,Object> params = new HashMap<>();
        params.put("studyId", TestConstants.TEST_STUDY_IDENTIFIER);
        params.put("externalId", externalId);
        
        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        when(mockHibernateHelper.queryGet(queryCaptor.capture(), eq(params), any(), any(), any()))
                .thenReturn(ImmutableList.of(otherHibernateAccount));
        
        ConstraintViolationException cve = new ConstraintViolationException("Duplicate entry for key 'Accounts-StudyId-ExternalId-Index'", null, null);
        doThrow(new PersistenceException(cve)).when(mockHibernateHelper).create(any());

        // execute
        try {
            GenericAccount account = makeValidGenericAccount();
            account.setEmail(null);
            account.setPhone(null);
            account.setExternalId(externalId);
            dao.createAccount(study, account);
            fail("expected exception");
        } catch (EntityAlreadyExistsException ex) {
            assertEquals("userId", ex.getEntity().get("userId"));
            assertTrue(queryCaptor.getValue().contains("externalId=:externalId"));
        }
    }
    
    @Test
    public void updateSuccess() {
        // Some fields can't be modified. Create the persisted account and set the base fields so we can verify they
        // weren't modified.
        HibernateAccount persistedAccount = new HibernateAccount();
        persistedAccount.setStudyId("persisted-study");
        persistedAccount.setEmail("persisted@example.com");
        persistedAccount.setCreatedOn(1234L);
        persistedAccount.setPasswordModifiedOn(5678L);
        persistedAccount.setPhone(PHONE);
        persistedAccount.setEmailVerified(Boolean.TRUE);
        persistedAccount.setPhoneVerified(Boolean.TRUE);

        // Set a dummy modifiedOn to make sure we're overwriting it.
        persistedAccount.setModifiedOn(5678L);

        // mock hibernate
        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(persistedAccount);

        GenericAccount account = makeValidGenericAccount();
        account.setEmail(OTHER_EMAIL);
        account.setPhone(OTHER_PHONE);
        account.setEmailVerified(Boolean.FALSE);
        account.setPhoneVerified(Boolean.FALSE);
        account.setExternalId(EXTERNAL_ID);
        
        // Execute. Identifiers not allows to change.
        dao.updateAccount(account, false);

        // verify hibernate update
        ArgumentCaptor<HibernateAccount> updatedHibernateAccountCaptor = ArgumentCaptor.forClass(
                HibernateAccount.class);
        verify(mockHibernateHelper).update(updatedHibernateAccountCaptor.capture());

        HibernateAccount updatedHibernateAccount = updatedHibernateAccountCaptor.getValue();
        assertEquals(ACCOUNT_ID, updatedHibernateAccount.getId());
        assertEquals("persisted-study", updatedHibernateAccount.getStudyId());
        assertEquals("persisted@example.com", updatedHibernateAccount.getEmail());
        assertEquals(PHONE.getNationalFormat(),
                updatedHibernateAccount.getPhone().getNationalFormat());
        assertEquals(Boolean.TRUE, updatedHibernateAccount.getEmailVerified());
        assertEquals(Boolean.TRUE, updatedHibernateAccount.getPhoneVerified());
        assertEquals(1234, updatedHibernateAccount.getCreatedOn().longValue());
        assertEquals(5678, updatedHibernateAccount.getPasswordModifiedOn().longValue());
        assertEquals(MOCK_NOW_MILLIS, updatedHibernateAccount.getModifiedOn().longValue());
        assertEquals(EXTERNAL_ID, updatedHibernateAccount.getExternalId());
    }
    
    @Test
    public void updateDoesNotChangePasswordOrReauthToken() throws Exception {
        HibernateAccount persistedAccount = makeValidHibernateAccount(true, true);
        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(persistedAccount);
        
        GenericAccount account = new GenericAccount();
        account.setId(ACCOUNT_ID);
        account.setPasswordAlgorithm(PasswordAlgorithm.STORMPATH_HMAC_SHA_256);
        account.setPasswordHash("bad password hash");
        account.setPasswordModifiedOn(MOCK_NOW_MILLIS);
        account.setReauthTokenAlgorithm(PasswordAlgorithm.STORMPATH_HMAC_SHA_256);
        account.setReauthTokenHash("bad reauth token hash");
        account.setReauthTokenModifiedOn(MOCK_NOW_MILLIS);
        
        dao.updateAccount(account, false);
        
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
            dao.updateAccount(makeValidGenericAccount(), false);
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
        persistedAccount.setCreatedOn(1234L);
        persistedAccount.setPasswordModifiedOn(5678L);
        persistedAccount.setPhone(PHONE);
        persistedAccount.setEmailVerified(Boolean.TRUE);
        persistedAccount.setPhoneVerified(Boolean.TRUE);
        persistedAccount.setExternalId("some-other-extid");

        // Set a dummy modifiedOn to make sure we're overwriting it.
        persistedAccount.setModifiedOn(5678L);

        // mock hibernate
        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(persistedAccount);

        // execute
        GenericAccount account = makeValidGenericAccount();
        account.setEmail(OTHER_EMAIL);
        account.setPhone(OTHER_PHONE);
        account.setEmailVerified(Boolean.FALSE);
        account.setPhoneVerified(Boolean.FALSE);
        account.setExternalId(EXTERNAL_ID);
        
        // Identifiers ARE allowed to change here.
        dao.updateAccount(account, true);

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
        hibernateAccount.setHealthId("original-" + HEALTH_ID);
        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(hibernateAccount);

        // execute and validate - just validate ID, study, and email, and health code mapping
        GenericAccount account = (GenericAccount) dao.getAccount(ACCOUNT_ID_WITH_ID);
        assertEquals(ACCOUNT_ID, account.getId());
        assertEquals(TestConstants.TEST_STUDY, account.getStudyIdentifier());
        assertEquals(EMAIL, account.getEmail());
        assertEquals("original-" + HEALTH_CODE, account.getHealthCode());
        assertEquals("original-" + HEALTH_ID, account.getHealthId());

        // We don't create a new health code mapping nor update the account.
        verify(mockHealthCodeService, never()).createMapping(any());
        verify(mockHibernateHelper, never()).update(any());
    }

    @Test
    public void getByIdSuccessCreateNewHealthCode() throws Exception {
        // mock hibernate
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false, false);
        // Clear these fields to verify that they are created
        hibernateAccount.setHealthId(null);
        hibernateAccount.setHealthCode(null);
        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(
                hibernateAccount);

        // execute and validate - just validate ID, study, and email, and health code mapping
        GenericAccount account = (GenericAccount) dao.getAccount(ACCOUNT_ID_WITH_ID);
        assertEquals(ACCOUNT_ID, account.getId());
        assertEquals(TestConstants.TEST_STUDY, account.getStudyIdentifier());
        assertEquals(EMAIL, account.getEmail());
        assertEquals(HEALTH_CODE, account.getHealthCode());
        assertEquals(HEALTH_ID, account.getHealthId());

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
        // mock hibernate
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false, false);
        hibernateAccount.setHealthCode("original-" + HEALTH_CODE);
        hibernateAccount.setHealthId("original-" + HEALTH_ID);
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any())).thenReturn(ImmutableList.of(hibernateAccount));

        // execute and validate - just validate ID, study, and email, and health code mapping
        GenericAccount account = (GenericAccount) dao.getAccount(ACCOUNT_ID_WITH_EMAIL);
        assertEquals(ACCOUNT_ID, account.getId());
        assertEquals(TestConstants.TEST_STUDY, account.getStudyIdentifier());
        assertEquals(EMAIL, account.getEmail());
        assertEquals("original-" + HEALTH_CODE, account.getHealthCode());
        assertEquals("original-" + HEALTH_ID, account.getHealthId());

        // verify hibernate query
        verify(mockHibernateHelper).queryGet("from HibernateAccount where studyId=:studyId and email=:email",
                EMAIL_QUERY_PARAMS, null, null, HibernateAccount.class);

        // We don't create a new health code mapping nor update the account.
        verify(mockHealthCodeService, never()).createMapping(any());
        verify(mockHibernateHelper, never()).update(any());
    }

    @Test
    public void getByEmailSuccessCreateNewHealthCode() throws Exception {
        // mock hibernate
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false, false);
        // Clear these fields to verify that they are created
        hibernateAccount.setHealthId(null);
        hibernateAccount.setHealthCode(null);
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any()))
                .thenReturn(ImmutableList.of(hibernateAccount));

        // execute and validate - just validate ID, study, and email, and health code mapping
        GenericAccount account = (GenericAccount) dao.getAccount(ACCOUNT_ID_WITH_EMAIL);
        assertEquals(ACCOUNT_ID, account.getId());
        assertEquals(TestConstants.TEST_STUDY, account.getStudyIdentifier());
        assertEquals(EMAIL, account.getEmail());
        assertEquals(HEALTH_CODE, account.getHealthCode());
        assertEquals(HEALTH_ID, account.getHealthId());

        // verify hibernate query
        verify(mockHibernateHelper).queryGet("from HibernateAccount where studyId=:studyId and email=:email",
                EMAIL_QUERY_PARAMS, null, null, HibernateAccount.class);

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
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false, false);
        // mock hibernate
        when(mockHibernateHelper.queryGet(
                "from HibernateAccount where studyId=:studyId and phone.number=:number and phone.regionCode=:regionCode",
                PHONE_QUERY_PARAMS, null, null, HibernateAccount.class)).thenReturn(ImmutableList.of(hibernateAccount));

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
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false, false);
        // mock hibernate
        when(mockHibernateHelper.queryGet(
                "from HibernateAccount where studyId=:studyId and phone.number=:number and phone.regionCode=:regionCode",
                PHONE_QUERY_PARAMS, null, null, HibernateAccount.class)).thenReturn(ImmutableList.of(hibernateAccount));

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
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false, false);
        // mock hibernate
        when(mockHibernateHelper.queryGet("from HibernateAccount where studyId=:studyId and healthCode=:healthCode",
                HEALTHCODE_QUERY_PARAMS, null, null, HibernateAccount.class))
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
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false, false);
        // mock hibernate
        when(mockHibernateHelper.queryGet("from HibernateAccount where studyId=:studyId and healthCode=:healthCode",
                HEALTHCODE_QUERY_PARAMS, null, null, HibernateAccount.class)).thenReturn(ImmutableList.of(hibernateAccount));
        
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
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false, false);
        // mock hibernate
        when(mockHibernateHelper.queryGet("from HibernateAccount where studyId=:studyId and externalId=:externalId",
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
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false, false);
        // mock hibernate
        when(mockHibernateHelper.queryGet("from HibernateAccount where studyId=:studyId and externalId=:externalId",
                EXTID_QUERY_PARAMS, null, null, HibernateAccount.class)).thenReturn(ImmutableList.of(hibernateAccount));

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
        // Directly deletes with the ID it has
        dao.deleteAccount(ACCOUNT_ID_WITH_ID);
        
        verify(mockHibernateHelper).deleteById(HibernateAccount.class, ACCOUNT_ID);
        verify(mockHibernateHelper, never()).queryGet(any(), any(), any(), any(), any());
    }

    @Test
    public void getAll() throws Exception {
        // mock hibernate
        HibernateAccount hibernateAccount1 = makeValidHibernateAccount(false, false);
        hibernateAccount1.setId("account-1");
        hibernateAccount1.setEmail("email1@example.com");

        HibernateAccount hibernateAccount2 = makeValidHibernateAccount(false, false);
        hibernateAccount2.setId("account-2");
        hibernateAccount2.setEmail("email2@example.com");

        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any()))
                .thenReturn(ImmutableList.of(hibernateAccount1, hibernateAccount2));

        // execute and validate - just ID, study, and email is sufficient
        Iterator<AccountSummary> accountSummaryIter = dao.getAllAccounts();
        List<AccountSummary> accountSummaryList = ImmutableList.copyOf(accountSummaryIter);
        assertEquals(2, accountSummaryList.size());

        assertEquals("account-1", accountSummaryList.get(0).getId());
        assertEquals(TestConstants.TEST_STUDY, accountSummaryList.get(0).getStudyIdentifier());
        assertEquals("email1@example.com", accountSummaryList.get(0).getEmail());

        assertEquals("account-2", accountSummaryList.get(1).getId());
        assertEquals(TestConstants.TEST_STUDY, accountSummaryList.get(1).getStudyIdentifier());
        assertEquals("email2@example.com", accountSummaryList.get(1).getEmail());

        // verify hibernate call
        verify(mockHibernateHelper).queryGet("from HibernateAccount", null, null, null, HibernateAccount.class);
    }

    @Test
    public void getAllInStudy() throws Exception {
        // mock hibernate
        HibernateAccount hibernateAccount1 = makeValidHibernateAccount(false, false);
        hibernateAccount1.setId("account-1");
        hibernateAccount1.setEmail("email1@example.com");

        HibernateAccount hibernateAccount2 = makeValidHibernateAccount(false, false);
        hibernateAccount2.setId("account-2");
        hibernateAccount2.setEmail("email2@example.com");

        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any()))
                .thenReturn(ImmutableList.of(hibernateAccount1, hibernateAccount2));

        // execute and validate - just ID, study, and email is sufficient
        Iterator<AccountSummary> accountSummaryIter = dao.getStudyAccounts(study);
        List<AccountSummary> accountSummaryList = ImmutableList.copyOf(accountSummaryIter);
        assertEquals(2, accountSummaryList.size());

        assertEquals("account-1", accountSummaryList.get(0).getId());
        assertEquals(TestConstants.TEST_STUDY, accountSummaryList.get(0).getStudyIdentifier());
        assertEquals("email1@example.com", accountSummaryList.get(0).getEmail());

        assertEquals("account-2", accountSummaryList.get(1).getId());
        assertEquals(TestConstants.TEST_STUDY, accountSummaryList.get(1).getStudyIdentifier());
        assertEquals("email2@example.com", accountSummaryList.get(1).getEmail());

        // verify hibernate call
        verify(mockHibernateHelper).queryGet("from HibernateAccount where studyId=:studyId", STUDY_QUERY_PARAMS, null,
                null, HibernateAccount.class);
    }

    @Test
    public void getPaged() throws Exception {
        // mock hibernate
        HibernateAccount hibernateAccount1 = makeValidHibernateAccount(false, false);
        hibernateAccount1.setId("account-1");
        hibernateAccount1.setEmail("email1@example.com");

        HibernateAccount hibernateAccount2 = makeValidHibernateAccount(false, false);
        hibernateAccount2.setId("account-2");
        hibernateAccount2.setEmail("email2@example.com");

        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any())).thenReturn(ImmutableList.of(hibernateAccount1,
                hibernateAccount2));
        when(mockHibernateHelper.queryCount(any(), any())).thenReturn(12);

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
        String expectedQueryString = "from HibernateAccount as acct where studyId=:studyId";
        String expectedGetQueryString = HibernateAccountDao.ACCOUNT_SUMMARY_QUERY_PREFIX + expectedQueryString;
        
        verify(mockHibernateHelper).queryGet(expectedGetQueryString, STUDY_QUERY_PARAMS, 10, 5, HibernateAccount.class);
        verify(mockHibernateHelper).queryCount(expectedQueryString, STUDY_QUERY_PARAMS);
    }

    @Test
    public void getPagedWithOptionalParams() throws Exception {
        // Setup start and end dates.
        DateTime startDate = DateTime.parse("2017-05-19T11:40:06.247-0700");
        DateTime endDate = DateTime.parse("2017-05-19T18:32:03.434-0700");

        // mock hibernate
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any())).thenReturn(ImmutableList.of(
                makeValidHibernateAccount(false, false)));
        when(mockHibernateHelper.queryCount(any(), any())).thenReturn(11);

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
        params.put("startTime", startDate.getMillis());
        params.put("endTime", endDate.getMillis());
        params.put("in1", "a");
        params.put("in2", "b");
        params.put("notin1", "c");
        params.put("notin2", "d");
        params.put("language", "de");
        
        String expectedQueryString = "from HibernateAccount as acct where studyId=:studyId and " + 
                "email like :email and phone.number like :number and createdOn >= :startTime and createdOn <= :endTime and " + 
                ":language in elements(acct.languages) and (:in2 in elements(acct.dataGroups) and :in1 in elements(acct.dataGroups)) " +
                "and (:notin1 not in elements(acct.dataGroups) and :notin2 not in elements(acct.dataGroups))";
        String expectedGetQueryString = HibernateAccountDao.ACCOUNT_SUMMARY_QUERY_PREFIX + expectedQueryString;
        verify(mockHibernateHelper).queryGet(eq(expectedGetQueryString), paramCaptor.capture(), eq(10), eq(5), eq(HibernateAccount.class));
        verify(mockHibernateHelper).queryCount(eq(expectedQueryString), paramCaptor.capture());
        
        Map<String,Object> capturedParams = paramCaptor.getAllValues().get(0);
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, capturedParams.get("studyId"));
        assertEquals("%"+EMAIL+"%", capturedParams.get("email"));
        assertEquals("%"+phoneString+"%", capturedParams.get("number"));
        assertEquals(startDate.getMillis(), capturedParams.get("startTime"));
        assertEquals(endDate.getMillis(), capturedParams.get("endTime"));
        assertEquals("a", capturedParams.get("in1"));
        assertEquals("b", capturedParams.get("in2"));
        assertEquals("d", capturedParams.get("notin1"));
        assertEquals("c", capturedParams.get("notin2"));
        assertEquals("de", capturedParams.get("language"));
        
        capturedParams = paramCaptor.getAllValues().get(1);
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, capturedParams.get("studyId"));
        assertEquals("%"+EMAIL+"%", capturedParams.get("email"));
        assertEquals("%"+phoneString+"%", capturedParams.get("number"));
        assertEquals(startDate.getMillis(), capturedParams.get("startTime"));
        assertEquals(endDate.getMillis(), capturedParams.get("endTime"));
        assertEquals("a", capturedParams.get("in1"));
        assertEquals("b", capturedParams.get("in2"));
        assertEquals("d", capturedParams.get("notin1"));
        assertEquals("c", capturedParams.get("notin2"));
        assertEquals("de", capturedParams.get("language"));
    }
    
    @Test
    public void getPagedWithOptionalEmptySetParams() throws Exception {
        // Setup start and end dates.
        DateTime startDate = DateTime.parse("2017-05-19T11:40:06.247-0700");
        DateTime endDate = DateTime.parse("2017-05-19T18:32:03.434-0700");

        // mock hibernate
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any())).thenReturn(ImmutableList.of(
                makeValidHibernateAccount(false, false)));
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
        params.put("studyId", TestConstants.TEST_STUDY_IDENTIFIER);
        params.put("email", "%"+EMAIL+"%");
        params.put("number", "%"+phoneString+"%");
        params.put("startTime", startDate.getMillis());
        params.put("endTime", endDate.getMillis());
        params.put("language", "de");
        
        String expectedQueryString = "from HibernateAccount as acct where studyId=:studyId and " + 
                "email like :email and phone.number like :number and createdOn >= :startTime and createdOn <= :endTime and " + 
                ":language in elements(acct.languages)";
        String expectedGetQueryString = HibernateAccountDao.ACCOUNT_SUMMARY_QUERY_PREFIX + expectedQueryString;
        verify(mockHibernateHelper).queryGet(eq(expectedGetQueryString), paramCaptor.capture(), eq(10), eq(5), eq(HibernateAccount.class));
        verify(mockHibernateHelper).queryCount(eq(expectedQueryString), paramCaptor.capture());
        
        Map<String,Object> capturedParams = paramCaptor.getAllValues().get(0);
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, capturedParams.get("studyId"));
        assertEquals("%"+EMAIL+"%", capturedParams.get("email"));
        assertEquals("%"+phoneString+"%", capturedParams.get("number"));
        assertEquals(startDate.getMillis(), capturedParams.get("startTime"));
        assertEquals(endDate.getMillis(), capturedParams.get("endTime"));
        assertEquals("de", capturedParams.get("language"));
        
        capturedParams = paramCaptor.getAllValues().get(1);
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, capturedParams.get("studyId"));
        assertEquals("%"+EMAIL+"%", capturedParams.get("email"));
        assertEquals("%"+phoneString+"%", capturedParams.get("number"));
        assertEquals(startDate.getMillis(), capturedParams.get("startTime"));
        assertEquals(endDate.getMillis(), capturedParams.get("endTime"));
        assertEquals("de", capturedParams.get("language"));
    }
    
    @Test
    public void getHealthCode() throws Exception {
        // mock hibernate
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false, false);
        hibernateAccount.setHealthCode(HEALTH_CODE);
        hibernateAccount.setHealthId(HEALTH_ID);
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any())).thenReturn(ImmutableList.of(hibernateAccount));

        // execute and validate
        String healthCode = dao.getHealthCodeForAccount(ACCOUNT_ID_WITH_EMAIL);
        assertEquals(HEALTH_CODE, healthCode);

        // verify hibernate query
        verify(mockHibernateHelper).queryGet("from HibernateAccount where studyId=:studyId and email=:email",
                EMAIL_QUERY_PARAMS, null, null, HibernateAccount.class);
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
    public void marshallSuccess() {
        // create a fully populated GenericAccount
        GenericAccount genericAccount = new GenericAccount();
        genericAccount.setId(ACCOUNT_ID);
        genericAccount.setStudyId(TestConstants.TEST_STUDY);
        genericAccount.setEmail(EMAIL);
        genericAccount.setPhone(PHONE);
        genericAccount.setEmailVerified(Boolean.TRUE);
        genericAccount.setPhoneVerified(Boolean.FALSE);
        genericAccount.setCreatedOn(CREATED_ON);
        genericAccount.setHealthCode(HEALTH_CODE);
        genericAccount.setHealthId(HEALTH_ID);
        genericAccount.setFirstName(FIRST_NAME);
        genericAccount.setLastName(LAST_NAME);
        genericAccount.setPasswordAlgorithm(PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM);
        genericAccount.setPasswordHash(DUMMY_PASSWORD_HASH);
        genericAccount.setPasswordModifiedOn(CREATED_ON.getMillis());
        genericAccount.setReauthTokenAlgorithm(PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM);
        genericAccount.setReauthTokenHash(DUMMY_REAUTH_TOKEN_HASH);
        genericAccount.setReauthTokenModifiedOn(CREATED_ON.getMillis());
        genericAccount.setRoles(EnumSet.of(Roles.DEVELOPER, Roles.RESEARCHER));
        genericAccount.setStatus(AccountStatus.ENABLED);
        genericAccount.setClientData(TestUtils.getClientData());
        genericAccount.setVersion(VERSION);
        genericAccount.setTimeZone(DateTimeZone.forOffsetHours(-7));
        genericAccount.setSharingScope(SharingScope.ALL_QUALIFIED_RESEARCHERS);
        genericAccount.setNotifyByEmail(true);
        genericAccount.setExternalId(EXTERNAL_ID);
        genericAccount.setDataGroups(TestConstants.USER_DATA_GROUPS);
        genericAccount.setLanguages(TestConstants.LANGUAGES);
        genericAccount.setMigrationVersion(AccountDao.MIGRATION_VERSION);         

        // populate attributes
        genericAccount.setAttribute("foo-attr", "foo-value");
        genericAccount.setAttribute("bar-attr", "bar-value");

        // populate GenericAccount with consents, 2 subpops, 2 consents each.
        SubpopulationGuid fooSubpopGuid = SubpopulationGuid.create("foo-subpop-guid");
        SubpopulationGuid barSubpopGuid = SubpopulationGuid.create("bar-subpop-guid");

        ConsentSignature fooConsentSignature1 = new ConsentSignature.Builder().withName("One McFooface")
                .withBirthdate("1999-01-01").withConsentCreatedOn(1000).withSignedOn(1111)
                .build();
        ConsentSignature fooConsentSignature2 = new ConsentSignature.Builder().withName("Two McFooface")
                .withBirthdate("1999-02-02").withConsentCreatedOn(2000).withSignedOn(2222)
                .withWithdrewOn(2777L).build();
        ConsentSignature barConsentSignature3 = new ConsentSignature.Builder().withName("Three McBarface")
                .withBirthdate("1999-03-03").withConsentCreatedOn(3000).withSignedOn(3333)
                .build();
        ConsentSignature barConsentSignature4 = new ConsentSignature.Builder().withName("Four McBarface")
                .withBirthdate("1999-04-04").withImageData("dummy-image-data").withImageMimeType("image/dummy")
                .withConsentCreatedOn(4000).withSignedOn(4444).build();

        genericAccount.setConsentSignatureHistory(fooSubpopGuid, ImmutableList.of(fooConsentSignature1,
                fooConsentSignature2));
        genericAccount.setConsentSignatureHistory(barSubpopGuid, ImmutableList.of(barConsentSignature3,
                barConsentSignature4));

        // marshall
        HibernateAccount hibernateAccount = HibernateAccountDao.marshallAccount(genericAccount);
        assertEquals(ACCOUNT_ID, hibernateAccount.getId());
        assertEquals(TestConstants.TEST_STUDY_IDENTIFIER, hibernateAccount.getStudyId());
        assertEquals(EMAIL, hibernateAccount.getEmail());
        assertEquals(PHONE, hibernateAccount.getPhone());
        assertEquals(Boolean.TRUE, hibernateAccount.getEmailVerified());
        assertEquals(Boolean.FALSE, hibernateAccount.getPhoneVerified());
        assertEquals(CREATED_ON.getMillis(), hibernateAccount.getCreatedOn().longValue());
        assertEquals(HEALTH_CODE, hibernateAccount.getHealthCode());
        assertEquals(HEALTH_ID, hibernateAccount.getHealthId());
        assertEquals(FIRST_NAME, hibernateAccount.getFirstName());
        assertEquals(LAST_NAME, hibernateAccount.getLastName());
        assertEquals(PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM, hibernateAccount.getPasswordAlgorithm());
        assertEquals(DUMMY_PASSWORD_HASH, hibernateAccount.getPasswordHash());
        assertEquals(new Long(CREATED_ON.getMillis()), hibernateAccount.getPasswordModifiedOn());
        assertEquals(PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM, hibernateAccount.getReauthTokenAlgorithm());
        assertEquals(DUMMY_REAUTH_TOKEN_HASH, hibernateAccount.getReauthTokenHash());
        assertEquals(new Long(CREATED_ON.getMillis()), hibernateAccount.getReauthTokenModifiedOn());
        assertEquals(EnumSet.of(Roles.DEVELOPER, Roles.RESEARCHER), hibernateAccount.getRoles());
        assertEquals(AccountStatus.ENABLED, hibernateAccount.getStatus());
        assertEquals(TestUtils.getClientData().toString(), hibernateAccount.getClientData());
        assertEquals(VERSION, hibernateAccount.getVersion());
        assertEquals("-07:00", hibernateAccount.getTimeZone());
        assertEquals(SharingScope.ALL_QUALIFIED_RESEARCHERS, hibernateAccount.getSharingScope());
        assertEquals(Boolean.TRUE, hibernateAccount.getNotifyByEmail());
        assertEquals(EXTERNAL_ID, hibernateAccount.getExternalId());
        assertEquals(TestConstants.USER_DATA_GROUPS, hibernateAccount.getDataGroups());
        assertEquals(Lists.newArrayList(TestConstants.LANGUAGES), hibernateAccount.getLanguages());
        assertEquals(AccountDao.MIGRATION_VERSION, hibernateAccount.getMigrationVersion());
        
        // validate attributes
        Map<String, String> hibernateAttrMap = hibernateAccount.getAttributes();
        assertEquals(2, hibernateAttrMap.size());
        assertEquals("foo-value", hibernateAttrMap.get("foo-attr"));
        assertEquals("bar-value", hibernateAttrMap.get("bar-attr"));

        // validate consents
        Map<HibernateAccountConsentKey, HibernateAccountConsent> hibernateConsentMap = hibernateAccount.getConsents();
        assertEquals(4, hibernateConsentMap.size());
        validateHibernateConsent(fooConsentSignature1, hibernateConsentMap.get(new HibernateAccountConsentKey(
                fooSubpopGuid.getGuid(), fooConsentSignature1.getSignedOn())));
        validateHibernateConsent(fooConsentSignature2, hibernateConsentMap.get(new HibernateAccountConsentKey(
                fooSubpopGuid.getGuid(), fooConsentSignature2.getSignedOn())));
        validateHibernateConsent(barConsentSignature3, hibernateConsentMap.get(new HibernateAccountConsentKey(
                barSubpopGuid.getGuid(), barConsentSignature3.getSignedOn())));
        validateHibernateConsent(barConsentSignature4, hibernateConsentMap.get(new HibernateAccountConsentKey(
                barSubpopGuid.getGuid(), barConsentSignature4.getSignedOn())));

        // Note that modifiedOn doesn't appear in GenericAccount, and we always modify it when creating or updating, so
        // it doesn't need to be marshalled. Similarly for passwordModifiedOn.
    }

    // branch coverage, to make sure nothing crashes.
    @Test
    public void marshallBlankAccount() {
        HibernateAccount hibernateAccount = HibernateAccountDao.marshallAccount(new GenericAccount());
        assertNotNull(hibernateAccount);
    }

    // branch coverage
    @Test(expected = BridgeServiceException.class)
    public void marshalNotGenericAccount() {
        HibernateAccountDao.marshallAccount(mock(Account.class));
    }

    @Test
    public void unmarshallSuccess() {
        // create a fully populated HibernateAccount
        HibernateAccount hibernateAccount = new HibernateAccount();
        hibernateAccount.setId(ACCOUNT_ID);
        hibernateAccount.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        hibernateAccount.setEmail(EMAIL);
        hibernateAccount.setPhone(PHONE);
        hibernateAccount.setEmailVerified(Boolean.TRUE);
        hibernateAccount.setPhoneVerified(Boolean.FALSE);
        hibernateAccount.setCreatedOn(CREATED_ON.getMillis());
        hibernateAccount.setHealthCode(HEALTH_CODE);
        hibernateAccount.setHealthId(HEALTH_ID);
        hibernateAccount.setFirstName(FIRST_NAME);
        hibernateAccount.setLastName(LAST_NAME);
        hibernateAccount.setPasswordAlgorithm(PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM);
        hibernateAccount.setPasswordHash(DUMMY_PASSWORD_HASH);
        hibernateAccount.setPasswordModifiedOn(CREATED_ON.getMillis());
        hibernateAccount.setReauthTokenAlgorithm(PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM);
        hibernateAccount.setReauthTokenHash(DUMMY_REAUTH_TOKEN_HASH);
        hibernateAccount.setReauthTokenModifiedOn(CREATED_ON.getMillis());
        hibernateAccount.setRoles(EnumSet.of(Roles.DEVELOPER, Roles.RESEARCHER));
        hibernateAccount.setStatus(AccountStatus.ENABLED);
        hibernateAccount.setClientData(TestUtils.getClientData().toString());
        hibernateAccount.setVersion(VERSION);
        hibernateAccount.setTimeZone("-07:00");
        hibernateAccount.setSharingScope(SharingScope.ALL_QUALIFIED_RESEARCHERS);
        hibernateAccount.setNotifyByEmail(true);
        hibernateAccount.setExternalId(EXTERNAL_ID);
        hibernateAccount.setDataGroups(TestConstants.USER_DATA_GROUPS);
        hibernateAccount.setLanguages(Lists.newArrayList(TestConstants.LANGUAGES));
        hibernateAccount.setMigrationVersion(AccountDao.MIGRATION_VERSION);         

        // populate attributes
        hibernateAccount.getAttributes().put("foo-attr", "foo-value");
        hibernateAccount.getAttributes().put("bar-attr", "bar-value");

        // populate HibernateAccount with consents, 2 subpops, 2 consents each.
        SubpopulationGuid fooSubpopGuid = SubpopulationGuid.create("foo-subpop-guid");
        SubpopulationGuid barSubpopGuid = SubpopulationGuid.create("bar-subpop-guid");

        long fooSignedOn1 = 1111;
        long fooSignedOn2 = 2222;
        long barSignedOn3 = 3333;
        long barSignedOn4 = 4444;

        HibernateAccountConsent fooHibernateConsent1 = new HibernateAccountConsent();
        fooHibernateConsent1.setBirthdate("1999-01-01");
        fooHibernateConsent1.setConsentCreatedOn(1000);
        fooHibernateConsent1.setName("One McFooface");
        hibernateAccount.getConsents().put(new HibernateAccountConsentKey(fooSubpopGuid.getGuid(), fooSignedOn1),
                fooHibernateConsent1);

        HibernateAccountConsent fooHibernateConsent2 = new HibernateAccountConsent();
        fooHibernateConsent2.setBirthdate("1999-02-02");
        fooHibernateConsent2.setConsentCreatedOn(2000);
        fooHibernateConsent2.setName("Two McFooface");
        fooHibernateConsent2.setWithdrewOn(2777L);
        hibernateAccount.getConsents().put(new HibernateAccountConsentKey(fooSubpopGuid.getGuid(), fooSignedOn2),
                fooHibernateConsent2);

        HibernateAccountConsent barHibernateConsent3 = new HibernateAccountConsent();
        barHibernateConsent3.setBirthdate("1999-03-03");
        barHibernateConsent3.setConsentCreatedOn(3000);
        barHibernateConsent3.setName("Three McBarface");
        hibernateAccount.getConsents().put(new HibernateAccountConsentKey(barSubpopGuid.getGuid(), barSignedOn3),
                barHibernateConsent3);

        HibernateAccountConsent barHibernateConsent4 = new HibernateAccountConsent();
        barHibernateConsent4.setBirthdate("1999-04-04");
        barHibernateConsent4.setConsentCreatedOn(4000);
        barHibernateConsent4.setName("Four McBarface");
        barHibernateConsent4.setSignatureImageData("dummy-image-data");
        barHibernateConsent4.setSignatureImageMimeType("image/dummy");
        hibernateAccount.getConsents().put(new HibernateAccountConsentKey(barSubpopGuid.getGuid(), barSignedOn4),
                barHibernateConsent4);

        // unmarshall
        GenericAccount genericAccount = (GenericAccount) HibernateAccountDao.unmarshallAccount(hibernateAccount);
        assertEquals(ACCOUNT_ID, genericAccount.getId());
        assertEquals(TestConstants.TEST_STUDY, genericAccount.getStudyIdentifier());
        assertEquals(EMAIL, genericAccount.getEmail());
        assertEquals(PHONE.getNationalFormat(), genericAccount.getPhone().getNationalFormat());
        assertEquals(Boolean.TRUE, genericAccount.getEmailVerified());
        assertEquals(Boolean.FALSE, genericAccount.getPhoneVerified());
        assertEquals(HEALTH_CODE, genericAccount.getHealthCode());
        assertEquals(HEALTH_ID, genericAccount.getHealthId());
        assertEquals(FIRST_NAME, genericAccount.getFirstName());
        assertEquals(LAST_NAME, genericAccount.getLastName());
        assertEquals(PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM, genericAccount.getPasswordAlgorithm());
        assertEquals(DUMMY_PASSWORD_HASH, genericAccount.getPasswordHash());
        assertEquals(new Long(CREATED_ON.getMillis()), genericAccount.getPasswordModifiedOn());
        assertEquals(PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM, genericAccount.getReauthTokenAlgorithm());
        assertEquals(DUMMY_REAUTH_TOKEN_HASH, genericAccount.getReauthTokenHash());
        assertEquals(new Long(CREATED_ON.getMillis()), genericAccount.getReauthTokenModifiedOn());
        assertEquals(EnumSet.of(Roles.DEVELOPER, Roles.RESEARCHER), genericAccount.getRoles());
        assertEquals(AccountStatus.ENABLED, genericAccount.getStatus());
        assertEquals(TestUtils.getClientData(), genericAccount.getClientData());
        assertEquals(VERSION, genericAccount.getVersion());
        assertEquals(DateTimeZone.forOffsetHours(-7), genericAccount.getTimeZone());
        assertEquals(SharingScope.ALL_QUALIFIED_RESEARCHERS, genericAccount.getSharingScope());
        assertEquals(Boolean.TRUE, genericAccount.getNotifyByEmail());
        assertEquals(EXTERNAL_ID, genericAccount.getExternalId());
        assertEquals(TestConstants.USER_DATA_GROUPS, genericAccount.getDataGroups());
        assertEquals(TestConstants.LANGUAGES, genericAccount.getLanguages());
        assertEquals(AccountDao.MIGRATION_VERSION, genericAccount.getMigrationVersion());

        // createdOn is stored as a long, so just compare epoch milliseconds.
        assertEquals(CREATED_ON.getMillis(), genericAccount.getCreatedOn().getMillis());

        // validate attributes
        assertEquals(ImmutableSet.of("foo-attr", "bar-attr"), genericAccount.getAttributeNameSet());
        assertEquals("foo-value", genericAccount.getAttribute("foo-attr"));
        assertEquals("bar-value", genericAccount.getAttribute("bar-attr"));

        // validate consents - They are sorted by signedOn.
        Map<SubpopulationGuid, List<ConsentSignature>> genericConsentsBySubpop = genericAccount
                .getAllConsentSignatureHistories();
        assertEquals(2, genericConsentsBySubpop.size());

        List<ConsentSignature> fooConsentSignatureList = genericConsentsBySubpop.get(fooSubpopGuid);
        assertEquals(2, fooConsentSignatureList.size());
        validateGenericConsent(fooSignedOn1, fooHibernateConsent1, fooConsentSignatureList.get(0));
        validateGenericConsent(fooSignedOn2, fooHibernateConsent2, fooConsentSignatureList.get(1));

        List<ConsentSignature> barConsentSignatureList = genericConsentsBySubpop.get(barSubpopGuid);
        assertEquals(2, barConsentSignatureList.size());
        validateGenericConsent(barSignedOn3, barHibernateConsent3, barConsentSignatureList.get(0));
        validateGenericConsent(barSignedOn4, barHibernateConsent4, barConsentSignatureList.get(1));
    }
    
    @Test
    public void unmarshallDefaults() {
        HibernateAccount hibernateAccount = new HibernateAccount();
        GenericAccount genericAccount = (GenericAccount) HibernateAccountDao.unmarshallAccount(hibernateAccount);
        
        assertTrue(genericAccount.getNotifyByEmail());
        assertEquals(SharingScope.NO_SHARING, genericAccount.getSharingScope());
    }

    // branch coverage, to make sure nothing crashes.
    @Test
    public void unmarshallBlankAccount() {
        Account account = HibernateAccountDao.unmarshallAccount(new HibernateAccount());
        assertNotNull(account);
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
        hibernateAccount.setCreatedOn(CREATED_ON.getMillis());
        hibernateAccount.setStatus(AccountStatus.ENABLED);

        // Unmarshall
        AccountSummary accountSummary = HibernateAccountDao.unmarshallAccountSummary(hibernateAccount);
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
    public void unmarshallAccountSummaryBlankAccount() {
        AccountSummary accountSummary = HibernateAccountDao.unmarshallAccountSummary(new HibernateAccount());
        assertNotNull(accountSummary);
    }
    
    @Test
    public void legacyAccountsWithoutEmailVerificationAreFixed() {
        HibernateAccount hibernateAccount = new HibernateAccount();
        hibernateAccount.setStatus(AccountStatus.ENABLED);
        
        Account account = HibernateAccountDao.unmarshallAccount(hibernateAccount);
        assertEquals(AccountStatus.ENABLED, account.getStatus());
        assertEquals(Boolean.TRUE, account.getEmailVerified());
        
        hibernateAccount = new HibernateAccount();
        hibernateAccount.setStatus(AccountStatus.UNVERIFIED);
        
        account = HibernateAccountDao.unmarshallAccount(hibernateAccount);
        assertEquals(AccountStatus.UNVERIFIED, account.getStatus());
        assertEquals(Boolean.FALSE, account.getEmailVerified());
    }
    
    @Test
    public void unmarshallAccountWithEmailVerifiedSet() {
        HibernateAccount hibernateAccount = new HibernateAccount();
        hibernateAccount.setStatus(AccountStatus.ENABLED);
        // Because this is set, it will not be changed by legacy fix
        hibernateAccount.setEmailVerified(Boolean.FALSE);
        
        Account account = HibernateAccountDao.unmarshallAccount(hibernateAccount);
        assertEquals(AccountStatus.ENABLED, account.getStatus());
        assertEquals(Boolean.FALSE, account.getEmailVerified());
    }
    
    @Test
    public void editAccountSuccess() throws Exception {
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false, false);
        // To prevent two updates during test, enter healthCode/healthId
        hibernateAccount.setHealthCode("A");
        hibernateAccount.setHealthId("B");
        // mock hibernate
        when(mockHibernateHelper.queryGet("from HibernateAccount where studyId=:studyId and healthCode=:healthCode",
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
    public void editAccountCannotChangeSensitiveFields() throws Exception {
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false, false);
        // To prevent two updates during test, enter healthCode/healthId
        hibernateAccount.setHealthCode("A");
        hibernateAccount.setHealthId("B");
        // mock hibernate
        when(mockHibernateHelper.queryGet("from HibernateAccount where studyId=:studyId and healthCode=:healthCode",
                HEALTHCODE_QUERY_PARAMS, null, null, HibernateAccount.class))
                        .thenReturn(ImmutableList.of(hibernateAccount));
        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(hibernateAccount);

        // execute and validate
        dao.editAccount(TestConstants.TEST_STUDY, HEALTH_CODE, account -> account.setEmail("JUNK"));
        
        ArgumentCaptor<HibernateAccount> updatedAccountCaptor = ArgumentCaptor.forClass(HibernateAccount.class);
        verify(mockHibernateHelper).update(updatedAccountCaptor.capture());
        
        assertEquals(EMAIL, updatedAccountCaptor.getValue().getEmail());
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
        
        String query = dao.assembleSearchQuery("api", search, new HashMap<>());
        assertEquals("from HibernateAccount as acct where studyId=:studyId", query);
    }

    @Test
    public void languageQueryCorrect() throws Exception {
        AccountSummarySearch search = new AccountSummarySearch.Builder().withLanguage("en").build();
        
        String query = dao.assembleSearchQuery("api", search, new HashMap<>());
        assertEquals("from HibernateAccount as acct where studyId=:studyId and :language in elements(acct.languages)", query);
    }

    @Test
    public void groupClausesGroupedCorrectly() throws Exception {
        AccountSummarySearch search = new AccountSummarySearch.Builder()
                .withNoneOfGroups(Sets.newHashSet("sdk-int-1"))
                .withAllOfGroups(Sets.newHashSet("group1")).build();
        
        String query = dao.assembleSearchQuery("api", search, new HashMap<>());
        assertEquals("from HibernateAccount as acct where studyId=:studyId and (:in1 in elements(acct.dataGroups)) "+
                "and (:notin1 not in elements(acct.dataGroups))", query);
    }
    
    @Test
    public void oneAllOfGroupsQueryCorrect() throws Exception {
        AccountSummarySearch search = new AccountSummarySearch.Builder()
                .withAllOfGroups(Sets.newHashSet("group1")).build();
        
        String query = dao.assembleSearchQuery("api", search, new HashMap<>());
        assertEquals("from HibernateAccount as acct where studyId=:studyId and (:in1 in elements(acct.dataGroups))", query);
    }
    
    @Test
    public void twoAllOfGroupsQueryCorrect() throws Exception {
        AccountSummarySearch search = new AccountSummarySearch.Builder()
                .withAllOfGroups(Sets.newHashSet("sdk-int-1", "group1")).build();
        
        String query = dao.assembleSearchQuery("api", search, new HashMap<>());
        assertEquals("from HibernateAccount as acct where studyId=:studyId and (:in2 in "+
                "elements(acct.dataGroups) and :in1 in elements(acct.dataGroups))", query);
    }

    @Test
    public void oneNoneOfGroupsQueryCorrect() throws Exception {
        AccountSummarySearch search = new AccountSummarySearch.Builder()
                .withNoneOfGroups(Sets.newHashSet("group1")).build();
        
        String query = dao.assembleSearchQuery("api", search, new HashMap<>());
        assertEquals("from HibernateAccount as acct where studyId=:studyId and (:notin1 not in "+
                "elements(acct.dataGroups))", query);
    }
    
    @Test
    public void twoNoneOfGroupsQueryCorrect() throws Exception {
        AccountSummarySearch search = new AccountSummarySearch.Builder()
                .withNoneOfGroups(Sets.newHashSet("sdk-int-1", "group1")).build();
        
        String query = dao.assembleSearchQuery("api", search, new HashMap<>());
        assertTrue(query.contains("from HibernateAccount as acct where studyId=:studyId and "));
        assertTrue(query.contains(":notin1 not in elements(acct.dataGroups)"));
        assertTrue(query.contains(":notin2 not in elements(acct.dataGroups)"));
    }
    
    public void authenticateAccountUnverifiedEmailSucceedsForLegacy() throws Exception {
        HibernateAccount hibernateAccount = makeValidHibernateAccount(true, true);
        hibernateAccount.setEmailVerified(false);
        
        // mock hibernate
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), any()))
                .thenReturn(ImmutableList.of(hibernateAccount));

        dao.authenticate(study, PASSWORD_SIGNIN);
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
    
    public void authenticateAccountUnverifiedPhoneSucceedsForLegacy() throws Exception {
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
    
    // This test (when applied to HibernateHelper) used to validate a conversion to 
    // ConcurrentModificationException, but now we retain the constraint violation 
    // exception and convert it to the Bridge namespace.
    @Test(expected = org.sagebionetworks.bridge.exceptions.ConstraintViolationException.class)
    public void genericConstraintViolationOnCreate() {
        PersistenceException ex = new PersistenceException(mock(ConstraintViolationException.class));
        doThrow(ex).when(mockHibernateHelper).create(any());
        
        GenericAccount account = makeValidGenericAccount();
        dao.createAccount(study, account);
    }
    
    @Test(expected = org.sagebionetworks.bridge.exceptions.ConstraintViolationException.class)
    public void genericConstraintViolationOnUpdate() throws Exception {
        PersistenceException ex = new PersistenceException(mock(ConstraintViolationException.class));
        doThrow(ex).when(mockHibernateHelper).update(any());
        
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false, false);
        when(mockHibernateHelper.getById(eq(HibernateAccount.class), any())).thenReturn(hibernateAccount);

        GenericAccount account = makeValidGenericAccount();
        dao.updateAccount(account, true);
    }
    
    @Test
    public void emailConstraintViolationOnCreate() throws Exception {
        ConstraintViolationException cve = new ConstraintViolationException(
                "Duplicate entry ABC for key 'Accounts-StudyId-Email-Index'", null, null);
        PersistenceException ex = new PersistenceException(cve);
        doThrow(ex).when(mockHibernateHelper).create(any());
     
        GenericAccount account = makeValidGenericAccount();
        account.setId("id");
        
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false, false);
        hibernateAccount.setId("id2");
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), eq(HibernateAccount.class)))
                .thenReturn(ImmutableList.of(hibernateAccount));
        try {
            dao.createAccount(study, account);
            fail("Should have thrown an exception");
        } catch(EntityAlreadyExistsException e) {
            assertEquals("Email address has already been used by another account.", e.getMessage());
            assertEquals("id2", e.getEntityKeys().get("userId"));
        }
    }
    
    @Test
    public void emailConstraintViolationOnUpdate() throws Exception {
        ConstraintViolationException cve = new ConstraintViolationException(
                "Duplicate entry ABC for key 'Accounts-StudyId-Email-Index'", null, null);
        PersistenceException ex = new PersistenceException(cve);
        doThrow(ex).when(mockHibernateHelper).update(any());
        
        GenericAccount account = makeValidGenericAccount();
        account.setId("id");
        
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false, false);
        hibernateAccount.setId("id2");
        when(mockHibernateHelper.getById(eq(HibernateAccount.class), any())).thenReturn(hibernateAccount);
        
        try {
            dao.updateAccount(account, true);
            fail("Should have thrown an exception");
        } catch(EntityAlreadyExistsException e) {
            assertEquals("Email address has already been used by another account.", e.getMessage());
            assertEquals("id2", e.getEntityKeys().get("userId"));
        }
    }
    
    @Test
    public void phoneConstraintViolationOnCreate() throws Exception {
        ConstraintViolationException cve = new ConstraintViolationException(
                "Duplicate entry ABC for key 'Accounts-StudyId-Phone-Index'", null, null);
        PersistenceException ex = new PersistenceException(cve);
        doThrow(ex).when(mockHibernateHelper).create(any());
     
        GenericAccount account = makeValidGenericAccount();
        account.setId("id");
        
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false, false);
        hibernateAccount.setId("id2");
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), eq(HibernateAccount.class)))
                .thenReturn(ImmutableList.of(hibernateAccount));
        try {
            dao.createAccount(study, account);
            fail("Should have thrown an exception");
        } catch(EntityAlreadyExistsException e) {
            assertEquals("Phone number has already been used by another account.", e.getMessage());
            assertEquals("id2", e.getEntityKeys().get("userId"));
        }
    }
    
    @Test
    public void phoneConstraintViolationOnUpdate() throws Exception {
        ConstraintViolationException cve = new ConstraintViolationException(
                "Duplicate entry ABC for key 'Accounts-StudyId-Phone-Index'", null, null);
        PersistenceException ex = new PersistenceException(cve);
        doThrow(ex).when(mockHibernateHelper).update(any());
        
        GenericAccount account = makeValidGenericAccount();
        account.setId("id");
        
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false, false);
        hibernateAccount.setId("id2");
        when(mockHibernateHelper.getById(eq(HibernateAccount.class), any())).thenReturn(hibernateAccount);
        
        try {
            dao.updateAccount(account, true);
            fail("Should have thrown an exception");
        } catch(EntityAlreadyExistsException e) {
            assertEquals("Phone number has already been used by another account.", e.getMessage());
            assertEquals("id2", e.getEntityKeys().get("userId"));
        }
    }
    
    @Test
    public void externalIdConstraintViolationOnCreate() throws Exception {
        ConstraintViolationException cve = new ConstraintViolationException(
                "Duplicate entry ABC for key 'Accounts-StudyId-ExternalId-Index'", null, null);
        PersistenceException ex = new PersistenceException(cve);
        doThrow(ex).when(mockHibernateHelper).create(any());
     
        GenericAccount account = makeValidGenericAccount();
        account.setId("id");
        
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false, false);
        hibernateAccount.setId("id2");
        when(mockHibernateHelper.queryGet(any(), any(), any(), any(), eq(HibernateAccount.class)))
                .thenReturn(ImmutableList.of(hibernateAccount));
        try {
            dao.createAccount(study, account);
            fail("Should have thrown an exception");
        } catch(EntityAlreadyExistsException e) {
            assertEquals("External ID has already been used by another account.", e.getMessage());
            assertEquals("id2", e.getEntityKeys().get("userId"));
        }
    }
    
    @Test
    public void externalIdConstraintViolationOnUpdate() throws Exception {
        ConstraintViolationException cve = new ConstraintViolationException(
                "Duplicate entry ABC for key 'Accounts-StudyId-ExternalId-Index'", null, null);
        PersistenceException ex = new PersistenceException(cve);
        doThrow(ex).when(mockHibernateHelper).update(any());
        
        GenericAccount account = makeValidGenericAccount();
        account.setId("id");
        
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false, false);
        hibernateAccount.setId("id2");
        when(mockHibernateHelper.getById(eq(HibernateAccount.class), any())).thenReturn(hibernateAccount);
        
        try {
            dao.updateAccount(account, true);
            fail("Should have thrown an exception");
        } catch(EntityAlreadyExistsException e) {
            assertEquals("External ID has already been used by another account.", e.getMessage());
            assertEquals("id2", e.getEntityKeys().get("userId"));
        }
    }    
    
    @Test(expected = ConcurrentModificationException.class)
    public void updateConcurrentModification() throws Exception {
        PersistenceException ex = new javax.persistence.OptimisticLockException();
        doThrow(ex).when(mockHibernateHelper).update(any());
        
        when(mockHibernateHelper.getById(eq(HibernateAccount.class), any()))
                .thenReturn(makeValidHibernateAccount(false, false));
        
        GenericAccount account = makeValidGenericAccount();
        dao.updateAccount(account, true);
    }
    
    @Test(expected = BridgeServiceException.class)
    public void unforeseenPersistenceException() throws Exception {
        PersistenceException ex = new javax.persistence.TransactionRequiredException();
        doThrow(ex).when(mockHibernateHelper).update(any());
        
        when(mockHibernateHelper.getById(eq(HibernateAccount.class), any()))
        .thenReturn(makeValidHibernateAccount(false, false));

        GenericAccount account = makeValidGenericAccount();
        dao.updateAccount(account, true);
    }
    
    private void verifyCreatedHealthCode() {
        // Verify we create the new health code mapping
        verify(mockHealthCodeService).createMapping(TestConstants.TEST_STUDY);

        ArgumentCaptor<HibernateAccount> updatedAccountCaptor = ArgumentCaptor.forClass(HibernateAccount.class);
        verify(mockHibernateHelper).update(updatedAccountCaptor.capture());

        HibernateAccount updatedAccount = updatedAccountCaptor.getValue();
        assertEquals(ACCOUNT_ID, updatedAccount.getId());
        assertEquals(HEALTH_CODE, updatedAccount.getHealthCode());
        assertEquals(HEALTH_ID, updatedAccount.getHealthId());
        assertEquals(MOCK_NOW_MILLIS, updatedAccount.getModifiedOn().longValue());
    }

    private static void validateHibernateConsent(ConsentSignature consentSignature,
            HibernateAccountConsent hibernateAccountConsent) {
        assertEquals(consentSignature.getBirthdate(), hibernateAccountConsent.getBirthdate());
        assertEquals(consentSignature.getConsentCreatedOn(), hibernateAccountConsent.getConsentCreatedOn());
        assertEquals(consentSignature.getName(), hibernateAccountConsent.getName());
        assertEquals(consentSignature.getImageData(), hibernateAccountConsent.getSignatureImageData());
        assertEquals(consentSignature.getImageMimeType(), hibernateAccountConsent.getSignatureImageMimeType());
        assertEquals(consentSignature.getWithdrewOn(), hibernateAccountConsent.getWithdrewOn());
    }

    private static void validateGenericConsent(long signedOn,
            HibernateAccountConsent hibernateConsent, ConsentSignature consentSignature) {
        assertEquals(hibernateConsent.getName(), consentSignature.getName());
        assertEquals(hibernateConsent.getBirthdate(), consentSignature.getBirthdate());
        assertEquals(hibernateConsent.getSignatureImageData(), consentSignature.getImageData());
        assertEquals(hibernateConsent.getSignatureImageMimeType(), consentSignature.getImageMimeType());
        assertEquals(hibernateConsent.getConsentCreatedOn(), consentSignature.getConsentCreatedOn());
        assertEquals(signedOn, consentSignature.getSignedOn());
        assertEquals(hibernateConsent.getWithdrewOn(), consentSignature.getWithdrewOn());
    }

    // Create minimal generic account for everything that will be used by HibernateAccountDao.
    private static GenericAccount makeValidGenericAccount() {
        GenericAccount genericAccount = new GenericAccount();
        genericAccount.setId(ACCOUNT_ID);
        genericAccount.setStudyId(TestConstants.TEST_STUDY);
        genericAccount.setEmail(EMAIL);
        genericAccount.setStatus(AccountStatus.UNVERIFIED);
        return genericAccount;
    }

    // Create minimal Hibernate account for everything that will be used by HibernateAccountDao.
    private static HibernateAccount makeValidHibernateAccount(boolean generatePasswordHash, boolean generateReauthHash) throws Exception {
        HibernateAccount hibernateAccount = new HibernateAccount();
        hibernateAccount.setId(ACCOUNT_ID);
        hibernateAccount.setHealthId(HEALTH_ID);
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
