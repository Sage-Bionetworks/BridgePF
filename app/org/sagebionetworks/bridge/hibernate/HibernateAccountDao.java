package org.sagebionetworks.bridge.hibernate;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.services.AuthenticationService.ChannelType.EMAIL;
import static org.sagebionetworks.bridge.services.AuthenticationService.ChannelType.PHONE;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.collect.ImmutableMap;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.BridgeUtils.SubstudyAssociations;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.SecureTokenGenerator;
import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.exceptions.AccountDisabledException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.time.DateUtils;
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
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.services.AuthenticationService;
import org.sagebionetworks.bridge.services.AuthenticationService.ChannelType;

/** Hibernate implementation of Account Dao. */
@Component
public class HibernateAccountDao implements AccountDao {
    
    private static final Logger LOG = LoggerFactory.getLogger(HibernateAccountDao.class);
    
    static final String SUMMARY_QUERY = "SELECT new HibernateAccount(acct.createdOn, acct.studyId, "+
            "acct.firstName, acct.lastName, acct.email, acct.phone, acct.externalId, acct.id, acct.status) "+
            "FROM HibernateAccount AS acct";
            
    static final String FULL_QUERY = "SELECT acct FROM HibernateAccount AS acct";
    
    static final String COUNT_QUERY = "SELECT COUNT(DISTINCT acct.id) FROM HibernateAccount AS acct";
    
    private HibernateHelper hibernateHelper;
    private CacheProvider cacheProvider;

    /** This makes interfacing with Hibernate easier. */
    @Resource(name = "accountHibernateHelper")
    public final void setHibernateHelper(HibernateHelper hibernateHelper) {
        this.hibernateHelper = hibernateHelper;
    }
    
    @Autowired
    public final void setCacheProvider(CacheProvider cacheProvider) {
        this.cacheProvider = cacheProvider;
    }

    /**
     * Mark the email address as verified and enable the account if it is in the unverified state. 
     * This method assumes some logic has executed that proves the user has control of the email 
     * address.
     */
    @Override
    public void verifyChannel(AuthenticationService.ChannelType channelType, Account account) {
        checkNotNull(channelType);
        checkNotNull(account);
        
        // Do not modify the account if it is disabled (all email verification workflows are 
        // user triggered, and disabled means that a user cannot use or change an account).
        if (account.getStatus() == AccountStatus.DISABLED) {
            return;
        }
        
        // Avoid updating on every sign in by examining object state first.
        boolean shouldUpdateEmailVerified = (channelType == EMAIL && account.getEmailVerified() != Boolean.TRUE);
        boolean shouldUpdatePhoneVerified = (channelType == PHONE && account.getPhoneVerified() != Boolean.TRUE);
        boolean shouldUpdateStatus = (account.getStatus() == AccountStatus.UNVERIFIED);
        
        if (shouldUpdatePhoneVerified || shouldUpdateEmailVerified || shouldUpdateStatus) {
            HibernateAccount hibernateAccount = hibernateHelper.getById(HibernateAccount.class, account.getId());
            if (hibernateAccount == null) {
                throw new EntityNotFoundException(Account.class);
            }
            if (shouldUpdateEmailVerified) {
                account.setEmailVerified(Boolean.TRUE);
                hibernateAccount.setEmailVerified(Boolean.TRUE);
            }
            if (shouldUpdatePhoneVerified) {
                account.setPhoneVerified(Boolean.TRUE);
                hibernateAccount.setPhoneVerified(Boolean.TRUE);
            }
            if (shouldUpdateStatus) {
                account.setStatus(AccountStatus.ENABLED);
                hibernateAccount.setStatus(AccountStatus.ENABLED);
            }
            hibernateAccount.setModifiedOn(DateUtils.getCurrentDateTime());
            hibernateHelper.update(hibernateAccount, null);    
        }
    }

