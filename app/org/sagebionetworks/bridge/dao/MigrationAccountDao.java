package org.sagebionetworks.bridge.dao;

import java.util.Iterator;
import java.util.function.BiConsumer;
import java.util.function.Function;

import com.google.common.base.Preconditions;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.hibernate.HibernateAccountDao;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.Email;
import org.sagebionetworks.bridge.models.accounts.EmailVerification;
import org.sagebionetworks.bridge.models.accounts.GenericAccount;
import org.sagebionetworks.bridge.models.accounts.HealthId;
import org.sagebionetworks.bridge.models.accounts.MigrationAccount;
import org.sagebionetworks.bridge.models.accounts.PasswordReset;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.services.HealthCodeService;
import org.sagebionetworks.bridge.stormpath.StormpathAccount;
import org.sagebionetworks.bridge.stormpath.StormpathAccountDao;

/**
 * <p>
 * This DAO encapsulates both the Hibernate (MySQL) Account DAO and the Stormpath Account DAO. In general, we want to
 * read from MySQL and fallback to Stormpath, and write to both. In a few cases, such as list APIs, we still read from
 * Stormpath since not all accounts are in MySQL yet.
 * </p>
 * <p>
 * In general, beans that consume this autowire an AccountDao by type. We mark this bean with the @Primary annotation
 * so Spring knows which of the three AccountDao beans to pick. (Otherwise, it falls back to autowiring by name using
 * the implicit name.)
 * </p>
 */
@Component
@Primary
public class MigrationAccountDao implements AccountDao {
    final static String CONFIG_KEY_AUTH_PROVIDER = "auth.provider";
    final static String CONFIG_KEY_CREATE_ACCOUNTS = "auth.create.mysql.accounts";

    private boolean createMySqlAccounts;
    private boolean useMySqlAuth;
    private HealthCodeService healthCodeService;
    private HibernateAccountDao hibernateAccountDao;
    private StormpathAccountDao stormpathAccountDao;

    /** Spring setter for Config, from which we get the flags that control whether we use MySQL or not. */
    @Autowired
    public final void setConfig(BridgeConfig config) {
        // Flag to enable MySQL logic.
        String authProvider = config.get(CONFIG_KEY_AUTH_PROVIDER);
        useMySqlAuth = "mysql".equalsIgnoreCase(authProvider);

        // Flag to separately enable creating accounts in MySQL (most useful for testing legacy case). If useMySqlAuth
        // is disabled, this flag is also automatically disabled.
        createMySqlAccounts = Boolean.valueOf(config.get(CONFIG_KEY_CREATE_ACCOUNTS)) && useMySqlAuth;
    }

    /** Health Code Service, so we can create one Health Code mapping and use it for both MySQL and Stormpath. */
    @Autowired
    public final void setHealthCodeService(HealthCodeService healthCodeService) {
        this.healthCodeService = healthCodeService;
    }

    /** Hibernate (MySQL) Account DAO */
    @Autowired
    public final void setHibernateAccountDao(HibernateAccountDao hibernateAccountDao) {
        this.hibernateAccountDao = hibernateAccountDao;
    }

    /** Stormpath Account DAO */
    @Autowired
    public final void setStormpathAccountDao(StormpathAccountDao stormpathAccountDao) {
        this.stormpathAccountDao = stormpathAccountDao;
    }

    /** {@inheritDoc} */
    @Override
    public void verifyEmail(EmailVerification verification) {
        // Both Hibernate and Stormpath implementations do the same thing. For the purposes of the migration, just call
        // the Hibernate version.
        hibernateAccountDao.verifyEmail(verification);
    }

    /** {@inheritDoc} */
    @Override
    public void resendEmailVerificationToken(StudyIdentifier studyIdentifier, Email email) {
        // Both Hibernate and Stormpath implementations do the same thing. For the purposes of the migration, just call
        // the Hibernate version.
        hibernateAccountDao.resendEmailVerificationToken(studyIdentifier, email);
    }

    /** {@inheritDoc} */
    @Override
    public void requestResetPassword(Study study, Email email) {
        // Both Hibernate and Stormpath implementations do the same thing. For the purposes of the migration, just call
        // the Hibernate version.
        hibernateAccountDao.requestResetPassword(study, email);
    }

    /** {@inheritDoc} */
    @Override
    public void resetPassword(PasswordReset passwordReset) {
        // Both Hibernate and Stormpath implementations do the same thing. For the purposes of the migration, just call
        // the Hibernate version.
        hibernateAccountDao.resetPassword(passwordReset);
    }

    /** {@inheritDoc} */
    @Override
    public void changePassword(Account account, String newPassword) {
        writeWithFallback((dao, typedAccount) -> dao.changePassword(typedAccount, newPassword), account);
    }

    /** {@inheritDoc} */
    @Override
    public Account authenticate(Study study, SignIn signIn) {
        return readWithFallback(dao -> dao.authenticate(study, signIn));
    }

    /** {@inheritDoc} */
    @Override
    public Account constructAccount(Study study, String email, String password) {
        // Create Health Code mapping.
        HealthId healthId = healthCodeService.createMapping(study.getStudyIdentifier());

        // Construct MySQL account.
        GenericAccount genericAccount = null;
        if (createMySqlAccounts) {
            genericAccount = (GenericAccount) hibernateAccountDao.constructAccountForMigration(study, email, password,
                    healthId);
        }

        // Construct Stormpath account.
        StormpathAccount stormpathAccount = (StormpathAccount) stormpathAccountDao.constructAccountForMigration(study,
                email, password, healthId);

        // Stuff both into a MigrationAccount and return.
        return new MigrationAccount(genericAccount, stormpathAccount);
    }

