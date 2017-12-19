package org.sagebionetworks.bridge.hibernate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.exceptions.AccountDisabledException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.AccountStatus;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.EmailVerification;
import org.sagebionetworks.bridge.models.accounts.GenericAccount;
import org.sagebionetworks.bridge.models.accounts.HealthId;
import org.sagebionetworks.bridge.models.accounts.HealthIdImpl;
import org.sagebionetworks.bridge.models.accounts.PasswordAlgorithm;
import org.sagebionetworks.bridge.models.accounts.PasswordReset;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.AccountWorkflowService;
import org.sagebionetworks.bridge.services.AuthenticationService;
import org.sagebionetworks.bridge.services.HealthCodeService;

public class HibernateAccountDaoTest {
    private static final String ACCOUNT_ID = "account-id";
    private static final DateTime CREATED_ON = DateTime.parse("2017-05-19T11:03:50.224-0700");
    private static final String DUMMY_PASSWORD = "Aa!Aa!Aa!Aa!1";
    private static final String DUMMY_PASSWORD_HASH = "dummy-password-hash";
    private static final String DUMMY_TOKEN = "dummy-token";
    private static final String EMAIL = "eggplant@example.com";
    private static final Phone PHONE = TestConstants.PHONE;
    private static final Phone OTHER_PHONE = new Phone("+12065881469", "US");
    private static final String HEALTH_CODE = "health-code";
    private static final String HEALTH_ID = "health-id";
    private static final long MOCK_NOW_MILLIS = DateTime.parse("2017-05-19T14:45:27.593-0700").getMillis();
    private static final String FIRST_NAME = "Eggplant";
    private static final String LAST_NAME = "McTester";
    private static final String REAUTH_TOKEN = "reauth-token";
    private static final int VERSION = 7;
    private static final AccountId ACCOUNT_ID_WITH_ID = AccountId.forId(TestConstants.TEST_STUDY_IDENTIFIER, ACCOUNT_ID);
    private static final AccountId ACCOUNT_ID_WITH_EMAIL = AccountId.forEmail(TestConstants.TEST_STUDY_IDENTIFIER, EMAIL);
    private static final AccountId ACCOUNT_ID_WITH_PHONE = AccountId.forPhone(TestConstants.TEST_STUDY_IDENTIFIER, TestConstants.PHONE);
    
    private static final SignIn REAUTH_SIGNIN = new SignIn.Builder().withStudy(TestConstants.TEST_STUDY_IDENTIFIER)
            .withEmail(EMAIL).withReauthToken(REAUTH_TOKEN).build();
    private static final SignIn PASSWORD_SIGNIN = new SignIn.Builder().withStudy(TestConstants.TEST_STUDY_IDENTIFIER)
            .withEmail(EMAIL).withPassword(DUMMY_PASSWORD).build();