    /** {@inheritDoc} */
    @Override
    public void changePassword(Account account, ChannelType channelType, String newPassword) {
        String accountId = account.getId();
        PasswordAlgorithm passwordAlgorithm = PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM;
        
        String passwordHash = hashCredential(passwordAlgorithm, "password", newPassword);

        // We have to load and update the whole account in order to use Hibernate's optimistic versioning.
        HibernateAccount hibernateAccount = hibernateHelper.getById(HibernateAccount.class, accountId);
        if (hibernateAccount == null) {
            throw new EntityNotFoundException(Account.class, "Account " + accountId + " not found");
        }

        // Update
        DateTime modifiedOn = DateUtils.getCurrentDateTime();
        hibernateAccount.setModifiedOn(modifiedOn);
        hibernateAccount.setPasswordAlgorithm(passwordAlgorithm);
        hibernateAccount.setPasswordHash(passwordHash);
        hibernateAccount.setPasswordModifiedOn(modifiedOn);
        // One of these (the channel used to reset the password) is also verified by resetting the password.
        if (channelType == ChannelType.EMAIL) {
            hibernateAccount.setStatus(AccountStatus.ENABLED);
            hibernateAccount.setEmailVerified(true);    
        } else if (channelType == ChannelType.PHONE) {
            hibernateAccount.setStatus(AccountStatus.ENABLED);
            hibernateAccount.setPhoneVerified(true);    
        } else if (channelType == null) {
            // If there's no channel type, we're assuming a password-based sign-in using
            // external ID (the third identifying credential that can be used), so here
            // we will enable the account.
            hibernateAccount.setStatus(AccountStatus.ENABLED);
        }
        hibernateHelper.update(hibernateAccount, null);
    }

    /** {@inheritDoc} */
    @Override
    public Account authenticate(Study study, SignIn signIn) {
        Account hibernateAccount = fetchHibernateAccount(signIn);
        return authenticateInternal(study, hibernateAccount, hibernateAccount.getPasswordAlgorithm(),
                hibernateAccount.getPasswordHash(), signIn.getPassword(), "password", signIn);
    }

    /** {@inheritDoc} */
    @Override
    public Account reauthenticate(Study study, SignIn signIn) {
        if (!study.isReauthenticationEnabled()) {
            throw new UnauthorizedException("Reauthentication is not enabled for study: " + study.getName());    
        }
        Account hibernateAccount = fetchHibernateAccount(signIn);
        return authenticateInternal(study, hibernateAccount, hibernateAccount.getReauthTokenAlgorithm(),
                hibernateAccount.getReauthTokenHash(), signIn.getReauthToken(), "reauth token", signIn);
    }
    
    private Account authenticateInternal(Study study, Account hibernateAccount, PasswordAlgorithm algorithm,
            String hash, String credentialValue, String credentialName, SignIn signIn) {

        // First check and throw an entity not found exception if the password is wrong.
        verifyCredential(hibernateAccount.getId(), credentialName, algorithm, hash, credentialValue);
        
        // Password successful, you can now leak further information about the account through other exceptions.
        // For email/phone sign ins, the specific credential must have been verified (unless we've disabled
        // email verification for older studies that didn't have full external ID support).
        if (hibernateAccount.getStatus() == AccountStatus.UNVERIFIED) {
            throw new UnauthorizedException("Email or phone number have not been verified");
        } else if (hibernateAccount.getStatus() == AccountStatus.DISABLED) {
            throw new AccountDisabledException();
        } else if (study.isVerifyChannelOnSignInEnabled()) {
            if (signIn.getPhone() != null && !Boolean.TRUE.equals(hibernateAccount.getPhoneVerified())) {
                throw new UnauthorizedException("Phone number has not been verified");
            } else if (study.isEmailVerificationEnabled() && 
                    signIn.getEmail() != null && !Boolean.TRUE.equals(hibernateAccount.getEmailVerified())) {
                throw new UnauthorizedException("Email has not been verified");
            }
        }
        
        // Unmarshall account
        boolean accountUpdated = validateHealthCode(hibernateAccount);
        accountUpdated = updateReauthToken(study, hibernateAccount) || accountUpdated;
        if (accountUpdated) {
            Account updated = hibernateHelper.update(hibernateAccount, null);
            hibernateAccount.setVersion(updated.getVersion());
        }
        return hibernateAccount;
    }

