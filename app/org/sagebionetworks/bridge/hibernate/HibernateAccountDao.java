package org.sagebionetworks.bridge.hibernate;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.services.AuthenticationService.ChannelType.EMAIL;
import static org.sagebionetworks.bridge.services.AuthenticationService.ChannelType.PHONE;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;

/** Hibernate implementation of Account Dao. */
@Component
public class HibernateAccountDao implements AccountDao {
    
    private static final Logger LOG = LoggerFactory.getLogger(HibernateAccountDao.class);

    static final String ACCOUNT_SUMMARY_QUERY_PREFIX = "select new " + HibernateAccount.class.getCanonicalName() +
            "(createdOn, studyId, firstName, lastName, email, phone, externalId, id, status) ";
    static final String EMAIL_QUERY = "from HibernateAccount where studyId=:studyId and email=:email";
    static final String HEALTH_CODE_QUERY = "from HibernateAccount where studyId=:studyId and healthCode=:healthCode";
    static final String PHONE_QUERY = "from HibernateAccount where studyId=:studyId and phone.number=:number and phone.regionCode=:regionCode";
    static final String EXTID_QUERY = "from HibernateAccount where studyId=:studyId and externalId=:externalId";
    
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
            hibernateHelper.update(hibernateAccount);    
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
        hibernateHelper.update(hibernateAccount);
    }

    /** {@inheritDoc} */
    @Override
    public Account authenticate(Study study, SignIn signIn) {
        HibernateAccount hibernateAccount = fetchHibernateAccount(signIn);
        return authenticateInternal(study, hibernateAccount, hibernateAccount.getPasswordAlgorithm(),
                hibernateAccount.getPasswordHash(), signIn.getPassword(), "password", signIn);
    }

    /** {@inheritDoc} */
    @Override
    public Account reauthenticate(Study study, SignIn signIn) {
        if (!study.isReauthenticationEnabled()) {
            throw new UnauthorizedException("Reauthentication is not enabled for study: " + study.getName());    
        }
        HibernateAccount hibernateAccount = fetchHibernateAccount(signIn);
        return authenticateInternal(study, hibernateAccount, hibernateAccount.getReauthTokenAlgorithm(),
                hibernateAccount.getReauthTokenHash(), signIn.getReauthToken(), "reauth token", signIn);
    }
    
    private Account authenticateInternal(Study study, HibernateAccount hibernateAccount, PasswordAlgorithm algorithm,
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
            HibernateAccount updated = hibernateHelper.update(hibernateAccount);
            hibernateAccount.setVersion(updated.getVersion());
        }
        return hibernateAccount;
    }

    @Override
    public Account getAccountAfterAuthentication(AccountId accountId) {
        HibernateAccount hibernateAccount = getHibernateAccount(accountId);

        if (hibernateAccount != null) {
            boolean accountUpdated = validateHealthCode(hibernateAccount);
            accountUpdated = updateReauthToken(null, hibernateAccount) || accountUpdated;
            if (accountUpdated) {
                HibernateAccount updated = hibernateHelper.update(hibernateAccount);
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
        HibernateAccount hibernateAccount = getHibernateAccount(accountId);
        if (hibernateAccount != null && hibernateAccount.getReauthTokenHash() != null) {
            hibernateAccount.setReauthTokenHash(null);
            hibernateAccount.setReauthTokenAlgorithm(null);
            hibernateAccount.setReauthTokenModifiedOn(null);
            hibernateHelper.update(hibernateAccount);
        }
    }
    
    private boolean updateReauthToken(Study study, HibernateAccount hibernateAccount) {
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
    public void createAccount(Study study, Account account) {
        account.setStudyId(study.getIdentifier());
        DateTime timestamp = DateUtils.getCurrentDateTime();
        account.setCreatedOn(timestamp);
        account.setModifiedOn(timestamp);
        account.setPasswordModifiedOn(timestamp);
        account.setMigrationVersion(AccountDao.MIGRATION_VERSION);

        // Create account. We don't verify substudies because this is handled
        // by validation
        hibernateHelper.create(account);
    }

    /** {@inheritDoc} */
    @Override
    public void updateAccount(Account account) {
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

        // Update. We don't verify substudies because this is handled
        // by validation
        hibernateHelper.update(account);            
    }
    
    /** {@inheritDoc} */
    @Override
    public void editAccount(StudyIdentifier studyId, String healthCode, Consumer<Account> accountEdits) {
        AccountId accountId = AccountId.forHealthCode(studyId.getIdentifier(), healthCode);
        Account account = getAccount(accountId);
        
        if (account != null) {
            accountEdits.accept(account);
            updateAccount(account);
        }
    }

    /** {@inheritDoc} */
    @Override
    public Account getAccount(AccountId accountId) {
        HibernateAccount hibernateAccount = getHibernateAccount(accountId);
        if (hibernateAccount != null) {
            boolean accountUpdated = validateHealthCode(hibernateAccount);
            if (accountUpdated) {
                HibernateAccount updated = hibernateHelper.update(hibernateAccount);
                hibernateAccount.setVersion(updated.getVersion());
            }
            return hibernateAccount;
        } else {
            // In keeping with the email implementation, just return null
            return null;
        }
    }

    private HibernateAccount fetchHibernateAccount(SignIn signIn) {
        // Fetch account
        HibernateAccount hibernateAccount = getHibernateAccount(signIn.getAccountId());
        if (hibernateAccount == null) {
            throw new EntityNotFoundException(Account.class);
        }
        return hibernateAccount;
    }
    
    private void verifyCredential(String accountId, String type, PasswordAlgorithm algorithm, String hash,
            String credentialValue) {
        // Verify credential (password or reauth token)
        if (algorithm == null || StringUtils.isBlank(hash)) {
            LOG.error("Account " + accountId + " is enabled but has no "+type+".");
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
    private HibernateAccount getHibernateAccount(AccountId accountId) {
        // This is the only method where accessing null values is not an error, since we're searching for 
        // the value that was provided. There will be one.
        HibernateAccount hibernateAccount = null;
        
        AccountId unguarded = accountId.getUnguardedAccountId();
        if (unguarded.getId() != null) {
            hibernateAccount = hibernateHelper.getById(HibernateAccount.class, unguarded.getId());
            if (hibernateAccount == null) {
                return null;
            }
        } else {
            String query = null;
            Map<String,Object> parameters = new HashMap<>();
            if (unguarded.getEmail() != null) {
                query = EMAIL_QUERY;
                parameters.put("studyId", unguarded.getStudyId());
                parameters.put("email", unguarded.getEmail());
            } else if (unguarded.getHealthCode() != null) {
                query = HEALTH_CODE_QUERY;
                parameters.put("studyId", unguarded.getStudyId());
                parameters.put("healthCode", unguarded.getHealthCode());
            } else if (unguarded.getPhone() != null) {
                query = PHONE_QUERY;
                parameters.put("studyId", unguarded.getStudyId());
                parameters.put("number", unguarded.getPhone().getNumber());
                parameters.put("regionCode", unguarded.getPhone().getRegionCode());
            } else {
                query = EXTID_QUERY;
                parameters.put("studyId", unguarded.getStudyId());
                parameters.put("externalId", unguarded.getExternalId());
            }
            List<HibernateAccount> accountList = hibernateHelper.queryGet(query, parameters, null, null, HibernateAccount.class);
            if (accountList.isEmpty()) {
                return null;
            }
            hibernateAccount = accountList.get(0);
            if (accountList.size() > 1) {
                LOG.warn("Multiple accounts found email/phone query; example accountId=" + hibernateAccount.getId());
            }
        }
        return BridgeUtils.filterForSubstudy(hibernateAccount);
    }

    /** {@inheritDoc} */
    @Override
    public void deleteAccount(AccountId accountId) {
        HibernateAccount hibernateAccount = getHibernateAccount(accountId);
        if (hibernateAccount != null) {
            String userId = hibernateAccount.getId();
            hibernateHelper.deleteById(HibernateAccount.class, userId);
        }
    }

    /** {@inheritDoc} */
    @Override
    public PagedResourceList<AccountSummary> getPagedAccountSummaries(Study study, AccountSummarySearch search) {
        /*
        // Note: emailFilter can be any substring, not just prefix/suffix. Same with phone.
        // Note: start- and endTime are inclusive.
        Map<String,Object> parameters = new HashMap<>();
        String query = assembleSearchQuery(study.getIdentifier(), search, parameters);
        
        // Don't retrieve unused columns, clientData can be large
        String getQuery = ACCOUNT_SUMMARY_QUERY_PREFIX + query;

        // Get page of accounts.
        List<HibernateAccount> hibernateAccountList = hibernateHelper.queryGet(getQuery, parameters,
                search.getOffsetBy(), search.getPageSize(), HibernateAccount.class);
        List<AccountSummary> accountSummaryList = hibernateAccountList.stream()
                .map(HibernateAccountDao::unmarshallAccountSummary).collect(Collectors.toList());

        // Get count of accounts.
        int count = hibernateHelper.queryCount(query, parameters);
*/
        
        String getQuery = "select new HibernateAccount acct JOIN HibernateAccountSubstudy acctSubstudy "+
                "WHERE acct.id = acctSubstudy.accountId AND acctSubstudy.substudyId in :substudies";
        Map<String,Object> parameters = new HashMap<>();
        parameters.put("substudies", ImmutableSet.of("orgA"));

        List<HibernateAccount> hibernateAccountList = hibernateHelper.queryGet(getQuery, parameters,
                search.getOffsetBy(), search.getPageSize(), HibernateAccount.class);
        List<AccountSummary> accountSummaryList = hibernateAccountList.stream()
                .map(HibernateAccountDao::unmarshallAccountSummary).collect(Collectors.toList());
        
        // Package results and return.
        return new PagedResourceList<>(accountSummaryList, /*count*/1)
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
    
    protected String assembleSearchQuery(String studyId, AccountSummarySearch search, Map<String,Object> parameters) {
        StringBuilder queryBuilder = new StringBuilder();

        queryBuilder.append("from HibernateAccount as acct where studyId=:studyId");
        parameters.put("studyId", studyId);

        if (StringUtils.isNotBlank(search.getEmailFilter())) {
            queryBuilder.append(" and email like :email");
            parameters.put("email", "%"+search.getEmailFilter()+"%");
        }
        if (StringUtils.isNotBlank(search.getPhoneFilter())) {
            String phoneString = search.getPhoneFilter().replaceAll("\\D*", "");
            queryBuilder.append(" and phone.number like :number");
            parameters.put("number", "%"+phoneString+"%");
        }
        if (search.getStartTime() != null) {
            queryBuilder.append(" and createdOn >= :startTime");
            parameters.put("startTime", search.getStartTime());
        }
        if (search.getEndTime() != null) {
            queryBuilder.append(" and createdOn <= :endTime");
            parameters.put("endTime", search.getEndTime());
        }
        if (search.getLanguage() != null) {
            queryBuilder.append(" and :language in elements(acct.languages)");
            parameters.put("language", search.getLanguage());
        }
        dataGroupQuerySegment(queryBuilder, search.getAllOfGroups(), "in", parameters);
        dataGroupQuerySegment(queryBuilder, search.getNoneOfGroups(), "not in", parameters);
        
        return queryBuilder.toString();        
    }

    private void dataGroupQuerySegment(StringBuilder queryBuilder, Set<String> dataGroups, String operator,
            Map<String, Object> parameters) {
        if (dataGroups != null && !dataGroups.isEmpty()) {
            int i = 0;
            Set<String> clauses = new HashSet<>();
            for (String oneDataGroup : dataGroups) {
                String varName = operator.replace(" ", "") + (++i);
                clauses.add(":"+varName+" "+operator+" elements(acct.dataGroups)");
                parameters.put(varName, oneDataGroup);
            }
            queryBuilder.append(" and (").append(Joiner.on(" and ").join(clauses)).append(")");
        }
    }
    
    // Callers of AccountDao assume that an Account will always a health code and health ID. All accounts created
    // through the DAO will automatically have health code and ID populated, but accounts created in the DB directly
    // are left in a bad state. This method validates the health code mapping on a HibernateAccount and updates it as
    // is necessary.
    private boolean validateHealthCode(HibernateAccount hibernateAccount) {
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
    static AccountSummary unmarshallAccountSummary(HibernateAccount hibernateAccount) {
        // Some attrs need parsing.
        DateTime createdOn = null;
        if (hibernateAccount.getCreatedOn() != null) {
            createdOn = new DateTime(hibernateAccount.getCreatedOn());
        }

        StudyIdentifier studyId = null;
        if (StringUtils.isNotBlank(hibernateAccount.getStudyId())) {
            studyId = new StudyIdentifierImpl(hibernateAccount.getStudyId());
        }
        
        // Unmarshall single account
        return new AccountSummary(hibernateAccount.getFirstName(), hibernateAccount.getLastName(),
                hibernateAccount.getEmail(), hibernateAccount.getPhone(), hibernateAccount.getExternalId(),
                hibernateAccount.getId(), createdOn, hibernateAccount.getStatus(), studyId);
    }
}