    private static final Study STUDY;
    static {
        STUDY = Study.create();
        STUDY.setIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);
    }

    private AccountWorkflowService mockAccountWorkflowService;
    private HealthCodeService mockHealthCodeService;
    private HibernateAccountDao dao;
    private HibernateHelper mockHibernateHelper;

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
        mockAccountWorkflowService = mock(AccountWorkflowService.class);
        mockHealthCodeService = mock(HealthCodeService.class);
        mockHibernateHelper = mock(HibernateHelper.class);
        dao = new HibernateAccountDao();
        dao.setAccountWorkflowService(mockAccountWorkflowService);
        dao.setHealthCodeService(mockHealthCodeService);
        dao.setHibernateHelper(mockHibernateHelper);
        
        when(mockHealthCodeService.createMapping(TestConstants.TEST_STUDY)).thenReturn(new HealthIdImpl(HEALTH_ID,
                HEALTH_CODE));
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
        
        when(mockAccountWorkflowService.verifyEmail(any())).thenReturn(account);
        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(hibernateAccount);
        
        EmailVerification verification = new EmailVerification(DUMMY_TOKEN);
        dao.verifyEmail(verification);
        
        verify(mockAccountWorkflowService).verifyEmail(verification);
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
        
        when(mockAccountWorkflowService.verifyEmail(any())).thenReturn(account);
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
        
        when(mockAccountWorkflowService.verifyEmail(any())).thenReturn(account);
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

        }
        verify(mockHibernateHelper, never()).update(any());
        assertEquals(AccountStatus.UNVERIFIED, account.getStatus());
        assertNull(account.getEmailVerified());
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
        
        when(mockAccountWorkflowService.verifyEmail(any())).thenReturn(account);
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
        
        when(mockAccountWorkflowService.verifyEmail(any())).thenReturn(account);
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

        }
        verify(mockHibernateHelper, never()).update(any());
        assertEquals(AccountStatus.UNVERIFIED, account.getStatus());
        assertNull(account.getPhoneVerified());
    }    
    
    @Test
    public void resendEmailVerificationToken() {
        dao.resendEmailVerificationToken(ACCOUNT_ID_WITH_EMAIL);
        verify(mockAccountWorkflowService).resendEmailVerificationToken(ACCOUNT_ID_WITH_EMAIL);
    }

    @Test
    public void requestResetPassword() {
        dao.requestResetPassword(STUDY, ACCOUNT_ID_WITH_EMAIL);
        verify(mockAccountWorkflowService).requestResetPassword(STUDY, ACCOUNT_ID_WITH_EMAIL);
    }

    @Test
    public void resetPassword() {
        PasswordReset passwordReset = new PasswordReset(DUMMY_PASSWORD, DUMMY_TOKEN,
                TestConstants.TEST_STUDY_IDENTIFIER);
        dao.resetPassword(passwordReset);
        verify(mockAccountWorkflowService).resetPassword(passwordReset);
    }

    @Test
    public void changePasswordSuccess() throws Exception {
        // mock hibernate
        HibernateAccount hibernateAccount = new HibernateAccount();
        hibernateAccount.setId(ACCOUNT_ID);
        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(hibernateAccount);

        // Set up test account
        GenericAccount account = new GenericAccount();
        account.setId(ACCOUNT_ID);

        // execute and verify
        dao.changePassword(account, DUMMY_PASSWORD);
        ArgumentCaptor<HibernateAccount> updatedAccountCaptor = ArgumentCaptor.forClass(HibernateAccount.class);
        verify(mockHibernateHelper).update(updatedAccountCaptor.capture());

        HibernateAccount updatedAccount = updatedAccountCaptor.getValue();
        assertEquals(ACCOUNT_ID, updatedAccount.getId());
        assertEquals(MOCK_NOW_MILLIS, updatedAccount.getModifiedOn().longValue());
        assertEquals(PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM, updatedAccount.getPasswordAlgorithm());
        assertEquals(MOCK_NOW_MILLIS, updatedAccount.getPasswordModifiedOn().longValue());

        // validate password hash
        assertTrue(PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM.checkHash(updatedAccount.getPasswordHash(),
                DUMMY_PASSWORD));
    }

    @Test(expected = EntityNotFoundException.class)
    public void changePasswordAccountNotFound() {
        // mock hibernate
        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(null);

        // Set up test account
        GenericAccount account = new GenericAccount();
        account.setId(ACCOUNT_ID);

        // execute
        dao.changePassword(account, DUMMY_PASSWORD);
    }

    @Test
    public void authenticateSuccessWithHealthCode() throws Exception {
        // mock hibernate
        HibernateAccount hibernateAccount = makeValidHibernateAccount(true, false);
        hibernateAccount.setHealthCode("original-" + HEALTH_CODE);
        hibernateAccount.setHealthId("original-" + HEALTH_ID);
        when(mockHibernateHelper.queryGet(any(), any(), any(), any())).thenReturn(ImmutableList.of(hibernateAccount));

        String originalReauthTokenHash = hibernateAccount.getReauthTokenHash();
        
        // execute and verify - Verify just ID, study, and email, and health code mapping is enough.
        GenericAccount account = (GenericAccount) dao.authenticate(STUDY, PASSWORD_SIGNIN);
        assertEquals(ACCOUNT_ID, account.getId());
        assertEquals(TestConstants.TEST_STUDY, account.getStudyIdentifier());
        assertEquals(EMAIL, account.getEmail());
        assertEquals("original-" + HEALTH_CODE, account.getHealthCode());
        assertEquals("original-" + HEALTH_ID, account.getHealthId());
        assertNotNull(account.getReauthToken());
        assertNotEquals(originalReauthTokenHash, account.getReauthToken());
        
        // verify query
        verify(mockHibernateHelper).queryGet("from HibernateAccount where studyId='" +
                TestConstants.TEST_STUDY_IDENTIFIER + "' and email='" + EMAIL + "'", null, null,
                HibernateAccount.class);
        
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
        when(mockHibernateHelper.queryGet(any(), any(), any(), any()))
                .thenReturn(ImmutableList.of(makeValidHibernateAccount(true, false)));

        // execute and verify - Verify just ID, study, and email, and health code mapping is enough.
        GenericAccount account = (GenericAccount) dao.authenticate(STUDY, PASSWORD_SIGNIN);
        assertEquals(ACCOUNT_ID, account.getId());
        assertEquals(TestConstants.TEST_STUDY, account.getStudyIdentifier());
        assertEquals(EMAIL, account.getEmail());
        assertEquals(HEALTH_CODE, account.getHealthCode());
        assertEquals(HEALTH_ID, account.getHealthId());

        // verify query
        verify(mockHibernateHelper).queryGet("from HibernateAccount where studyId='" +
                        TestConstants.TEST_STUDY_IDENTIFIER + "' and email='" + EMAIL + "'", null, null,
                HibernateAccount.class);
        verifyCreatedHealthCode();
    }

    @Test(expected = EntityNotFoundException.class)
    public void authenticateAccountNotFound() throws Exception {
        // mock hibernate
        when(mockHibernateHelper.queryGet(any(), any(), any(), any())).thenReturn(ImmutableList.of());

        // execute
        dao.authenticate(STUDY, PASSWORD_SIGNIN);
    }

    @Test(expected = UnauthorizedException.class)
    public void authenticateAccountUnverified() throws Exception {
        // mock hibernate
        HibernateAccount hibernateAccount = makeValidHibernateAccount(true, false);
        hibernateAccount.setStatus(AccountStatus.UNVERIFIED);
        when(mockHibernateHelper.queryGet(any(), any(), any(), any())).thenReturn(ImmutableList.of(hibernateAccount));

        // execute
        dao.authenticate(STUDY, PASSWORD_SIGNIN);
    }

    @Test(expected = AccountDisabledException.class)
    public void authenticateAccountDisabled() throws Exception {
        // mock hibernate
        HibernateAccount hibernateAccount = makeValidHibernateAccount(true, false);
        hibernateAccount.setStatus(AccountStatus.DISABLED);
        when(mockHibernateHelper.queryGet(any(), any(), any(), any())).thenReturn(ImmutableList.of(hibernateAccount));

        // execute
        dao.authenticate(STUDY, PASSWORD_SIGNIN);
    }

    @Test(expected = EntityNotFoundException.class)
    public void authenticateAccountHasNoPassword() throws Exception {
        // mock hibernate
        when(mockHibernateHelper.queryGet(any(), any(), any(), any())).thenReturn(
                ImmutableList.of(makeValidHibernateAccount(false, false)));

        // execute
        dao.authenticate(STUDY, PASSWORD_SIGNIN);
    }

    // branch coverage
    @Test(expected = EntityNotFoundException.class)
    public void authenticateAccountHasPasswordAlgorithmNoHash() throws Exception {
        // mock hibernate
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false, false);
        hibernateAccount.setPasswordAlgorithm(PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM);
        when(mockHibernateHelper.queryGet(any(), any(), any(), any())).thenReturn(ImmutableList.of(hibernateAccount));

        // execute
        dao.authenticate(STUDY, PASSWORD_SIGNIN);
    }

    @Test(expected = EntityNotFoundException.class)
    public void authenticateBadPassword() throws Exception {
        // mock hibernate
        when(mockHibernateHelper.queryGet(any(), any(), any(), any())).thenReturn(ImmutableList.of(
                makeValidHibernateAccount(true, false)));

        // execute
        dao.authenticate(STUDY, new SignIn.Builder().withStudy(TestConstants.TEST_STUDY_IDENTIFIER).withEmail(EMAIL)
                .withPassword("wrong password").build());
    }

    @Test
    public void reauthenticateSuccess() throws Exception {
        // mock hibernate
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false, true);
        when(mockHibernateHelper.queryGet(any(), any(), any(), any())).thenReturn(ImmutableList.of(hibernateAccount));
        String originalReauthTokenHash = hibernateAccount.getReauthTokenHash();

        // execute and verify - Verify just ID, study, and email, and health code mapping is enough.
        GenericAccount account = (GenericAccount) dao.reauthenticate(STUDY, REAUTH_SIGNIN);
        assertEquals(ACCOUNT_ID, account.getId());
        assertEquals(TestConstants.TEST_STUDY, account.getStudyIdentifier());
        assertEquals(EMAIL, account.getEmail());
        assertNotEquals(originalReauthTokenHash, account.getReauthToken());

        // verify query
        verify(mockHibernateHelper).queryGet("from HibernateAccount where studyId='" +
                TestConstants.TEST_STUDY_IDENTIFIER + "' and email='" + EMAIL + "'", null, null,
                HibernateAccount.class);

        // We update the account with a reauthentication token
        verify(mockHibernateHelper).update(hibernateAccount);
        // The hash has been changed
        assertNotEquals(originalReauthTokenHash, hibernateAccount.getReauthTokenHash());
        // This has been hashed
        assertNotEquals(account.getReauthToken(), hibernateAccount.getReauthTokenHash());
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void reauthenticateAccountNotFound() throws Exception {
        // mock hibernate
        when(mockHibernateHelper.queryGet(any(), any(), any(), any())).thenReturn(ImmutableList.of());

        // execute
        dao.reauthenticate(STUDY, REAUTH_SIGNIN);
    }
    
    @Test(expected = UnauthorizedException.class)
    public void reauthenticateAccountUnverified() throws Exception {
        // mock hibernate
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false, true);
        hibernateAccount.setStatus(AccountStatus.UNVERIFIED);
        when(mockHibernateHelper.queryGet(any(), any(), any(), any())).thenReturn(ImmutableList.of(hibernateAccount));

        // execute
        dao.reauthenticate(STUDY, REAUTH_SIGNIN);
    }
    
    @Test(expected = AccountDisabledException.class)
    public void reauthenticateAccountDisabled() throws Exception {
        // mock hibernate
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false, true);
        hibernateAccount.setStatus(AccountStatus.DISABLED);
        when(mockHibernateHelper.queryGet(any(), any(), any(), any())).thenReturn(ImmutableList.of(hibernateAccount));

        // execute
        dao.reauthenticate(STUDY, REAUTH_SIGNIN);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void reauthenticateAccountHasNoReauthToken() throws Exception {
        // mock hibernate
        when(mockHibernateHelper.queryGet(any(), any(), any(), any())).thenReturn(
                ImmutableList.of(makeValidHibernateAccount(false, false)));

        // execute
        dao.authenticate(STUDY, PASSWORD_SIGNIN);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void failedSignInOfDisabledAccountDoesNotIndicateAccountExists() throws Exception {
        HibernateAccount hibernateAccount = makeValidHibernateAccount(true, false);
        hibernateAccount.setStatus(AccountStatus.DISABLED);
        
        when(mockHibernateHelper.queryGet(any(), any(), any(), any())).thenReturn(
                ImmutableList.of(hibernateAccount));
        
        SignIn signIn = new SignIn.Builder().withStudy(TestConstants.TEST_STUDY_IDENTIFIER)
                .withEmail(EMAIL).withPassword("bad password").build();
        dao.authenticate(STUDY, signIn);
    }
    
    // branch coverage
    @Test(expected = EntityNotFoundException.class)
    public void reauthenticateAccountHasReauthTokenAlgorithmNoHash() throws Exception {
        // mock hibernate
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false, false);
        hibernateAccount.setReauthTokenAlgorithm(PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM);
        when(mockHibernateHelper.queryGet(any(), any(), any(), any())).thenReturn(ImmutableList.of(hibernateAccount));

        // execute
        dao.authenticate(STUDY, REAUTH_SIGNIN);
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void reauthenticateBadPassword() throws Exception {
        // mock hibernate
        when(mockHibernateHelper.queryGet(any(), any(), any(), any())).thenReturn(ImmutableList.of(
                makeValidHibernateAccount(false, true)));

        // execute
        dao.authenticate(STUDY, new SignIn.Builder().withStudy(TestConstants.TEST_STUDY_IDENTIFIER).withEmail(EMAIL)
                .withReauthToken("wrong reauth token").build());
    }

    @Test
    public void getAccountAsAuthenticated() throws Exception {
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false, true);
        String originalReauthTokenHash = hibernateAccount.getReauthTokenHash();
        when(mockHibernateHelper.queryGet(any(), any(), any(), any())).thenReturn(ImmutableList.of(
                hibernateAccount));

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
    public void signOut() throws Exception {
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false, false);
        hibernateAccount.setReauthTokenAlgorithm(PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM);
        hibernateAccount.setReauthTokenHash("AAA");
        hibernateAccount.setReauthTokenModifiedOn(DateTime.now().getMillis());
        when(mockHibernateHelper.queryGet(any(), any(), any(), any())).thenReturn(ImmutableList.of(
                hibernateAccount));
        
        ArgumentCaptor<HibernateAccount> accountCaptor = ArgumentCaptor.forClass(HibernateAccount.class);
        
        dao.signOut(ACCOUNT_ID_WITH_EMAIL);
        
        verify(mockHibernateHelper).update(accountCaptor.capture());
        HibernateAccount captured = accountCaptor.getValue();
        
        assertNull(captured.getReauthTokenAlgorithm());
        assertNull(captured.getReauthTokenHash());
        assertNull(captured.getReauthTokenModifiedOn());
    }
    
    @Test
    public void signOutAccountNotFound() throws Exception {
        // Just quietly succeeds without doing any work.
        dao.signOut(ACCOUNT_ID_WITH_EMAIL);
        
        verify(mockHibernateHelper, never()).update(any());
    }
    
    @Test
    public void getAccountAfterAuthentication() throws Exception {
        Long originalTimestamp = DateTime.now().minusMinutes(2).getMillis();
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false, true);
        hibernateAccount.setReauthTokenAlgorithm(PasswordAlgorithm.BCRYPT);
        hibernateAccount.setReauthTokenHash("AAA");
        hibernateAccount.setReauthTokenModifiedOn(originalTimestamp);
        when(mockHibernateHelper.queryGet(any(), any(), any(), any())).thenReturn(ImmutableList.of(
                hibernateAccount));
        
        ArgumentCaptor<HibernateAccount> accountCaptor = ArgumentCaptor.forClass(HibernateAccount.class);
        
        dao.getAccountAfterAuthentication(ACCOUNT_ID_WITH_EMAIL);
        
        verify(mockHibernateHelper).update(accountCaptor.capture());
        HibernateAccount captured = accountCaptor.getValue();
        
        assertNotEquals(PasswordAlgorithm.BCRYPT, captured.getReauthTokenAlgorithm());
        assertNotEquals("AAA", captured.getReauthTokenHash());
        assertNotEquals(originalTimestamp, captured.getReauthTokenModifiedOn());
    }
    
    @Test
    public void getAccountAfterAuthenticateReturnsNull() throws Exception {
        Account account = dao.getAccountAfterAuthentication(ACCOUNT_ID_WITH_EMAIL);
        assertNull(account);
    }
    
    @Test
    public void constructAccount() throws Exception {
        // execute and validate
        GenericAccount account = (GenericAccount) dao.constructAccount(STUDY, EMAIL, PHONE, DUMMY_PASSWORD);
        assertEquals(TestConstants.TEST_STUDY, account.getStudyIdentifier());
        assertEquals(EMAIL, account.getEmail());
        assertEquals(PHONE.getNationalFormat(), account.getPhone().getNationalFormat());
        assertEquals(Boolean.FALSE, account.getEmailVerified());
        assertEquals(Boolean.FALSE, account.getPhoneVerified());
        assertEquals(HEALTH_CODE, account.getHealthCode());
        assertEquals(HEALTH_ID, account.getHealthId());
        assertEquals(PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM, account.getPasswordAlgorithm());

        // validate password hash
        assertTrue(PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM.checkHash(account.getPasswordHash(), DUMMY_PASSWORD));
    }
    
    @Test
    public void constructAccountWithoutPasswordWorks() throws Exception {
        // execute and validate
        GenericAccount account = (GenericAccount) dao.constructAccount(STUDY, EMAIL, PHONE, null);
        assertEquals(TestConstants.TEST_STUDY, account.getStudyIdentifier());
        assertEquals(EMAIL, account.getEmail());
        assertEquals(PHONE.getNationalFormat(), account.getPhone().getNationalFormat());
        assertEquals(Boolean.FALSE, account.getEmailVerified());
        assertEquals(Boolean.FALSE, account.getPhoneVerified());
        assertEquals(HEALTH_CODE, account.getHealthCode());
        assertEquals(HEALTH_ID, account.getHealthId());
        assertNull(account.getPasswordHash());
        assertNull(account.getPasswordAlgorithm());
    }

    @Test
    public void constructAccountForMigration() throws Exception {
        HealthId healthId = new HealthIdImpl(HEALTH_ID, HEALTH_CODE);

        // execute
        GenericAccount account = (GenericAccount) dao.constructAccountForMigration(STUDY, EMAIL, PHONE, DUMMY_PASSWORD, healthId);

        // Most of this stuff has been tested in the previous test. Just test that we set the expected HealthId.
        assertEquals(HEALTH_CODE, account.getHealthCode());
        assertEquals(HEALTH_ID, account.getHealthId());

        // Also verify HealthCodeService is never called
        verify(mockHealthCodeService, never()).createMapping(any());
    }

    @Test
    public void createAccountSuccess() {
        // Study passed into createAccount() takes precedence over StudyId in the Account object. To test this, make
        // the account have a different study.
        GenericAccount account = makeValidGenericAccount();
        account.setStudyId(new StudyIdentifierImpl("wrong-study"));

        // execute - We generate a new account ID.
        String daoOutputAcountId = dao.createAccount(STUDY, account, false);
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

        // don't call sendEmailVerificationToken
        verify(mockAccountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());
    }

    @Test
    public void createAccountVerifyEmail() {
        // Most of this is tested in createAccountSuccess(). Just test email workflow.
        String accountId = dao.createAccount(STUDY, makeValidGenericAccount(), true);
        verify(mockAccountWorkflowService).sendEmailVerificationToken(STUDY, accountId, EMAIL);

        // created account has account status unverified
        ArgumentCaptor<HibernateAccount> createdHibernateAccountCaptor = ArgumentCaptor.forClass(
                HibernateAccount.class);
        verify(mockHibernateHelper).create(createdHibernateAccountCaptor.capture());

        HibernateAccount createdHibernateAccount = createdHibernateAccountCaptor.getValue();
        assertEquals(AccountStatus.UNVERIFIED, createdHibernateAccount.getStatus());
    }

    @Test
    public void createAccountForMigration() {
        // Most of this is tested in createAccountSuccess(). Just test that account ID is correctly propagated and that
        // email workflow is *not* called despite the flag.
        dao.createAccountForMigration(STUDY, makeValidGenericAccount(), ACCOUNT_ID, true);
        verify(mockAccountWorkflowService, never()).sendEmailVerificationToken(any(), any(), any());

        // created account has correct account ID account status unverified
        ArgumentCaptor<HibernateAccount> createdHibernateAccountCaptor = ArgumentCaptor.forClass(
                HibernateAccount.class);
        verify(mockHibernateHelper).create(createdHibernateAccountCaptor.capture());

        HibernateAccount createdHibernateAccount = createdHibernateAccountCaptor.getValue();
        assertEquals(ACCOUNT_ID, createdHibernateAccount.getId());
        assertEquals(AccountStatus.UNVERIFIED, createdHibernateAccount.getStatus());
    }

    @Test
    public void createAccountAlreadyExists() {
        // mock hibernate
        String otherAccountId = "other-account-id";
        HibernateAccount otherHibernateAccount = new HibernateAccount();
        otherHibernateAccount.setId(otherAccountId);
        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        when(mockHibernateHelper.queryGet(queryCaptor.capture(), any(), any(), any())).thenReturn(ImmutableList.of(
                otherHibernateAccount));

        doThrow(ConcurrentModificationException.class).when(mockHibernateHelper).create(any());

        // execute
        try {
            dao.createAccount(STUDY, makeValidGenericAccount(), false);
            fail("expected exception");
        } catch (EntityAlreadyExistsException ex) {
            assertEquals(otherAccountId, ex.getEntity().get("userId"));
            assertTrue(queryCaptor.getValue().contains("email='eggplant@example.com'"));
        }
    }

    @Test(expected = BridgeServiceException.class)
    public void createAccountAlreadyExistsButNotFound() {
        // mock hibernate
        when(mockHibernateHelper.queryGet(any(), any(), any(), any())).thenReturn(ImmutableList.of());
        doThrow(ConcurrentModificationException.class).when(mockHibernateHelper).create(any());

        // execute
        dao.createAccount(STUDY, makeValidGenericAccount(), false);
    }

    @Test
    public void createAccountAlreadyExistsForPhoneAccount() {
        String otherAccountId = "other-account-id";
        HibernateAccount otherHibernateAccount = new HibernateAccount();
        otherHibernateAccount.setId(otherAccountId);
        
        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        when(mockHibernateHelper.queryGet(queryCaptor.capture(), any(), any(), any())).thenReturn(ImmutableList.of(
                otherHibernateAccount));

        doThrow(ConcurrentModificationException.class).when(mockHibernateHelper).create(any());

        // execute
        try {
            GenericAccount account = makeValidGenericAccount();
            account.setEmail(null);
            account.setPhone(TestConstants.PHONE);
            dao.createAccount(STUDY, account, false);
            fail("expected exception");
        } catch (EntityAlreadyExistsException ex) {
            assertEquals(otherAccountId, ex.getEntity().get("userId"));
            assertTrue(queryCaptor.getValue().contains("phone.number='+19712486796' and phone.regionCode='US'"));
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

        // execute
        GenericAccount account = makeValidGenericAccount();
        account.setPhone(OTHER_PHONE);
        account.setEmailVerified(Boolean.FALSE);
        account.setPhoneVerified(Boolean.FALSE);
        dao.updateAccount(account);

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
        when(mockHibernateHelper.getById(HibernateAccount.class, ACCOUNT_ID)).thenReturn(
                makeValidHibernateAccount(false, false));

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
        when(mockHibernateHelper.queryGet(any(), any(), any(), any())).thenReturn(ImmutableList.of(hibernateAccount));

        // execute and validate - just validate ID, study, and email, and health code mapping
        GenericAccount account = (GenericAccount) dao.getAccount(ACCOUNT_ID_WITH_EMAIL);
        assertEquals(ACCOUNT_ID, account.getId());
        assertEquals(TestConstants.TEST_STUDY, account.getStudyIdentifier());
        assertEquals(EMAIL, account.getEmail());
        assertEquals("original-" + HEALTH_CODE, account.getHealthCode());
        assertEquals("original-" + HEALTH_ID, account.getHealthId());

        // verify hibernate query
        verify(mockHibernateHelper).queryGet("from HibernateAccount where studyId='" +
                TestConstants.TEST_STUDY_IDENTIFIER + "' and email='" + EMAIL + "'", null, null,
                HibernateAccount.class);

        // We don't create a new health code mapping nor update the account.
        verify(mockHealthCodeService, never()).createMapping(any());
        verify(mockHibernateHelper, never()).update(any());
    }

    @Test
    public void getByEmailSuccessCreateNewHealthCode() throws Exception {
        // mock hibernate
        when(mockHibernateHelper.queryGet(any(), any(), any(), any())).thenReturn(ImmutableList.of(
                makeValidHibernateAccount(false, false)));

        // execute and validate - just validate ID, study, and email, and health code mapping
        GenericAccount account = (GenericAccount) dao.getAccount(ACCOUNT_ID_WITH_EMAIL);
        assertEquals(ACCOUNT_ID, account.getId());
        assertEquals(TestConstants.TEST_STUDY, account.getStudyIdentifier());
        assertEquals(EMAIL, account.getEmail());
        assertEquals(HEALTH_CODE, account.getHealthCode());
        assertEquals(HEALTH_ID, account.getHealthId());

        // verify hibernate query
        verify(mockHibernateHelper).queryGet("from HibernateAccount where studyId='" +
                        TestConstants.TEST_STUDY_IDENTIFIER + "' and email='" + EMAIL + "'", null, null,
                HibernateAccount.class);

        // Verify we create the new health code mapping
        verifyCreatedHealthCode();
    }

    @Test
    public void getByEmailNotFound() {
        // mock hibernate
        when(mockHibernateHelper.queryGet(any(), any(), any(), any())).thenReturn(ImmutableList.of());

        // execute and validate
        Account account = dao.getAccount(ACCOUNT_ID_WITH_EMAIL);
        assertNull(account);
    }
    
    @Test
    public void getByPhone() throws Exception {
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false, false);
        // mock hibernate
        when(mockHibernateHelper.queryGet("from HibernateAccount where studyId='" + TestConstants.TEST_STUDY_IDENTIFIER
                + "' and phone.number='" + TestConstants.PHONE.getNumber() + "' and phone.regionCode='"
                + TestConstants.PHONE.getRegionCode() + "'", null, null, HibernateAccount.class))
                        .thenReturn(ImmutableList.of(hibernateAccount));

        // execute and validate
        Account account = dao.getAccount(ACCOUNT_ID_WITH_PHONE);
        assertEquals(hibernateAccount.getEmail(), account.getEmail());
    }
    
    @Test
    public void getByPhoneNotFound() {
        when(mockHibernateHelper.queryGet("from HibernateAccount where studyId='"
                + TestConstants.TEST_STUDY_IDENTIFIER + "' and phone='" + TestConstants.PHONE.getNumber() + 
                "'", null, null, HibernateAccount.class)).thenReturn(ImmutableList.of());
        
        Account account = dao.getAccount(ACCOUNT_ID_WITH_PHONE);
        assertNull(account);
    }

    @Test
    public void getByPhoneAfterAuthentication() throws Exception {
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false, false);
        // mock hibernate
        when(mockHibernateHelper.queryGet("from HibernateAccount where studyId='" + TestConstants.TEST_STUDY_IDENTIFIER
                + "' and phone.number='" + TestConstants.PHONE.getNumber() + "' and phone.regionCode='"
                + TestConstants.PHONE.getRegionCode() + "'", null, null, HibernateAccount.class))
                        .thenReturn(ImmutableList.of(hibernateAccount));        

        // execute and validate
        Account account = dao.getAccountAfterAuthentication(ACCOUNT_ID_WITH_PHONE);
        assertEquals(hibernateAccount.getEmail(), account.getEmail());
    }
    
    @Test
    public void getByPhoneNotFoundAfterAuthentication() {
        when(mockHibernateHelper.queryGet("from HibernateAccount where studyId='"
                + TestConstants.TEST_STUDY_IDENTIFIER + "' and phone='" + TestConstants.PHONE.getNumber() + 
                "'", null, null, HibernateAccount.class)).thenReturn(ImmutableList.of());
        
        Account account = dao.getAccountAfterAuthentication(ACCOUNT_ID_WITH_PHONE);
        assertNull(account);
    }
    
    @Test
    public void deleteWithoutId() throws Exception {
        // Can't use email, so it will do a lookup of the account
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false, false);
        when(mockHibernateHelper.queryGet(any(), any(), any(), any())).thenReturn(ImmutableList.of(hibernateAccount));
        
        dao.deleteAccount(ACCOUNT_ID_WITH_EMAIL);
        
        verify(mockHibernateHelper).deleteById(HibernateAccount.class, ACCOUNT_ID);
    }
    
    @Test
    public void deleteWithId() throws Exception {
        // Directly deletes with the ID it has
        dao.deleteAccount(ACCOUNT_ID_WITH_ID);
        
        verify(mockHibernateHelper).deleteById(HibernateAccount.class, ACCOUNT_ID);
        verify(mockHibernateHelper, never()).queryGet(any(), any(), any(), any());
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

        when(mockHibernateHelper.queryGet(any(), any(), any(), any())).thenReturn(ImmutableList.of(hibernateAccount1,
                hibernateAccount2));

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
        verify(mockHibernateHelper).queryGet("from HibernateAccount", null, null, HibernateAccount.class);
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

        when(mockHibernateHelper.queryGet(any(), any(), any(), any())).thenReturn(ImmutableList.of(hibernateAccount1,
                hibernateAccount2));

        // execute and validate - just ID, study, and email is sufficient
        Iterator<AccountSummary> accountSummaryIter = dao.getStudyAccounts(STUDY);
        List<AccountSummary> accountSummaryList = ImmutableList.copyOf(accountSummaryIter);
        assertEquals(2, accountSummaryList.size());

        assertEquals("account-1", accountSummaryList.get(0).getId());
        assertEquals(TestConstants.TEST_STUDY, accountSummaryList.get(0).getStudyIdentifier());
        assertEquals("email1@example.com", accountSummaryList.get(0).getEmail());

        assertEquals("account-2", accountSummaryList.get(1).getId());
        assertEquals(TestConstants.TEST_STUDY, accountSummaryList.get(1).getStudyIdentifier());
        assertEquals("email2@example.com", accountSummaryList.get(1).getEmail());

        // verify hibernate call
        verify(mockHibernateHelper).queryGet("from HibernateAccount where studyId='" +
                TestConstants.TEST_STUDY_IDENTIFIER + "'", null, null, HibernateAccount.class);
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

        when(mockHibernateHelper.queryGet(any(), any(), any(), any())).thenReturn(ImmutableList.of(hibernateAccount1,
                hibernateAccount2));
        when(mockHibernateHelper.queryCount(any())).thenReturn(12);

        // execute and validate
        PagedResourceList<AccountSummary> accountSummaryResourceList = dao.getPagedAccountSummaries(STUDY, 10, 5,
                null, null, null, null);
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
        String expectedQueryString = "from HibernateAccount where studyId='" + 
                TestConstants.TEST_STUDY_IDENTIFIER + "'";
        String expectedGetQueryString = HibernateAccountDao.ACCOUNT_SUMMARY_QUERY_PREFIX + expectedQueryString;
        
        verify(mockHibernateHelper).queryGet(expectedGetQueryString, 10, 5, HibernateAccount.class);
        verify(mockHibernateHelper).queryCount(expectedQueryString);
    }

    @Test
    public void getPagedWithOptionalParams() throws Exception {
        // Setup start and end dates.
        DateTime startDate = DateTime.parse("2017-05-19T11:40:06.247-0700");
        DateTime endDate = DateTime.parse("2017-05-19T18:32:03.434-0700");

        // mock hibernate
        when(mockHibernateHelper.queryGet(any(), any(), any(), any())).thenReturn(ImmutableList.of(
                makeValidHibernateAccount(false, false)));
        when(mockHibernateHelper.queryCount(any())).thenReturn(11);

        // execute and validate - Just validate filters and query, since everything else is tested in getPaged().
        PagedResourceList<AccountSummary> accountSummaryResourceList = dao.getPagedAccountSummaries(STUDY, 10, 5,
                EMAIL, PHONE.getNationalFormat(), startDate, endDate);

        Map<String, Object> paramsMap = accountSummaryResourceList.getRequestParams();
        assertEquals(6, paramsMap.size());
        assertEquals(5, paramsMap.get("pageSize"));
        assertEquals(10, paramsMap.get("offsetBy"));
        assertEquals(EMAIL, paramsMap.get("emailFilter"));
        assertEquals(PHONE.getNationalFormat(), paramsMap.get("phoneFilter"));
        assertEquals(startDate.toString(), paramsMap.get("startTime"));
        assertEquals(endDate.toString(), paramsMap.get("endTime"));

        String phoneString = PHONE.getNationalFormat().replaceAll("\\D*","");

        // verify hibernate calls
        String expectedQueryString = "from HibernateAccount where studyId='" + TestConstants.TEST_STUDY_IDENTIFIER
                + "' and email like '%" + EMAIL + "%' and phone.number like '%" + phoneString + "%' and createdOn >= "
                + startDate.getMillis() + " and createdOn <= " + endDate.getMillis();
        String expectedGetQueryString = HibernateAccountDao.ACCOUNT_SUMMARY_QUERY_PREFIX + expectedQueryString;
        verify(mockHibernateHelper).queryGet(expectedGetQueryString, 10, 5, HibernateAccount.class);
        verify(mockHibernateHelper).queryCount(expectedQueryString);
    }

    @Test
    public void getHealthCode() throws Exception {
        // mock hibernate
        HibernateAccount hibernateAccount = makeValidHibernateAccount(false, false);
        hibernateAccount.setHealthCode(HEALTH_CODE);
        hibernateAccount.setHealthId(HEALTH_ID);
        when(mockHibernateHelper.queryGet(any(), any(), any(), any())).thenReturn(ImmutableList.of(hibernateAccount));

        // execute and validate
        String healthCode = dao.getHealthCodeForAccount(ACCOUNT_ID_WITH_EMAIL);
        assertEquals(HEALTH_CODE, healthCode);

        // verify hibernate query
        verify(mockHibernateHelper).queryGet("from HibernateAccount where studyId='" +
                TestConstants.TEST_STUDY_IDENTIFIER + "' and email='" + EMAIL + "'", null, null,
                HibernateAccount.class);
    }

    @Test
    public void getHealthCodeNoAccount() {
        // mock hibernate
        when(mockHibernateHelper.queryGet(any(), any(), any(), any())).thenReturn(ImmutableList.of());

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
        genericAccount.setRoles(EnumSet.of(Roles.DEVELOPER, Roles.TEST_USERS));
        genericAccount.setStatus(AccountStatus.ENABLED);
        genericAccount.setClientData(TestUtils.getClientData());
        genericAccount.setVersion(VERSION);

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
        assertEquals(EnumSet.of(Roles.DEVELOPER, Roles.TEST_USERS), hibernateAccount.getRoles());
        assertEquals(AccountStatus.ENABLED, hibernateAccount.getStatus());
        assertEquals(TestUtils.getClientData().toString(), hibernateAccount.getClientData());
        assertEquals(VERSION, hibernateAccount.getVersion());

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
        hibernateAccount.setRoles(EnumSet.of(Roles.DEVELOPER, Roles.TEST_USERS));
        hibernateAccount.setStatus(AccountStatus.ENABLED);
        hibernateAccount.setClientData(TestUtils.getClientData().toString());
        hibernateAccount.setVersion(VERSION);

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
        assertEquals(EnumSet.of(Roles.DEVELOPER, Roles.TEST_USERS), genericAccount.getRoles());
        assertEquals(AccountStatus.ENABLED, genericAccount.getStatus());
        assertEquals(TestUtils.getClientData(), genericAccount.getClientData());
        assertEquals(VERSION, genericAccount.getVersion());

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
        return genericAccount;
    }

    // Create minimal Hibernate account for everything that will be used by HibernateAccountDao.
    private static HibernateAccount makeValidHibernateAccount(boolean generatePasswordHash, boolean generateReauthHash) throws Exception {
        HibernateAccount hibernateAccount = new HibernateAccount();
        hibernateAccount.setId(ACCOUNT_ID);
        hibernateAccount.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        hibernateAccount.setPhone(TestConstants.PHONE);
        hibernateAccount.setEmail(EMAIL);
        hibernateAccount.setStatus(AccountStatus.ENABLED);

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