    @Override
    public Account getAccountAfterAuthentication(AccountId accountId) {
        Account hibernateAccount = getHibernateAccount(accountId);

        if (hibernateAccount != null) {
            boolean accountUpdated = validateHealthCode(hibernateAccount);
            accountUpdated = updateReauthToken(null, hibernateAccount) || accountUpdated;
            if (accountUpdated) {
                Account updated = hibernateHelper.update(hibernateAccount, null);
                hibernateAccount.setVersion(updated.getVersion());
            }
            return hibernateAccount;
        } else {
            // In keeping with the email implementation, just return null
            return null;
        }
    }

    @Override
    public void deleteReauthToken(AccountId accountId) {
        Account hibernateAccount = getHibernateAccount(accountId);
        if (hibernateAccount != null && hibernateAccount.getReauthTokenHash() != null) {
            hibernateAccount.setReauthTokenHash(null);
            hibernateAccount.setReauthTokenAlgorithm(null);
            hibernateAccount.setReauthTokenModifiedOn(null);
            hibernateHelper.update(hibernateAccount, null);
        }
    }
    
    private boolean updateReauthToken(Study study, Account hibernateAccount) {
        if (study != null && !study.isReauthenticationEnabled()) {
            hibernateAccount.setReauthToken(null);
            return false;
        }
        CacheKey reauthTokenKey = CacheKey.reauthTokenLookupKey(hibernateAccount.getId(),
                new StudyIdentifierImpl(hibernateAccount.getStudyId()));
        
        // We cache the reauthentication token for 15 seconds so that concurrent sign in 
        // requests don't throw 409 concurrent modification exceptions as an optimistic lock
        // prevents (correctly) updating the reauth token that is issued over and over.
        String cachedReauthToken = cacheProvider.getObject(reauthTokenKey, String.class);
        if (cachedReauthToken != null) {
            hibernateAccount.setReauthToken(cachedReauthToken);
            return false;
        }
        String reauthToken = SecureTokenGenerator.INSTANCE.nextToken();
        cacheProvider.setObject(reauthTokenKey, reauthToken, BridgeConstants.REAUTH_TOKEN_CACHE_LOOKUP_IN_SECONDS);

        // Re-create and persist the authentication token.
        PasswordAlgorithm passwordAlgorithm = PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM;
        String reauthTokenHash = hashCredential(passwordAlgorithm, "reauth token", reauthToken);
        hibernateAccount.setReauthTokenHash(reauthTokenHash);
        hibernateAccount.setReauthTokenAlgorithm(passwordAlgorithm);
        hibernateAccount.setReauthTokenModifiedOn(DateUtils.getCurrentDateTime());
        // We must get the current version to return from the DAO, or subsequent updates to the 
        // account, even in the same call, will fail (e.g. to update languages captured from a request).
        hibernateAccount.setReauthToken(reauthToken);
        return true;
    }
    
    /** {@inheritDoc} */
    @Override
    public Account constructAccount(Study study, String email, Phone phone, String externalId, String password) {
        // Set basic params from inputs.
        Account account = Account.create();
        account.setId(generateGUID());
        account.setStudyId(study.getIdentifier());
        account.setEmail(email);
        account.setPhone(phone);
        account.setEmailVerified(Boolean.FALSE);
        account.setPhoneVerified(Boolean.FALSE);
        account.setHealthCode(generateGUID());
        account.setExternalId(externalId);

        // Hash password if it has been supplied.
        if (password != null) {
            PasswordAlgorithm passwordAlgorithm = PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM;
            String passwordHash = hashCredential(passwordAlgorithm, "password", password);

            account.setPasswordAlgorithm(passwordAlgorithm);
            account.setPasswordHash(passwordHash);
        }
        return account;
    }
    
    // Provided to override in tests
    protected String generateGUID() {
        return BridgeUtils.generateGuid();
    }

    /** {@inheritDoc} */
    @Override
    public void createAccount(Study study, Account account, Consumer<Account> afterPersistConsumer) {
        account.setStudyId(study.getIdentifier());
        DateTime timestamp = DateUtils.getCurrentDateTime();
        account.setCreatedOn(timestamp);
        account.setModifiedOn(timestamp);
        account.setPasswordModifiedOn(timestamp);
        account.setMigrationVersion(AccountDao.MIGRATION_VERSION);

        // Create account. We don't verify substudies because this is handled by validation
        hibernateHelper.create(account, afterPersistConsumer);
    }

