package org.sagebionetworks.bridge.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Iterator;
import java.util.List;

import com.google.common.collect.ImmutableList;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.hibernate.HibernateAccountDao;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountStatus;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.Email;
import org.sagebionetworks.bridge.models.accounts.EmailVerification;
import org.sagebionetworks.bridge.models.accounts.GenericAccount;
import org.sagebionetworks.bridge.models.accounts.HealthId;
import org.sagebionetworks.bridge.models.accounts.HealthIdImpl;
import org.sagebionetworks.bridge.models.accounts.MigrationAccount;
import org.sagebionetworks.bridge.models.accounts.PasswordReset;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.HealthCodeService;
import org.sagebionetworks.bridge.stormpath.StormpathAccount;
import org.sagebionetworks.bridge.stormpath.StormpathAccountDao;

public class MigrationAccountDaoTest {
    private static final String ACCOUNT_ID = "account-id";
    private static final String DUMMY_PASSWORD = "This is not a real password.";
    private static final String DUMMY_TOKEN = "dummy-token";
    private static final String EMAIL = "eggplant@example.com";
    private static final String HEALTH_CODE = "health-code";
    private static final String HEALTH_ID_STRING = "health-id";
    private static final HealthId HEALTH_ID = new HealthIdImpl(HEALTH_ID_STRING, HEALTH_CODE);
    private static final String STORMPATH_ACCOUNT_ID = "stormpath-account-id";

    private static final AccountSummary ACCOUNT_SUMMARY = new AccountSummary("Eggplant", "McTester", EMAIL, ACCOUNT_ID,
            new DateTime(12345678L), AccountStatus.ENABLED, TestConstants.TEST_STUDY);

    private static final GenericAccount GENERIC_ACCOUNT;
    static {
        GENERIC_ACCOUNT = new GenericAccount();
        GENERIC_ACCOUNT.setHealthId(HEALTH_ID);
    }