    /** {@inheritDoc} */
    @Override
    public String createAccount(Study study, final Account account, boolean sendVerifyEmail) {
        // For the same reason as writeWithFallback(), this should always be a MigrationAccount.
        if (!(account instanceof MigrationAccount)) {
            throw new BridgeServiceException("Expected MigrationAccount, instead was " + account.getClass()
                    .getSimpleName());
        }
        MigrationAccount migrationAccount = (MigrationAccount) account;

        // Create account is special. During the transition period, while we're creating accounts in both MySQL and in
        // Stormpath, we want to ensure the account IDs are the same in both. As such, we'll call
        // StormpathAccountDao.createAccount() first, then call the special method
        // HibernateAccountDao.createAccountForMigration().

        // Create in Stormpath and record the account ID.
        // Note that since the account comes from constructAccount(), it will always have StormpathAccount.
        String accountId = stormpathAccountDao.createAccount(study, migrationAccount.getStormpathAccount(),
                sendVerifyEmail);

        // Create account in MySQL with the same account ID. Note that if createMySqlAccounts is true, the
        // MigrationAccount will always have a GenericAccount.
        if (createMySqlAccounts) {
            hibernateAccountDao.createAccountForMigration(study, migrationAccount.getGenericAccount(), accountId,
                    sendVerifyEmail);
        }

        return accountId;
    }

    /** {@inheritDoc} */
    @Override
    public void updateAccount(Account account) {
        writeWithFallback(AccountDao::updateAccount, account);
    }

    /** {@inheritDoc} */
    @Override
    public Account getAccount(Study study, String id) {
        return readWithFallback(dao -> dao.getAccount(study, id));
    }

    /** {@inheritDoc} */
    @Override
    public Account getAccountWithEmail(Study study, String email) {
        return readWithFallback(dao -> dao.getAccountWithEmail(study, email));
    }

    /** {@inheritDoc} */
    @Override
    public void deleteAccount(Study study, String id) {
        // Another special case. To make sure we don't try to delete accounts that don't exist, get the account by ID,
        // then delete it.
        Account account = getAccount(study, id);
        if (account == null) {
            return;
        }

        writeWithFallback((dao, typedAccount) -> dao.deleteAccount(study, id), account);
    }

    /** {@inheritDoc} */
    @Override
    public Iterator<AccountSummary> getAllAccounts() {
        // During the migration, MySQL might not have all the accounts. Therefore, just get the Stormpath
        // implementation.
        return stormpathAccountDao.getAllAccounts();
    }

    /** {@inheritDoc} */
    @Override
    public Iterator<AccountSummary> getStudyAccounts(Study study) {
        // During the migration, MySQL might not have all the accounts. Therefore, just get the Stormpath
        // implementation.
        return stormpathAccountDao.getStudyAccounts(study);
    }

    /** {@inheritDoc} */
    @Override
    public PagedResourceList<AccountSummary> getPagedAccountSummaries(Study study, int offsetBy, int pageSize,
            String emailFilter, DateTime startDate, DateTime endDate) {
        // During the migration, MySQL might not have all the accounts. Therefore, just get the Stormpath
        // implementation.
        return stormpathAccountDao.getPagedAccountSummaries(study, offsetBy, pageSize, emailFilter, startDate,
                endDate);
    }

    // Helper function which encapsulates reading from both Hibernate and Stormpath.
    private Account readWithFallback(Function<AccountDao, Account> func) {
        // Read from MySQL first.
        GenericAccount genericAccount = null;
        if (useMySqlAuth) {
            try {
                genericAccount = (GenericAccount) func.apply(hibernateAccountDao);
            } catch (RuntimeException ex) {
                // Squelch error. Accounts might not exist in MySQL yet if we haven't migrated the data over, and we
                // don't want to spam the logs.
            }
        }

        // Read from Stormpath next.
        StormpathAccount stormpathAccount = (StormpathAccount) func.apply(stormpathAccountDao);

        if (genericAccount == null && stormpathAccount == null) {
            // This means the account doesn't exist. In keeping with the underlying implementation, we should return
            // null instead of an empty MigrationAccount.
            return null;
        }

        // Stuff both into a MigrationAccount and return.
        return new MigrationAccount(genericAccount, stormpathAccount);
    }

    // Helper function to write to both Hibernate and Stormpath.
    private void writeWithFallback(BiConsumer<AccountDao, Account> func, final Account account) {
        Preconditions.checkNotNull(account);

        // This should always be passed a MigrationAccount. This MigrationAccount always comes from somewhere else in
        // MigrationAccountDao, such as constructAccount() or getAccount(). As such, it should always have
        // genericAccount and stormpathAccount filled in, or else they don't exist.
        if (!(account instanceof MigrationAccount)) {
            throw new BridgeServiceException("Expected MigrationAccount, instead was " + account.getClass()
                    .getSimpleName());
        }
        MigrationAccount migrationAccount = (MigrationAccount) account;

        // Write to MySQL. If this fails, propagate the error.
        if (useMySqlAuth) {
            GenericAccount genericAccount = migrationAccount.getGenericAccount();
            if (genericAccount != null) {
                func.accept(hibernateAccountDao, genericAccount);
            }
        }

        // Then write to Stormpath.
        StormpathAccount stormpathAccount = migrationAccount.getStormpathAccount();
        if (stormpathAccount != null) {
            func.accept(stormpathAccountDao, stormpathAccount);
        }
    }
}