    /** {@inheritDoc} */
    @Override
    public void updateAccount(Account account, Consumer<Account> afterPersistConsumer) {
        String accountId = account.getId();

        // Can't change study, email, phone, emailVerified, phoneVerified, createdOn, or passwordModifiedOn.
        HibernateAccount persistedAccount = hibernateHelper.getById(HibernateAccount.class, accountId);
        if (persistedAccount == null) {
            throw new EntityNotFoundException(Account.class, "Account " + accountId + " not found");
        }
        // None of these values should be changeable by the user.
        account.setStudyId(persistedAccount.getStudyId());
        account.setCreatedOn(persistedAccount.getCreatedOn());
        account.setPasswordAlgorithm(persistedAccount.getPasswordAlgorithm());
        account.setPasswordHash(persistedAccount.getPasswordHash());
        account.setPasswordModifiedOn(persistedAccount.getPasswordModifiedOn());
        account.setReauthTokenAlgorithm(persistedAccount.getReauthTokenAlgorithm());
        account.setReauthTokenHash(persistedAccount.getReauthTokenHash());
        account.setReauthTokenModifiedOn(persistedAccount.getReauthTokenModifiedOn());
        // Update modifiedOn.
        account.setModifiedOn(DateUtils.getCurrentDateTime());

        // Update. We don't verify substudies because this is handled by validation
        hibernateHelper.update(account, afterPersistConsumer);            
    }
    
    /** {@inheritDoc} */
    @Override
    public void editAccount(StudyIdentifier studyId, String healthCode, Consumer<Account> accountEdits) {
        AccountId accountId = AccountId.forHealthCode(studyId.getIdentifier(), healthCode);
        Account account = BridgeUtils.filterForSubstudy( getAccount(accountId) );
        
        if (account != null) {
            accountEdits.accept(account);
            updateAccount(account, null);
        }
    }

    /** {@inheritDoc} */
    @Override
    public Account getAccount(AccountId accountId) {
        Account hibernateAccount = getHibernateAccount(accountId);
        if (hibernateAccount != null) {
            boolean accountUpdated = validateHealthCode(hibernateAccount);
            if (accountUpdated) {
                Account updated = hibernateHelper.update(hibernateAccount, null);
                hibernateAccount.setVersion(updated.getVersion());
            }
            return hibernateAccount;
        } else {
            // In keeping with the email implementation, just return null
            return null;
        }
    }

    private Account fetchHibernateAccount(SignIn signIn) {
        // Fetch account
        Account hibernateAccount = getHibernateAccount(signIn.getAccountId());
        if (hibernateAccount == null) {
            throw new EntityNotFoundException(Account.class);
        }
        return hibernateAccount;
    }
    
    private void verifyCredential(String accountId, String type, PasswordAlgorithm algorithm, String hash,
            String credentialValue) {
        // Verify credential (password or reauth token)
        if (algorithm == null || StringUtils.isBlank(hash)) {
            LOG.warn("Account " + accountId + " is enabled but has no "+type+".");
            throw new EntityNotFoundException(Account.class);
        }
        try {
            if (!algorithm.checkHash(hash, credentialValue)) {
                // To prevent enumeration attacks, if the credential doesn't match, throw 404 account not found.
                throw new EntityNotFoundException(Account.class);
            }
        } catch (InvalidKeyException | InvalidKeySpecException | NoSuchAlgorithmException ex) {
            throw new BridgeServiceException("Error validating "+type+": " + ex.getMessage(), ex);
        }        
    }
    
    private String hashCredential(PasswordAlgorithm algorithm, String type, String value) {
        String hash = null;
        try {
            hash = algorithm.generateHash(value);
        } catch (InvalidKeyException | InvalidKeySpecException | NoSuchAlgorithmException ex) {
            throw new BridgeServiceException("Error creating "+type+": " + ex.getMessage(), ex);
        }
        return hash;
    }