    private static final Study STUDY;
    static {
        STUDY = Study.create();
        STUDY.setIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);
    }

    private HibernateAccountDao mySqlHibernateAccountDao;
    private StormpathAccountDao mySqlStormpathAccountDao;
    private MigrationAccountDao mySqlMigrationAccountDao;

    private HibernateAccountDao noCreateHibernateAccountDao;
    private StormpathAccountDao noCreateStormpathAccountDao;
    private MigrationAccountDao noCreateMigrationAccountDao;

    private HibernateAccountDao noMySqlHibernateAccountDao;
    private StormpathAccountDao noMySqlStormpathAccountDao;
    private MigrationAccountDao noMySqlMigrationAccountDao;

    private StormpathAccount mockStormpathAccount;
    private MigrationAccount migrationAccount;

    @Before
    public void setupMySqlDao() {
        // Set up MigrationAccountDao with MySQL.
        BridgeConfig mySqlConfig = mock(BridgeConfig.class);
        when(mySqlConfig.get(MigrationAccountDao.CONFIG_KEY_AUTH_PROVIDER)).thenReturn("mysql");
        when(mySqlConfig.get(MigrationAccountDao.CONFIG_KEY_CREATE_ACCOUNTS)).thenReturn(String.valueOf(true));

        mySqlHibernateAccountDao = mock(HibernateAccountDao.class);
        mySqlStormpathAccountDao = mock(StormpathAccountDao.class);

        mySqlMigrationAccountDao = new MigrationAccountDao();
        mySqlMigrationAccountDao.setConfig(mySqlConfig);
        mySqlMigrationAccountDao.setHibernateAccountDao(mySqlHibernateAccountDao);
        mySqlMigrationAccountDao.setStormpathAccountDao(mySqlStormpathAccountDao);
    }

    @Before
    public void setupNoCreateDao() {
        // Set up MigrationAccountDao with MySQL but without creating accounts flag.
        BridgeConfig noCreateConfig = mock(BridgeConfig.class);
        when(noCreateConfig.get(MigrationAccountDao.CONFIG_KEY_AUTH_PROVIDER)).thenReturn("mysql");
        when(noCreateConfig.get(MigrationAccountDao.CONFIG_KEY_CREATE_ACCOUNTS)).thenReturn(String.valueOf(false));

        noCreateHibernateAccountDao = mock(HibernateAccountDao.class);
        noCreateStormpathAccountDao = mock(StormpathAccountDao.class);

        noCreateMigrationAccountDao = new MigrationAccountDao();
        noCreateMigrationAccountDao.setConfig(noCreateConfig);
        noCreateMigrationAccountDao.setHibernateAccountDao(noCreateHibernateAccountDao);
        noCreateMigrationAccountDao.setStormpathAccountDao(noCreateStormpathAccountDao);
    }

    @Before
    public void setupNoMySqlDao() {
        // Set up MigrationAccountDao with MySQL but without creating accounts flag.
        // We're setting create.accounts to true to verify that auth.provider=hibernate takes precedence.
        BridgeConfig noMySqlConfig = mock(BridgeConfig.class);
        when(noMySqlConfig.get(MigrationAccountDao.CONFIG_KEY_AUTH_PROVIDER)).thenReturn("hibernate");
        when(noMySqlConfig.get(MigrationAccountDao.CONFIG_KEY_CREATE_ACCOUNTS)).thenReturn(String.valueOf(true));

        noMySqlHibernateAccountDao = mock(HibernateAccountDao.class);
        noMySqlStormpathAccountDao = mock(StormpathAccountDao.class);

        noMySqlMigrationAccountDao = new MigrationAccountDao();
        noMySqlMigrationAccountDao.setConfig(noMySqlConfig);
        noMySqlMigrationAccountDao.setHibernateAccountDao(noMySqlHibernateAccountDao);
        noMySqlMigrationAccountDao.setStormpathAccountDao(noMySqlStormpathAccountDao);
    }

    @Before
    public void setupMigrationAccount() {
        mockStormpathAccount = mock(StormpathAccount.class);
        when(mockStormpathAccount.getHealthCode()).thenReturn(HEALTH_CODE);
        when(mockStormpathAccount.getHealthId()).thenReturn(HEALTH_ID_STRING);

        migrationAccount = new MigrationAccount(GENERIC_ACCOUNT, mockStormpathAccount);
    }

    @Test
    public void verifyEmail() {
        // These all call through to the HibernateAccountDao, since Hibernate and Stormpath both do the same thing.
        EmailVerification emailVerification = new EmailVerification(DUMMY_TOKEN);

        mySqlMigrationAccountDao.verifyEmail(emailVerification);
        verify(mySqlHibernateAccountDao).verifyEmail(emailVerification);
        verify(mySqlStormpathAccountDao, never()).verifyEmail(any());

        noCreateMigrationAccountDao.verifyEmail(emailVerification);
        verify(noCreateHibernateAccountDao).verifyEmail(emailVerification);
        verify(noCreateStormpathAccountDao, never()).verifyEmail(any());

        noMySqlMigrationAccountDao.verifyEmail(emailVerification);
        verify(noMySqlHibernateAccountDao).verifyEmail(emailVerification);
        verify(noMySqlStormpathAccountDao, never()).verifyEmail(any());
    }

    @Test
    public void resendEmailVerificationToken() {
        // similarly
        Email email = new Email(TestConstants.TEST_STUDY, EMAIL);

        mySqlMigrationAccountDao.resendEmailVerificationToken(TestConstants.TEST_STUDY, email);
        verify(mySqlHibernateAccountDao).resendEmailVerificationToken(TestConstants.TEST_STUDY, email);
        verify(mySqlStormpathAccountDao, never()).resendEmailVerificationToken(any(), any());

        noCreateMigrationAccountDao.resendEmailVerificationToken(TestConstants.TEST_STUDY, email);
        verify(noCreateHibernateAccountDao).resendEmailVerificationToken(TestConstants.TEST_STUDY, email);
        verify(noCreateStormpathAccountDao, never()).resendEmailVerificationToken(any(), any());

        noMySqlMigrationAccountDao.resendEmailVerificationToken(TestConstants.TEST_STUDY, email);
        verify(noMySqlHibernateAccountDao).resendEmailVerificationToken(TestConstants.TEST_STUDY, email);
        verify(noMySqlStormpathAccountDao, never()).resendEmailVerificationToken(any(), any());
    }

    @Test
    public void requestResetPassword() {
        // similarly
        Email email = new Email(TestConstants.TEST_STUDY, EMAIL);

        mySqlMigrationAccountDao.requestResetPassword(STUDY, email);
        verify(mySqlHibernateAccountDao).requestResetPassword(STUDY, email);
        verify(mySqlStormpathAccountDao, never()).requestResetPassword(any(), any());

        noCreateMigrationAccountDao.requestResetPassword(STUDY, email);
        verify(noCreateHibernateAccountDao).requestResetPassword(STUDY, email);
        verify(noCreateStormpathAccountDao, never()).requestResetPassword(any(), any());

        noMySqlMigrationAccountDao.requestResetPassword(STUDY, email);
        verify(noMySqlHibernateAccountDao).requestResetPassword(STUDY, email);
        verify(noMySqlStormpathAccountDao, never()).requestResetPassword(any(), any());
    }

    @Test
    public void resetPassword() {
        // similarly
        PasswordReset passwordReset = new PasswordReset(DUMMY_PASSWORD, DUMMY_TOKEN,
                TestConstants.TEST_STUDY_IDENTIFIER);

        mySqlMigrationAccountDao.resetPassword(passwordReset);
        verify(mySqlHibernateAccountDao).resetPassword(passwordReset);
        verify(mySqlStormpathAccountDao, never()).resetPassword(any());

        noCreateMigrationAccountDao.resetPassword(passwordReset);
        verify(noCreateHibernateAccountDao).resetPassword(passwordReset);
        verify(noCreateStormpathAccountDao, never()).resetPassword(any());

        noMySqlMigrationAccountDao.resetPassword(passwordReset);
        verify(noMySqlHibernateAccountDao).resetPassword(passwordReset);
        verify(noMySqlStormpathAccountDao, never()).resetPassword(any());
    }

    @Test
    public void changePassword() {
        mySqlMigrationAccountDao.changePassword(migrationAccount, DUMMY_PASSWORD);
        verify(mySqlHibernateAccountDao).changePassword(GENERIC_ACCOUNT, DUMMY_PASSWORD);
        verify(mySqlStormpathAccountDao).changePassword(mockStormpathAccount, DUMMY_PASSWORD);

        noCreateMigrationAccountDao.changePassword(migrationAccount, DUMMY_PASSWORD);
        verify(noCreateHibernateAccountDao).changePassword(GENERIC_ACCOUNT, DUMMY_PASSWORD);
        verify(noCreateStormpathAccountDao).changePassword(mockStormpathAccount, DUMMY_PASSWORD);

        noMySqlMigrationAccountDao.changePassword(migrationAccount, DUMMY_PASSWORD);
        verify(noMySqlHibernateAccountDao, never()).changePassword(any(), any());
        verify(noMySqlStormpathAccountDao).changePassword(mockStormpathAccount, DUMMY_PASSWORD);
    }

    @Test
    public void authenticate() {
        SignIn signIn = new SignIn(TestConstants.TEST_STUDY_IDENTIFIER, EMAIL, DUMMY_PASSWORD, DUMMY_TOKEN);

        // MySQL case
        {
            when(mySqlHibernateAccountDao.authenticate(STUDY, signIn)).thenReturn(GENERIC_ACCOUNT);
            when(mySqlStormpathAccountDao.authenticate(STUDY, signIn)).thenReturn(mockStormpathAccount);

            MigrationAccount retval = (MigrationAccount) mySqlMigrationAccountDao.authenticate(STUDY, signIn);
            assertSame(GENERIC_ACCOUNT, retval.getGenericAccount());
            assertSame(mockStormpathAccount, retval.getStormpathAccount());

            verify(mySqlHibernateAccountDao).authenticate(STUDY, signIn);
            verify(mySqlStormpathAccountDao).authenticate(STUDY, signIn);

            verify(mySqlHibernateAccountDao, never()).updateAccount(any());
        }

        // no-create case
        {
            when(noCreateHibernateAccountDao.authenticate(STUDY, signIn)).thenReturn(GENERIC_ACCOUNT);
            when(noCreateStormpathAccountDao.authenticate(STUDY, signIn)).thenReturn(mockStormpathAccount);

            MigrationAccount retval = (MigrationAccount) noCreateMigrationAccountDao.authenticate(STUDY, signIn);
            assertSame(GENERIC_ACCOUNT, retval.getGenericAccount());
            assertSame(mockStormpathAccount, retval.getStormpathAccount());

            verify(noCreateHibernateAccountDao).authenticate(STUDY, signIn);
            verify(noCreateStormpathAccountDao).authenticate(STUDY, signIn);

            verify(noCreateHibernateAccountDao, never()).updateAccount(any());
        }

        // no MySQL case
        {
            when(noMySqlStormpathAccountDao.authenticate(STUDY, signIn)).thenReturn(mockStormpathAccount);

            MigrationAccount retval = (MigrationAccount) noMySqlMigrationAccountDao.authenticate(STUDY, signIn);
            assertNull(retval.getGenericAccount());
            assertSame(mockStormpathAccount, retval.getStormpathAccount());

            verify(noMySqlHibernateAccountDao, never()).authenticate(any(), any());
            verify(noMySqlStormpathAccountDao).authenticate(STUDY, signIn);

            verify(noMySqlHibernateAccountDao, never()).updateAccount(any());
        }
    }

    @Test
    public void authenticateWithDifferentHealthCodes() {
        // setup test
        SignIn signIn = new SignIn(TestConstants.TEST_STUDY_IDENTIFIER, EMAIL, DUMMY_PASSWORD, DUMMY_TOKEN);
        GenericAccount genericAccount = new GenericAccount();
        genericAccount.setHealthCode("different-health-code");
        genericAccount.setHealthId("different-health-id");

        when(mySqlHibernateAccountDao.authenticate(STUDY, signIn)).thenReturn(genericAccount);
        when(mySqlStormpathAccountDao.authenticate(STUDY, signIn)).thenReturn(mockStormpathAccount);

        // Execute and validate health codes.
        MigrationAccount retval = (MigrationAccount) mySqlMigrationAccountDao.authenticate(STUDY, signIn);
        assertSame(HEALTH_CODE, retval.getGenericAccount().getHealthCode());
        assertSame(HEALTH_ID_STRING, retval.getGenericAccount().getHealthId());
        assertSame(HEALTH_CODE, retval.getStormpathAccount().getHealthCode());
        assertSame(HEALTH_ID_STRING, retval.getStormpathAccount().getHealthId());

        // Validate we save the generic account with the new health code mapping.
        ArgumentCaptor<GenericAccount> updatedAccountCaptor = ArgumentCaptor.forClass(GenericAccount.class);
        verify(mySqlHibernateAccountDao).updateAccount(updatedAccountCaptor.capture());

        GenericAccount updatedAccount = updatedAccountCaptor.getValue();
        assertEquals(HEALTH_CODE, updatedAccount.getHealthCode());
        assertEquals(HEALTH_ID_STRING, updatedAccount.getHealthId());
    }

    @Test
    public void authenticateMySqlAccountNotFound() {
        SignIn signIn = new SignIn(TestConstants.TEST_STUDY_IDENTIFIER, EMAIL, DUMMY_PASSWORD, DUMMY_TOKEN);

        when(mySqlHibernateAccountDao.authenticate(STUDY, signIn)).thenThrow(new EntityNotFoundException(
                Account.class));
        when(mySqlStormpathAccountDao.authenticate(STUDY, signIn)).thenReturn(mockStormpathAccount);

        MigrationAccount retval = (MigrationAccount) mySqlMigrationAccountDao.authenticate(STUDY, signIn);
        assertNull(retval.getGenericAccount());
        assertSame(mockStormpathAccount, retval.getStormpathAccount());

        verify(mySqlHibernateAccountDao).authenticate(STUDY, signIn);
        verify(mySqlStormpathAccountDao).authenticate(STUDY, signIn);
    }

    @Test
    public void constructAccount() {
        // mock health code service
        HealthCodeService mockHealthCodeService = mock(HealthCodeService.class);
        when(mockHealthCodeService.createMapping(TestConstants.TEST_STUDY)).thenReturn(HEALTH_ID);
        mySqlMigrationAccountDao.setHealthCodeService(mockHealthCodeService);
        noCreateMigrationAccountDao.setHealthCodeService(mockHealthCodeService);
        noMySqlMigrationAccountDao.setHealthCodeService(mockHealthCodeService);

        // MySQL case
        {
            when(mySqlHibernateAccountDao.constructAccountForMigration(STUDY, EMAIL, DUMMY_PASSWORD, HEALTH_ID))
                    .thenReturn(GENERIC_ACCOUNT);
            when(mySqlStormpathAccountDao.constructAccountForMigration(STUDY, EMAIL, DUMMY_PASSWORD, HEALTH_ID))
                    .thenReturn(mockStormpathAccount);

            MigrationAccount retval = (MigrationAccount) mySqlMigrationAccountDao.constructAccount(STUDY, EMAIL,
                    DUMMY_PASSWORD);
            assertSame(GENERIC_ACCOUNT, retval.getGenericAccount());
            assertSame(mockStormpathAccount, retval.getStormpathAccount());

            verify(mySqlHibernateAccountDao).constructAccountForMigration(STUDY, EMAIL, DUMMY_PASSWORD, HEALTH_ID);
            verify(mySqlHibernateAccountDao, never()).constructAccount(any(), any(), any());

            verify(mySqlStormpathAccountDao).constructAccountForMigration(STUDY, EMAIL, DUMMY_PASSWORD, HEALTH_ID);
            verify(mySqlStormpathAccountDao, never()).constructAccount(any(), any(), any());
        }

        // no-create case
        {
            when(noCreateStormpathAccountDao.constructAccountForMigration(STUDY, EMAIL, DUMMY_PASSWORD, HEALTH_ID))
                    .thenReturn(mockStormpathAccount);

            MigrationAccount retval = (MigrationAccount) noCreateMigrationAccountDao.constructAccount(STUDY, EMAIL,
                    DUMMY_PASSWORD);
            assertNull(retval.getGenericAccount());
            assertSame(mockStormpathAccount, retval.getStormpathAccount());

            verify(noCreateHibernateAccountDao, never()).constructAccountForMigration(any(), any(), any(), any());
            verify(noCreateHibernateAccountDao, never()).constructAccount(any(), any(), any());

            verify(noCreateStormpathAccountDao).constructAccountForMigration(STUDY, EMAIL, DUMMY_PASSWORD, HEALTH_ID);
            verify(noCreateStormpathAccountDao, never()).constructAccount(any(), any(), any());
        }

        // no MySQL case
        {
            when(noMySqlStormpathAccountDao.constructAccountForMigration(STUDY, EMAIL, DUMMY_PASSWORD, HEALTH_ID))
                    .thenReturn(mockStormpathAccount);

            MigrationAccount retval = (MigrationAccount) noMySqlMigrationAccountDao.constructAccount(STUDY, EMAIL,
                    DUMMY_PASSWORD);
            assertNull(retval.getGenericAccount());
            assertSame(mockStormpathAccount, retval.getStormpathAccount());

            verify(noMySqlHibernateAccountDao, never()).constructAccountForMigration(any(), any(), any(), any());
            verify(noMySqlHibernateAccountDao, never()).constructAccount(any(), any(), any());

            verify(noMySqlStormpathAccountDao).constructAccountForMigration(STUDY, EMAIL, DUMMY_PASSWORD, HEALTH_ID);
            verify(noMySqlStormpathAccountDao, never()).constructAccount(any(), any(), any());
        }
    }

    @Test
    public void createAccount() {
        // MySQL case
        {
            when(mySqlStormpathAccountDao.createAccount(STUDY, mockStormpathAccount, true)).thenReturn(
                    STORMPATH_ACCOUNT_ID);

            String retval = mySqlMigrationAccountDao.createAccount(STUDY, migrationAccount, true);
            assertEquals(STORMPATH_ACCOUNT_ID, retval);

            verify(mySqlStormpathAccountDao).createAccount(STUDY, mockStormpathAccount, true);
            verify(mySqlHibernateAccountDao).createAccountForMigration(STUDY, GENERIC_ACCOUNT, STORMPATH_ACCOUNT_ID,
                    true);
            verify(mySqlHibernateAccountDao, never()).createAccount(any(), any(), anyBoolean());
        }

        // no-create case
        {
            when(noCreateStormpathAccountDao.createAccount(STUDY, mockStormpathAccount, true)).thenReturn(
                    STORMPATH_ACCOUNT_ID);

            String retval = noCreateMigrationAccountDao.createAccount(STUDY, migrationAccount, true);
            assertEquals(STORMPATH_ACCOUNT_ID, retval);

            verify(noCreateStormpathAccountDao).createAccount(STUDY, mockStormpathAccount, true);
            verify(noCreateHibernateAccountDao, never()).createAccountForMigration(any(), any(), any(), anyBoolean());
            verify(noCreateHibernateAccountDao, never()).createAccount(any(), any(), anyBoolean());
        }

        // no MySQL case
        {
            when(noMySqlStormpathAccountDao.createAccount(STUDY, mockStormpathAccount, true)).thenReturn(
                    STORMPATH_ACCOUNT_ID);

            String retval = noMySqlMigrationAccountDao.createAccount(STUDY, migrationAccount, true);
            assertEquals(STORMPATH_ACCOUNT_ID, retval);

            verify(noMySqlStormpathAccountDao).createAccount(STUDY, mockStormpathAccount, true);
            verify(noMySqlHibernateAccountDao, never()).createAccountForMigration(any(), any(), any(), anyBoolean());
            verify(noMySqlHibernateAccountDao, never()).createAccount(any(), any(), anyBoolean());
        }
    }

    @Test
    public void updateAccount() {
        mySqlMigrationAccountDao.updateAccount(migrationAccount);
        verify(mySqlHibernateAccountDao).updateAccount(GENERIC_ACCOUNT);
        verify(mySqlStormpathAccountDao).updateAccount(mockStormpathAccount);

        noCreateMigrationAccountDao.updateAccount(migrationAccount);
        verify(noCreateHibernateAccountDao).updateAccount(GENERIC_ACCOUNT);
        verify(noCreateStormpathAccountDao).updateAccount(mockStormpathAccount);

        noMySqlMigrationAccountDao.updateAccount(migrationAccount);
        verify(noMySqlHibernateAccountDao, never()).updateAccount(any());
        verify(noMySqlStormpathAccountDao).updateAccount(mockStormpathAccount);
    }

    @Test
    public void getAccount() {
        // MySQL case
        {
            when(mySqlHibernateAccountDao.getAccount(STUDY, ACCOUNT_ID)).thenReturn(GENERIC_ACCOUNT);
            when(mySqlStormpathAccountDao.getAccount(STUDY, ACCOUNT_ID)).thenReturn(mockStormpathAccount);

            MigrationAccount retval = (MigrationAccount) mySqlMigrationAccountDao.getAccount(STUDY, ACCOUNT_ID);
            assertSame(GENERIC_ACCOUNT, retval.getGenericAccount());
            assertSame(mockStormpathAccount, retval.getStormpathAccount());

            verify(mySqlHibernateAccountDao).getAccount(STUDY, ACCOUNT_ID);
            verify(mySqlStormpathAccountDao).getAccount(STUDY, ACCOUNT_ID);

            verify(mySqlHibernateAccountDao, never()).updateAccount(any());
        }

        // no-create case
        {
            when(noCreateHibernateAccountDao.getAccount(STUDY, ACCOUNT_ID)).thenReturn(GENERIC_ACCOUNT);
            when(noCreateStormpathAccountDao.getAccount(STUDY, ACCOUNT_ID)).thenReturn(mockStormpathAccount);

            MigrationAccount retval = (MigrationAccount) noCreateMigrationAccountDao.getAccount(STUDY, ACCOUNT_ID);
            assertSame(GENERIC_ACCOUNT, retval.getGenericAccount());
            assertSame(mockStormpathAccount, retval.getStormpathAccount());

            verify(noCreateHibernateAccountDao).getAccount(STUDY, ACCOUNT_ID);
            verify(noCreateStormpathAccountDao).getAccount(STUDY, ACCOUNT_ID);

            verify(noCreateHibernateAccountDao, never()).updateAccount(any());
        }

        // no MySQL case
        {
            when(noMySqlStormpathAccountDao.getAccount(STUDY, ACCOUNT_ID)).thenReturn(mockStormpathAccount);

            MigrationAccount retval = (MigrationAccount) noMySqlMigrationAccountDao.getAccount(STUDY, ACCOUNT_ID);
            assertNull(retval.getGenericAccount());
            assertSame(mockStormpathAccount, retval.getStormpathAccount());

            verify(noMySqlHibernateAccountDao, never()).getAccount(any(), any());
            verify(noMySqlStormpathAccountDao).getAccount(STUDY, ACCOUNT_ID);

            verify(noMySqlHibernateAccountDao, never()).updateAccount(any());
        }
    }

    @Test
    public void getAccountWithDifferentHealthCodes() {
        // setup test
        GenericAccount genericAccount = new GenericAccount();
        genericAccount.setHealthCode("different-health-code");
        genericAccount.setHealthId("different-health-id");

        when(mySqlHibernateAccountDao.getAccount(STUDY, ACCOUNT_ID)).thenReturn(genericAccount);
        when(mySqlStormpathAccountDao.getAccount(STUDY, ACCOUNT_ID)).thenReturn(mockStormpathAccount);

        // Execute and validate health codes.
        MigrationAccount retval = (MigrationAccount) mySqlMigrationAccountDao.getAccount(STUDY, ACCOUNT_ID);
        assertSame(HEALTH_CODE, retval.getGenericAccount().getHealthCode());
        assertSame(HEALTH_ID_STRING, retval.getGenericAccount().getHealthId());
        assertSame(HEALTH_CODE, retval.getStormpathAccount().getHealthCode());
        assertSame(HEALTH_ID_STRING, retval.getStormpathAccount().getHealthId());

        // Validate we save the generic account with the new health code mapping.
        ArgumentCaptor<GenericAccount> updatedAccountCaptor = ArgumentCaptor.forClass(GenericAccount.class);
        verify(mySqlHibernateAccountDao).updateAccount(updatedAccountCaptor.capture());

        GenericAccount updatedAccount = updatedAccountCaptor.getValue();
        assertEquals(HEALTH_CODE, updatedAccount.getHealthCode());
        assertEquals(HEALTH_ID_STRING, updatedAccount.getHealthId());
    }

    @Test
    public void getAccountNoAccount() {
        when(mySqlHibernateAccountDao.getAccount(STUDY, ACCOUNT_ID)).thenReturn(null);
        when(mySqlStormpathAccountDao.getAccount(STUDY, ACCOUNT_ID)).thenReturn(null);

        MigrationAccount retval = (MigrationAccount) mySqlMigrationAccountDao.getAccount(STUDY, ACCOUNT_ID);
        assertNull(retval);

        verify(mySqlHibernateAccountDao).getAccount(STUDY, ACCOUNT_ID);
        verify(mySqlStormpathAccountDao).getAccount(STUDY, ACCOUNT_ID);
    }

    @Test
    public void getAccountWithEmail() {
        // MySQL case
        {
            when(mySqlHibernateAccountDao.getAccountWithEmail(STUDY, EMAIL)).thenReturn(GENERIC_ACCOUNT);
            when(mySqlStormpathAccountDao.getAccountWithEmail(STUDY, EMAIL)).thenReturn(mockStormpathAccount);

            MigrationAccount retval = (MigrationAccount) mySqlMigrationAccountDao.getAccountWithEmail(STUDY, EMAIL);
            assertSame(GENERIC_ACCOUNT, retval.getGenericAccount());
            assertSame(mockStormpathAccount, retval.getStormpathAccount());

            verify(mySqlHibernateAccountDao).getAccountWithEmail(STUDY, EMAIL);
            verify(mySqlStormpathAccountDao).getAccountWithEmail(STUDY, EMAIL);

            verify(mySqlHibernateAccountDao, never()).updateAccount(any());
        }

        // no-create case
        {
            when(noCreateHibernateAccountDao.getAccountWithEmail(STUDY, EMAIL)).thenReturn(GENERIC_ACCOUNT);
            when(noCreateStormpathAccountDao.getAccountWithEmail(STUDY, EMAIL)).thenReturn(mockStormpathAccount);

            MigrationAccount retval = (MigrationAccount) noCreateMigrationAccountDao.getAccountWithEmail(STUDY, EMAIL);
            assertSame(GENERIC_ACCOUNT, retval.getGenericAccount());
            assertSame(mockStormpathAccount, retval.getStormpathAccount());

            verify(noCreateHibernateAccountDao).getAccountWithEmail(STUDY, EMAIL);
            verify(noCreateStormpathAccountDao).getAccountWithEmail(STUDY, EMAIL);

            verify(noCreateHibernateAccountDao, never()).updateAccount(any());
        }

        // no MySQL case
        {
            when(noMySqlStormpathAccountDao.getAccountWithEmail(STUDY, EMAIL)).thenReturn(mockStormpathAccount);

            MigrationAccount retval = (MigrationAccount) noMySqlMigrationAccountDao.getAccountWithEmail(STUDY, EMAIL);
            assertNull(retval.getGenericAccount());
            assertSame(mockStormpathAccount, retval.getStormpathAccount());

            verify(noMySqlHibernateAccountDao, never()).getAccountWithEmail(any(), any());
            verify(noMySqlStormpathAccountDao).getAccountWithEmail(STUDY, EMAIL);

            verify(noMySqlHibernateAccountDao, never()).updateAccount(any());
        }
    }

    @Test
    public void getAccountWithEmailWithDifferentHealthCodes() {
        // setup test
        GenericAccount genericAccount = new GenericAccount();
        genericAccount.setHealthCode("different-health-code");
        genericAccount.setHealthId("different-health-id");

        when(mySqlHibernateAccountDao.getAccountWithEmail(STUDY, EMAIL)).thenReturn(genericAccount);
        when(mySqlStormpathAccountDao.getAccountWithEmail(STUDY, EMAIL)).thenReturn(mockStormpathAccount);

        // Execute and validate health codes.
        MigrationAccount retval = (MigrationAccount) mySqlMigrationAccountDao.getAccountWithEmail(STUDY, EMAIL);
        assertSame(HEALTH_CODE, retval.getGenericAccount().getHealthCode());
        assertSame(HEALTH_ID_STRING, retval.getGenericAccount().getHealthId());
        assertSame(HEALTH_CODE, retval.getStormpathAccount().getHealthCode());
        assertSame(HEALTH_ID_STRING, retval.getStormpathAccount().getHealthId());

        // Validate we save the generic account with the new health code mapping.
        ArgumentCaptor<GenericAccount> updatedAccountCaptor = ArgumentCaptor.forClass(GenericAccount.class);
        verify(mySqlHibernateAccountDao).updateAccount(updatedAccountCaptor.capture());

        GenericAccount updatedAccount = updatedAccountCaptor.getValue();
        assertEquals(HEALTH_CODE, updatedAccount.getHealthCode());
        assertEquals(HEALTH_ID_STRING, updatedAccount.getHealthId());
    }

    @Test
    public void deleteAccount() {
        // MySQL case
        {
            when(mySqlHibernateAccountDao.getAccount(STUDY, ACCOUNT_ID)).thenReturn(GENERIC_ACCOUNT);
            when(mySqlStormpathAccountDao.getAccount(STUDY, ACCOUNT_ID)).thenReturn(mockStormpathAccount);

            mySqlMigrationAccountDao.deleteAccount(STUDY, ACCOUNT_ID);

            verify(mySqlHibernateAccountDao).getAccount(STUDY, ACCOUNT_ID);
            verify(mySqlHibernateAccountDao).deleteAccount(STUDY, ACCOUNT_ID);

            verify(mySqlStormpathAccountDao).getAccount(STUDY, ACCOUNT_ID);
            verify(mySqlStormpathAccountDao).deleteAccount(STUDY, ACCOUNT_ID);
        }

        // no-create case
        {
            when(noCreateHibernateAccountDao.getAccount(STUDY, ACCOUNT_ID)).thenReturn(GENERIC_ACCOUNT);
            when(noCreateStormpathAccountDao.getAccount(STUDY, ACCOUNT_ID)).thenReturn(mockStormpathAccount);

            noCreateMigrationAccountDao.deleteAccount(STUDY, ACCOUNT_ID);

            verify(noCreateHibernateAccountDao).getAccount(STUDY, ACCOUNT_ID);
            verify(noCreateHibernateAccountDao).deleteAccount(STUDY, ACCOUNT_ID);

            verify(noCreateStormpathAccountDao).getAccount(STUDY, ACCOUNT_ID);
            verify(noCreateStormpathAccountDao).deleteAccount(STUDY, ACCOUNT_ID);
        }

        // no MySQL case
        {
            when(noMySqlStormpathAccountDao.getAccount(STUDY, ACCOUNT_ID)).thenReturn(mockStormpathAccount);

            noMySqlMigrationAccountDao.deleteAccount(STUDY, ACCOUNT_ID);

            verify(noMySqlHibernateAccountDao, never()).getAccount(any(), any());
            verify(noMySqlHibernateAccountDao, never()).deleteAccount(any(), any());

            verify(noMySqlStormpathAccountDao).getAccount(STUDY, ACCOUNT_ID);
            verify(noMySqlStormpathAccountDao).deleteAccount(STUDY, ACCOUNT_ID);
        }
    }

    @Test
    public void deleteAccountNoAccountInMySql() {
        when(mySqlHibernateAccountDao.getAccount(STUDY, ACCOUNT_ID)).thenReturn(null);
        when(mySqlStormpathAccountDao.getAccount(STUDY, ACCOUNT_ID)).thenReturn(mockStormpathAccount);

        mySqlMigrationAccountDao.deleteAccount(STUDY, ACCOUNT_ID);

        verify(mySqlHibernateAccountDao).getAccount(STUDY, ACCOUNT_ID);
        verify(mySqlHibernateAccountDao, never()).deleteAccount(any(), any());

        verify(mySqlStormpathAccountDao).getAccount(STUDY, ACCOUNT_ID);
        verify(mySqlStormpathAccountDao).deleteAccount(STUDY, ACCOUNT_ID);
    }

    @Test
    public void deleteAccountNoAccountInStormpath() {
        when(mySqlHibernateAccountDao.getAccount(STUDY, ACCOUNT_ID)).thenReturn(GENERIC_ACCOUNT);
        when(mySqlStormpathAccountDao.getAccount(STUDY, ACCOUNT_ID)).thenReturn(null);

        mySqlMigrationAccountDao.deleteAccount(STUDY, ACCOUNT_ID);

        verify(mySqlHibernateAccountDao).getAccount(STUDY, ACCOUNT_ID);
        verify(mySqlHibernateAccountDao).deleteAccount(STUDY, ACCOUNT_ID);

        verify(mySqlStormpathAccountDao).getAccount(STUDY, ACCOUNT_ID);
        verify(mySqlStormpathAccountDao, never()).deleteAccount(any(), any());
    }

    @Test
    public void deleteAccountNoAccountAnywhere() {
        when(mySqlHibernateAccountDao.getAccount(STUDY, ACCOUNT_ID)).thenReturn(null);
        when(mySqlStormpathAccountDao.getAccount(STUDY, ACCOUNT_ID)).thenReturn(null);

        mySqlMigrationAccountDao.deleteAccount(STUDY, ACCOUNT_ID);

        verify(mySqlHibernateAccountDao).getAccount(STUDY, ACCOUNT_ID);
        verify(mySqlHibernateAccountDao, never()).deleteAccount(any(), any());

        verify(mySqlStormpathAccountDao).getAccount(STUDY, ACCOUNT_ID);
        verify(mySqlStormpathAccountDao, never()).deleteAccount(any(), any());
    }

    @Test
    public void getAllAccounts() {
        // Until we backfill, all accounts are in Stormpath, so we just call the Stormpath version.
        List<AccountSummary> accountSummaryList = ImmutableList.of(ACCOUNT_SUMMARY);

        // MySQL case
        {
            when(mySqlStormpathAccountDao.getAllAccounts()).thenReturn(accountSummaryList.iterator());

            Iterator<AccountSummary> retval = mySqlMigrationAccountDao.getAllAccounts();
            assertTrue(retval.hasNext());
            assertEquals(ACCOUNT_SUMMARY, retval.next());
            assertFalse(retval.hasNext());

            verify(mySqlStormpathAccountDao).getAllAccounts();
            verify(mySqlHibernateAccountDao, never()).getAllAccounts();
        }

        // no-create case
        {
            when(noCreateStormpathAccountDao.getAllAccounts()).thenReturn(accountSummaryList.iterator());

            Iterator<AccountSummary> retval = noCreateMigrationAccountDao.getAllAccounts();
            assertTrue(retval.hasNext());
            assertEquals(ACCOUNT_SUMMARY, retval.next());
            assertFalse(retval.hasNext());

            verify(noCreateStormpathAccountDao).getAllAccounts();
            verify(noCreateHibernateAccountDao, never()).getAllAccounts();
        }

        // no MySQL case
        {
            when(noMySqlStormpathAccountDao.getAllAccounts()).thenReturn(accountSummaryList.iterator());

            Iterator<AccountSummary> retval = noMySqlMigrationAccountDao.getAllAccounts();
            assertTrue(retval.hasNext());
            assertEquals(ACCOUNT_SUMMARY, retval.next());
            assertFalse(retval.hasNext());

            verify(noMySqlStormpathAccountDao).getAllAccounts();
            verify(noMySqlHibernateAccountDao, never()).getAllAccounts();
        }
    }

    @Test
    public void getStudyAccounts() {
        // Until we backfill, all accounts are in Stormpath, so we just call the Stormpath version.
        List<AccountSummary> accountSummaryList = ImmutableList.of(ACCOUNT_SUMMARY);

        // MySQL case
        {
            when(mySqlStormpathAccountDao.getStudyAccounts(STUDY)).thenReturn(accountSummaryList.iterator());

            Iterator<AccountSummary> retval = mySqlMigrationAccountDao.getStudyAccounts(STUDY);
            assertTrue(retval.hasNext());
            assertEquals(ACCOUNT_SUMMARY, retval.next());
            assertFalse(retval.hasNext());

            verify(mySqlStormpathAccountDao).getStudyAccounts(STUDY);
            verify(mySqlHibernateAccountDao, never()).getStudyAccounts(any());
        }

        // no-create case
        {
            when(noCreateStormpathAccountDao.getStudyAccounts(STUDY)).thenReturn(accountSummaryList.iterator());

            Iterator<AccountSummary> retval = noCreateMigrationAccountDao.getStudyAccounts(STUDY);
            assertTrue(retval.hasNext());
            assertEquals(ACCOUNT_SUMMARY, retval.next());
            assertFalse(retval.hasNext());

            verify(noCreateStormpathAccountDao).getStudyAccounts(STUDY);
            verify(noCreateHibernateAccountDao, never()).getStudyAccounts(any());
        }

        // no MySQL case
        {
            when(noMySqlStormpathAccountDao.getStudyAccounts(STUDY)).thenReturn(accountSummaryList.iterator());

            Iterator<AccountSummary> retval = noMySqlMigrationAccountDao.getStudyAccounts(STUDY);
            assertTrue(retval.hasNext());
            assertEquals(ACCOUNT_SUMMARY, retval.next());
            assertFalse(retval.hasNext());

            verify(noMySqlStormpathAccountDao).getStudyAccounts(STUDY);
            verify(noMySqlHibernateAccountDao, never()).getStudyAccounts(any());
        }
    }

    @Test
    public void getPagedAccountSummaries() {
        // Until we backfill, all accounts are in Stormpath, so we just call the Stormpath version.
        List<AccountSummary> accountSummaryList = ImmutableList.of(ACCOUNT_SUMMARY);
        PagedResourceList<AccountSummary> pagedAccountSummaryList = new PagedResourceList<>(accountSummaryList,
                null, 10, 1);

        // MySQL case
        {
            when(mySqlStormpathAccountDao.getPagedAccountSummaries(STUDY, 0, 10, null, null, null)).thenReturn(
                    pagedAccountSummaryList);

            PagedResourceList<AccountSummary> retval = mySqlMigrationAccountDao.getPagedAccountSummaries(STUDY, 0, 10,
                    null, null, null);
            assertSame(pagedAccountSummaryList, retval);

            verify(mySqlStormpathAccountDao).getPagedAccountSummaries(STUDY, 0, 10, null, null, null);
            verify(mySqlHibernateAccountDao, never()).getPagedAccountSummaries(any(), anyInt(), anyInt(), any(), any(),
                    any());
        }

        // no-create case
        {
            when(noCreateStormpathAccountDao.getPagedAccountSummaries(STUDY, 0, 10, null, null, null)).thenReturn(
                    pagedAccountSummaryList);

            PagedResourceList<AccountSummary> retval = noCreateMigrationAccountDao.getPagedAccountSummaries(STUDY, 0,
                    10, null, null, null);
            assertSame(pagedAccountSummaryList, retval);

            verify(noCreateStormpathAccountDao).getPagedAccountSummaries(STUDY, 0, 10, null, null, null);
            verify(noCreateHibernateAccountDao, never()).getPagedAccountSummaries(any(), anyInt(), anyInt(), any(),
                    any(), any());
        }

        // no MySQL case
        {
            when(noMySqlStormpathAccountDao.getPagedAccountSummaries(STUDY, 0, 10, null, null, null)).thenReturn(
                    pagedAccountSummaryList);

            PagedResourceList<AccountSummary> retval = noMySqlMigrationAccountDao.getPagedAccountSummaries(STUDY, 0,
                    10, null, null, null);
            assertSame(pagedAccountSummaryList, retval);

            verify(noMySqlStormpathAccountDao).getPagedAccountSummaries(STUDY, 0, 10, null, null, null);
            verify(noMySqlHibernateAccountDao, never()).getPagedAccountSummaries(any(), anyInt(), anyInt(), any(),
                    any(), any());
        }
    }
}