    // Helper method to get a single account for a given study and id, email address, or phone number.
    private Account getHibernateAccount(AccountId accountId) {
        // This is the only method where accessing null values is not an error, since we're searching for 
        // the value that was provided. There will be one.
        HibernateAccount hibernateAccount = null;
        
        AccountId unguarded = accountId.getUnguardedAccountId();
        if (unguarded.getId() != null) {
            return hibernateHelper.getById(HibernateAccount.class, unguarded.getId());
        }
        
        QueryBuilder builder = makeQuery(FULL_QUERY, unguarded.getStudyId(), accountId, null, false);

        List<HibernateAccount> accountList = hibernateHelper.queryGet(
                builder.getQuery(), builder.getParameters(), null, null, HibernateAccount.class);
        if (accountList.isEmpty()) {
            return null;
        }
        hibernateAccount = accountList.get(0);
        if (accountList.size() > 1) {
            LOG.warn("Multiple accounts found email/phone query; example accountId=" + hibernateAccount.getId());
        }
        return hibernateAccount;
    }
    
    QueryBuilder makeQuery(String prefix, String studyId, AccountId accountId, AccountSummarySearch search, boolean isCount) {
        RequestContext context = BridgeUtils.getRequestContext();
        
        QueryBuilder builder = new QueryBuilder();
        builder.append(prefix);
        builder.append("LEFT JOIN acct.accountSubstudies AS acctSubstudy");
        builder.append("WITH acct.id = acctSubstudy.accountId");
        builder.append("WHERE acct.studyId = :studyId", "studyId", studyId);

        if (accountId != null) {
            AccountId unguarded = accountId.getUnguardedAccountId();
            if (unguarded.getEmail() != null) {
                builder.append("AND acct.email=:email", "email", unguarded.getEmail());
            } else if (unguarded.getHealthCode() != null) {
                builder.append("AND acct.healthCode=:healthCode","healthCode", unguarded.getHealthCode());
            } else if (unguarded.getPhone() != null) {
                builder.append("AND acct.phone.number=:number AND acct.phone.regionCode=:regionCode",
                        "number", unguarded.getPhone().getNumber(),
                        "regionCode", unguarded.getPhone().getRegionCode());
            } else {
                builder.append("AND (acctSubstudy.externalId=:externalId OR acct.externalId=:externalId)", "externalId", unguarded.getExternalId());
            }
        }
        if (search != null) {
            // Note: emailFilter can be any substring, not just prefix/suffix. Same with phone.
            if (StringUtils.isNotBlank(search.getEmailFilter())) {
                builder.append("AND acct.email LIKE :email", "email", "%"+search.getEmailFilter()+"%");
            }
            if (StringUtils.isNotBlank(search.getPhoneFilter())) {
                String phoneString = search.getPhoneFilter().replaceAll("\\D*", "");
                builder.append("AND acct.phone.number LIKE :number", "number", "%"+phoneString+"%");
            }
            // Note: start- and endTime are inclusive.            
            if (search.getStartTime() != null) {
                builder.append("AND acct.createdOn >= :startTime", "startTime", search.getStartTime());
            }
            if (search.getEndTime() != null) {
                builder.append("AND acct.createdOn <= :endTime", "endTime", search.getEndTime());
            }
            if (search.getLanguage() != null) {
                builder.append("AND :language IN ELEMENTS(acct.languages)", "language", search.getLanguage());
            }
            builder.dataGroups(search.getAllOfGroups(), "IN");
            builder.dataGroups(search.getNoneOfGroups(), "NOT IN");
        }
        Set<String> callerSubstudies = context.getCallerSubstudies();
        if (!callerSubstudies.isEmpty()) {
            builder.append("AND acctSubstudy.substudyId IN (:substudies)", "substudies", callerSubstudies);
        }
        if (!isCount) {
            builder.append("GROUP BY acct.id");        
        }
        return builder;
    }


    /** {@inheritDoc} */
    @Override
    public void deleteAccount(AccountId accountId) {
        Account hibernateAccount = getHibernateAccount(accountId);
        if (hibernateAccount != null) {
            String userId = hibernateAccount.getId();
            hibernateHelper.deleteById(HibernateAccount.class, userId);
        }
    }

    /** {@inheritDoc} */
    @Override
    public PagedResourceList<AccountSummary> getPagedAccountSummaries(Study study, AccountSummarySearch search) {
        QueryBuilder builder = makeQuery(SUMMARY_QUERY, study.getIdentifier(), null, search, false);

        // Get page of accounts.
        List<HibernateAccount> hibernateAccountList = hibernateHelper.queryGet(builder.getQuery(), builder.getParameters(),
                search.getOffsetBy(), search.getPageSize(), HibernateAccount.class);
        List<AccountSummary> accountSummaryList = hibernateAccountList.stream()
                .map(this::unmarshallAccountSummary).collect(Collectors.toList());

        // Get count of accounts.
        builder = makeQuery(COUNT_QUERY, study.getIdentifier(), null, search, true);
        int count = hibernateHelper.queryCount(builder.getQuery(), builder.getParameters());
        
        // Package results and return.
        return new PagedResourceList<>(accountSummaryList, count)
                .withRequestParam(ResourceList.OFFSET_BY, search.getOffsetBy())
                .withRequestParam(ResourceList.PAGE_SIZE, search.getPageSize())
                .withRequestParam(ResourceList.EMAIL_FILTER, search.getEmailFilter())
                .withRequestParam(ResourceList.PHONE_FILTER, search.getPhoneFilter())
                .withRequestParam(ResourceList.START_TIME, search.getStartTime())
                .withRequestParam(ResourceList.END_TIME, search.getEndTime())
                .withRequestParam(ResourceList.LANGUAGE, search.getLanguage())
                .withRequestParam(ResourceList.ALL_OF_GROUPS, search.getAllOfGroups())
                .withRequestParam(ResourceList.NONE_OF_GROUPS, search.getNoneOfGroups());
    }
    
    // Callers of AccountDao assume that an Account will always a health code and health ID. All accounts created
    // through the DAO will automatically have health code and ID populated, but accounts created in the DB directly
    // are left in a bad state. This method validates the health code mapping on a HibernateAccount and updates it as
    // is necessary.
    private boolean validateHealthCode(Account hibernateAccount) {
        if (StringUtils.isBlank(hibernateAccount.getHealthCode())) {
            hibernateAccount.setHealthCode(generateGUID());

            // We modified it. Update modifiedOn.
            DateTime modifiedOn = DateUtils.getCurrentDateTime();
            hibernateAccount.setModifiedOn(modifiedOn);
            return true;
        }
        return false;
    }

    // Helper method to unmarshall a HibernateAccount into an AccountSummary.
    // Package-scoped to facilitate unit tests.
    AccountSummary unmarshallAccountSummary(HibernateAccount hibernateAccount) {
        StudyIdentifier studyId = null;
        if (StringUtils.isNotBlank(hibernateAccount.getStudyId())) {
            studyId = new StudyIdentifierImpl(hibernateAccount.getStudyId());
        }
        // Hibernate will not load the collection of substudies once you use the constructor form of HQL 
        // to limit the data you retrieve from a table. May need to manually construct the objects to 
        // avoid this 1+N query.
        SubstudyAssociations assoc = null;
        if (hibernateAccount.getId() != null) {
            List<HibernateAccountSubstudy> accountSubstudies = hibernateHelper.queryGet(
                    "FROM HibernateAccountSubstudy WHERE accountId=:accountId",
                    ImmutableMap.of("accountId", hibernateAccount.getId()), null, null, HibernateAccountSubstudy.class);
            
            assoc = BridgeUtils.substudyAssociationsVisibleToCaller(accountSubstudies);
        } else {
            assoc = BridgeUtils.substudyAssociationsVisibleToCaller(null);
        }
        
        return new AccountSummary(hibernateAccount.getFirstName(), hibernateAccount.getLastName(),
                hibernateAccount.getEmail(), hibernateAccount.getPhone(), hibernateAccount.getExternalId(),
                assoc.getExternalIdsVisibleToCaller(), hibernateAccount.getId(), hibernateAccount.getCreatedOn(),
                hibernateAccount.getStatus(), studyId, assoc.getSubstudyIdsVisibleToCaller());
    }
}
