package org.sagebionetworks.bridge.hibernate;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.services.AuthenticationService.ChannelType.EMAIL;
import static org.sagebionetworks.bridge.services.AuthenticationService.ChannelType.PHONE;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.persistence.OptimisticLockException;
import javax.persistence.PersistenceException;

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
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.ConstraintViolationException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.time.DateUtils;
import org.sagebionetworks.bridge.models.AccountSummarySearch;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.AccountStatus;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.GenericAccount;
import org.sagebionetworks.bridge.models.accounts.HealthId;
import org.sagebionetworks.bridge.models.accounts.PasswordAlgorithm;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.AuthenticationService;
import org.sagebionetworks.bridge.services.HealthCodeService;
import org.sagebionetworks.bridge.services.AuthenticationService.ChannelType;

import com.amazonaws.util.Throwables;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

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
    
    private HealthCodeService healthCodeService;
    private HibernateHelper hibernateHelper;
    private CacheProvider cacheProvider;

    /** Health code service, because this DAO is expected to generate health codes for new accounts. */
    @Autowired
    public final void setHealthCodeService(HealthCodeService healthCodeService) {
        this.healthCodeService = healthCodeService;
    }

    /** This makes interfacing with Hibernate easier. */
    @Autowired
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
            hibernateAccount.setModifiedOn(DateUtils.getCurrentMillisFromEpoch());
            try {
                hibernateHelper.update(hibernateAccount);    
            } catch(PersistenceException pe) {
                throw convertPersistenceException(pe, hibernateAccount.getId());
            }
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
        long modifiedOn = DateUtils.getCurrentMillisFromEpoch();
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
        try {
            hibernateHelper.update(hibernateAccount);
        } catch(PersistenceException pe) {
            throw convertPersistenceException(pe, account.getId());
        }
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
        Account account = unmarshallAccount(hibernateAccount);
        accountUpdated = updateReauthToken(study, hibernateAccount, account) || accountUpdated;
        if (accountUpdated) {
            try {
                HibernateAccount updated = hibernateHelper.update(hibernateAccount);
                account.setVersion(updated.getVersion());
            } catch(PersistenceException pe) {
                throw convertPersistenceException(pe, hibernateAccount.getId());
            }
        }
        return account;
    }

    @Override
    public Account getAccountAfterAuthentication(AccountId accountId) {
        HibernateAccount hibernateAccount = getHibernateAccount(accountId);

        if (hibernateAccount != null) {
            boolean accountUpdated = validateHealthCode(hibernateAccount);
            Account account = unmarshallAccount(hibernateAccount);
            accountUpdated = updateReauthToken(null, hibernateAccount, account) || accountUpdated;
            if (accountUpdated) {
                try {
                    HibernateAccount updated = hibernateHelper.update(hibernateAccount);
                    account.setVersion(updated.getVersion());
                } catch(PersistenceException pe) {
                    throw convertPersistenceException(pe, hibernateAccount.getId());
                }
            }
            return account;
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
            try { 
                hibernateHelper.update(hibernateAccount);
            } catch(PersistenceException pe) {
                throw convertPersistenceException(pe, hibernateAccount.getId());
            }
        }
    }
    
    private boolean updateReauthToken(Study study, HibernateAccount hibernateAccount, Account account) {
        if (study != null && !study.isReauthenticationEnabled()) {
            account.setReauthToken(null);
            return false;
        }
        CacheKey reauthTokenKey = CacheKey.reauthTokenLookupKey(account.getId(), account.getStudyIdentifier());
        
        // We cache the reauthentication token for 15 seconds so that concurrent sign in 
        // requests don't throw 409 concurrent modification exceptions as an optimistic lock
        // prevents (correctly) updating the reauth token that is issued over and over.
        String cachedReauthToken = cacheProvider.getObject(reauthTokenKey, String.class);
        if (cachedReauthToken != null) {
            account.setReauthToken(cachedReauthToken);
            return false;
        }
        String reauthToken = SecureTokenGenerator.INSTANCE.nextToken();
        cacheProvider.setObject(reauthTokenKey, reauthToken, BridgeConstants.REAUTH_TOKEN_CACHE_LOOKUP_IN_SECONDS);

        // Re-create and persist the authentication token.
        PasswordAlgorithm passwordAlgorithm = PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM;
        String reauthTokenHash = hashCredential(passwordAlgorithm, "reauth token", reauthToken);
        hibernateAccount.setReauthTokenHash(reauthTokenHash);
        hibernateAccount.setReauthTokenAlgorithm(passwordAlgorithm);
        hibernateAccount.setReauthTokenModifiedOn(DateUtils.getCurrentMillisFromEpoch());
        // We must get the current version to return from the DAO, or subsequent updates to the 
        // account, even in the same call, will fail (e.g. to update languages captured from a request).
        account.setReauthToken(reauthToken);
        return true;
    }
    
    /** {@inheritDoc} */
    @Override
    public Account constructAccount(Study study, String email, Phone phone, String externalId, String password) {
        HealthId healthId = healthCodeService.createMapping(study.getStudyIdentifier());
        // Set basic params from inputs.
        GenericAccount account = new GenericAccount();
        account.setStudyId(study.getStudyIdentifier());
        account.setEmail(email);
        account.setPhone(phone);
        account.setEmailVerified(Boolean.FALSE);
        account.setPhoneVerified(Boolean.FALSE);
        account.setHealthId(healthId);
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

    /** {@inheritDoc} */
    @Override
    public String createAccount(Study study, Account account) {
        String userId = BridgeUtils.generateGuid();
        HibernateAccount hibernateAccount = marshallAccount(account);
        hibernateAccount.setId(userId);
        hibernateAccount.setStudyId(study.getIdentifier());
        hibernateAccount.setCreatedOn(DateUtils.getCurrentMillisFromEpoch());
        hibernateAccount.setModifiedOn(DateUtils.getCurrentMillisFromEpoch());
        hibernateAccount.setPasswordModifiedOn(DateUtils.getCurrentMillisFromEpoch());
        hibernateAccount.setMigrationVersion(AccountDao.MIGRATION_VERSION);

        // Create account
        try {
            hibernateHelper.create(hibernateAccount);
        } catch (PersistenceException ex) {
            // Find the ID of the account creating a constraint violation
            HibernateAccount otherAccount = seekForAccount(study.getIdentifier(), account);
            throw convertPersistenceException(ex, (otherAccount == null) ? null : otherAccount.getId());
        }
        return userId;
    }
    
    /**
     * In cases where there are multiple identifiers, the first one used may not be the cause
     * of the conflict that prevented a create. Seek until you find an account with the same 
     * identifier. Classic example of this is a unique email/phone but the external ID has already 
     * been taken.
     */
    private HibernateAccount seekForAccount(String studyId, Account account) {
        HibernateAccount otherAccount = null;
        AccountId accountId = null;
        if (account.getEmail() != null) {
            accountId = AccountId.forEmail(studyId, account.getEmail());
            otherAccount = getHibernateAccount(accountId);
        }
        if (otherAccount == null && account.getPhone() != null) {
            accountId = AccountId.forPhone(studyId, account.getPhone());
            otherAccount = getHibernateAccount(accountId);
        } 
        if (otherAccount == null && account.getExternalId() != null) {
            accountId = AccountId.forExternalId(studyId, account.getExternalId());
            otherAccount = getHibernateAccount(accountId);
        }
        return otherAccount;
    }

    /** {@inheritDoc} */
    @Override
    public void updateAccount(Account account, boolean allowIdentifierUpdates) {
        String accountId = account.getId();
        HibernateAccount accountToUpdate = marshallAccount(account);

        // Can't change study, email, phone, emailVerified, phoneVerified, createdOn, or passwordModifiedOn.
        HibernateAccount persistedAccount = hibernateHelper.getById(HibernateAccount.class, accountId);
        if (persistedAccount == null) {
            throw new EntityNotFoundException(Account.class, "Account " + accountId + " not found");
        }
        // None of these values should be changeable by the user.
        accountToUpdate.setStudyId(persistedAccount.getStudyId());
        accountToUpdate.setCreatedOn(persistedAccount.getCreatedOn());
        accountToUpdate.setPasswordAlgorithm(persistedAccount.getPasswordAlgorithm());
        accountToUpdate.setPasswordHash(persistedAccount.getPasswordHash());
        accountToUpdate.setPasswordModifiedOn(persistedAccount.getPasswordModifiedOn());
        accountToUpdate.setReauthTokenAlgorithm(persistedAccount.getReauthTokenAlgorithm());
        accountToUpdate.setReauthTokenHash(persistedAccount.getReauthTokenHash());
        accountToUpdate.setReauthTokenModifiedOn(persistedAccount.getReauthTokenModifiedOn());
        if (!allowIdentifierUpdates) {
            accountToUpdate.setEmail(persistedAccount.getEmail());
            accountToUpdate.setPhone(persistedAccount.getPhone());
            accountToUpdate.setEmailVerified(persistedAccount.getEmailVerified());
            accountToUpdate.setPhoneVerified(persistedAccount.getPhoneVerified());
        }

        // Update modifiedOn.
        accountToUpdate.setModifiedOn(DateUtils.getCurrentMillisFromEpoch());

        // Update
        try {
            hibernateHelper.update(accountToUpdate);            
        } catch(PersistenceException e) {
            throw convertPersistenceException(e, persistedAccount.getId());    
        }
    }
    
    /** {@inheritDoc} */
    @Override
    public void editAccount(StudyIdentifier studyId, String healthCode, Consumer<Account> accountEdits) {
        AccountId accountId = AccountId.forHealthCode(studyId.getIdentifier(), healthCode);
        Account account = getAccount(accountId);
        if (account != null) {
            accountEdits.accept(account);
            updateAccount(account, false);
        }
    }

    /** {@inheritDoc} */
    @Override
    public Account getAccount(AccountId accountId) {
        HibernateAccount hibernateAccount = getHibernateAccount(accountId);
        if (hibernateAccount != null) {
            boolean accountUpdated = validateHealthCode(hibernateAccount);
            Account account = unmarshallAccount(hibernateAccount);
            if (accountUpdated) {
                try {
                    HibernateAccount updated = hibernateHelper.update(hibernateAccount);
                    account.setVersion(updated.getVersion());
                } catch(PersistenceException pe) {
                    throw convertPersistenceException(pe, hibernateAccount.getId());
                }
            }
            return account;
        } else {
            // In keeping with the email implementation, just return null
            return null;
        }
    }
    
    private RuntimeException convertPersistenceException(PersistenceException exception, String userId) {
        // The sequence of type-checking and unwrapping of this exception is significant as unfortunately, 
        // the hierarchy of wrapped exceptions is very specific. 
        if (exception instanceof OptimisticLockException) {
            return new ConcurrentModificationException(
                    "Account has the wrong version number; it may have been saved in the background.");
        }
        if (exception.getCause() instanceof org.hibernate.exception.ConstraintViolationException) {
            // The specific error message is buried in the root MySQLIntegrityConstraintViolationException
            Throwable cause = Throwables.getRootCause(exception);
            String message = cause.getMessage();
            if (message != null && userId != null) {
                Map<String,Object> entityKeys = ImmutableMap.of("userId", userId);
                
                if (message.matches("Duplicate entry.*for key 'Accounts-StudyId-ExternalId-Index'")) {
                    return new EntityAlreadyExistsException(Account.class, "External ID has already been used by another account.", entityKeys);
                } else if (message.matches("Duplicate entry.*for key 'Accounts-StudyId-Email-Index'")) {
                    return new EntityAlreadyExistsException(Account.class, "Email address has already been used by another account.", entityKeys);
                } else if (message.matches("Duplicate entry.*for key 'Accounts-StudyId-Phone-Index'")) {
                    return new EntityAlreadyExistsException(Account.class, "Phone number has already been used by another account.", entityKeys);
                }
            }
            ConstraintViolationException.Builder cveBuilder = new ConstraintViolationException.Builder();
            if (message != null) {
                cveBuilder.withMessage(cause.getMessage());
            }
            return cveBuilder.build();
        }
        return new BridgeServiceException(exception);
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
        return hibernateAccount;
    }

    /** {@inheritDoc} */
    @Override
    public void deleteAccount(AccountId accountId) {
        String userId = accountId.getUnguardedAccountId().getId();
        if (userId == null) {
            HibernateAccount hibernateAccount = getHibernateAccount(accountId);
            userId = hibernateAccount.getId();
        }
        hibernateHelper.deleteById(HibernateAccount.class, userId);    
    }

    /** {@inheritDoc} */
    @Override
    public Iterator<AccountSummary> getAllAccounts() {
        List<HibernateAccount> hibernateAccountList = hibernateHelper.queryGet("from HibernateAccount", null, null,
                null, HibernateAccount.class);
        return hibernateAccountList.stream().map(HibernateAccountDao::unmarshallAccountSummary).iterator();
    }

    /** {@inheritDoc} */
    @Override
    public Iterator<AccountSummary> getStudyAccounts(Study study) {
        Map<String,Object> parameters = new HashMap<>();
        parameters.put("studyId", study.getIdentifier());
        List<HibernateAccount> hibernateAccountList = hibernateHelper.queryGet(
                "from HibernateAccount where studyId=:studyId", parameters, null, null, HibernateAccount.class);
        return hibernateAccountList.stream().map(HibernateAccountDao::unmarshallAccountSummary).iterator();
    }

    /** {@inheritDoc} */
    @Override
    public PagedResourceList<AccountSummary> getPagedAccountSummaries(Study study, AccountSummarySearch search) {
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
            parameters.put("startTime", search.getStartTime().getMillis());
        }
        if (search.getEndTime() != null) {
            queryBuilder.append(" and createdOn <= :endTime");
            parameters.put("endTime", search.getEndTime().getMillis());
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
    
    // Helper method which marshalls a GenericAccount into a HibernateAccount.
    // Package-scoped to facilitate unit tests.
    static HibernateAccount marshallAccount(Account account) {
        // Currently does not work with StormpathAccount. This is because StormpathAccount doesn't support certain
        // behaviors we need to make this work.
        if (!(account instanceof GenericAccount)) {
            throw new BridgeServiceException("Hibernate can't marshall a StormpathAccount");
        }
        GenericAccount genericAccount = (GenericAccount) account;

        // Simple attributes
        HibernateAccount hibernateAccount = new HibernateAccount();
        hibernateAccount.setId(genericAccount.getId());
        hibernateAccount.setEmail(genericAccount.getEmail());
        hibernateAccount.setPhone(genericAccount.getPhone());
        hibernateAccount.setEmailVerified(genericAccount.getEmailVerified());
        hibernateAccount.setPhoneVerified(genericAccount.getPhoneVerified());
        hibernateAccount.setHealthCode(genericAccount.getHealthCode());
        hibernateAccount.setHealthId(genericAccount.getHealthId());
        hibernateAccount.setFirstName(genericAccount.getFirstName());
        hibernateAccount.setLastName(genericAccount.getLastName());
        hibernateAccount.setRoles(genericAccount.getRoles());
        hibernateAccount.setStatus(genericAccount.getStatus());
        hibernateAccount.setVersion(genericAccount.getVersion());
        hibernateAccount.setPasswordAlgorithm(genericAccount.getPasswordAlgorithm());
        hibernateAccount.setPasswordHash(genericAccount.getPasswordHash());
        hibernateAccount.setPasswordModifiedOn(genericAccount.getPasswordModifiedOn());
        hibernateAccount.setReauthTokenAlgorithm(genericAccount.getReauthTokenAlgorithm());
        hibernateAccount.setReauthTokenHash(genericAccount.getReauthTokenHash());
        hibernateAccount.setReauthTokenModifiedOn(genericAccount.getReauthTokenModifiedOn());
        hibernateAccount.setTimeZone(DateUtils.timeZoneToOffsetString(genericAccount.getTimeZone()));
        hibernateAccount.setSharingScope(genericAccount.getSharingScope());
        hibernateAccount.setNotifyByEmail(genericAccount.getNotifyByEmail());
        hibernateAccount.setExternalId(genericAccount.getExternalId());
        hibernateAccount.setDataGroups(genericAccount.getDataGroups());
        hibernateAccount.setLanguages(Lists.newArrayList(genericAccount.getLanguages()));
        hibernateAccount.setMigrationVersion(genericAccount.getMigrationVersion());

        if (genericAccount.getClientData() != null) {
            hibernateAccount.setClientData(genericAccount.getClientData().toString());
        } else {
            hibernateAccount.setClientData(null);
        }

        // Attributes that need parsing.
        if (genericAccount.getStudyIdentifier() != null) {
            hibernateAccount.setStudyId(genericAccount.getStudyIdentifier().getIdentifier());
        }
        if (genericAccount.getCreatedOn() != null) {
            hibernateAccount.setCreatedOn(genericAccount.getCreatedOn().getMillis());
        }

        // Attribute map
        Map<String, String> hibernateAttrMap = hibernateAccount.getAttributes();
        for (String oneAttrName : genericAccount.getAttributeNameSet()) {
            hibernateAttrMap.put(oneAttrName, genericAccount.getAttribute(oneAttrName));
        }

        // Consents
        Map<HibernateAccountConsentKey, HibernateAccountConsent> hibernateConsentMap = hibernateAccount.getConsents();
        for (Map.Entry<SubpopulationGuid, List<ConsentSignature>> consentListForSubpop :
                genericAccount.getAllConsentSignatureHistories().entrySet()) {
            String subpopGuidString = consentListForSubpop.getKey().getGuid();

            for (ConsentSignature oneConsent : consentListForSubpop.getValue()) {
                // Consent key
                HibernateAccountConsentKey hibernateConsentKey = new HibernateAccountConsentKey(subpopGuidString,
                        oneConsent.getSignedOn());

                // Simple consent attributes.
                HibernateAccountConsent hibernateConsentValue = new HibernateAccountConsent();
                hibernateConsentValue.setConsentCreatedOn(oneConsent.getConsentCreatedOn());
                hibernateConsentValue.setName(oneConsent.getName());
                hibernateConsentValue.setSignatureImageData(oneConsent.getImageData());
                hibernateConsentValue.setSignatureImageMimeType(oneConsent.getImageMimeType());
                hibernateConsentValue.setWithdrewOn(oneConsent.getWithdrewOn());

                // We need to parse birthdate.
                if (StringUtils.isNotBlank(oneConsent.getBirthdate())) {
                    hibernateConsentValue.setBirthdate(oneConsent.getBirthdate());
                }

                // Store in hibernate account.
                hibernateConsentMap.put(hibernateConsentKey, hibernateConsentValue);
            }
        }

        return hibernateAccount;
    }
    
    // Callers of AccountDao assume that an Account will always a health code and health ID. All accounts created
    // through the DAO will automatically have health code and ID populated, but accounts created in the DB directly
    // are left in a bad state. This method validates the health code mapping on a HibernateAccount and updates it as
    // is necessary.
    private boolean validateHealthCode(HibernateAccount hibernateAccount) {
        if (StringUtils.isBlank(hibernateAccount.getHealthCode()) ||
                StringUtils.isBlank(hibernateAccount.getHealthId())) {
            // Generate health code mapping.
            StudyIdentifier studyId = new StudyIdentifierImpl(hibernateAccount.getStudyId());
            HealthId healthId = healthCodeService.createMapping(studyId);
            hibernateAccount.setHealthCode(healthId.getCode());
            hibernateAccount.setHealthId(healthId.getId());

            // We modified it. Update modifiedOn.
            long modifiedOn = DateUtils.getCurrentMillisFromEpoch();
            hibernateAccount.setModifiedOn(modifiedOn);
            return true;
        }
        return false;
    }

    // Helper method which unmarshall a HibernateAccount into a GenericAccount.
    // Package-scoped to facilitate unit tests.
    static Account unmarshallAccount(HibernateAccount hibernateAccount) {
        // Simple attributes
        GenericAccount account = new GenericAccount();
        account.setId(hibernateAccount.getId());
        account.setEmail(hibernateAccount.getEmail());
        account.setPhone(hibernateAccount.getPhone());
        account.setEmailVerified(hibernateAccount.getEmailVerified());
        account.setPhoneVerified(hibernateAccount.getPhoneVerified());
        account.setFirstName(hibernateAccount.getFirstName());
        account.setLastName(hibernateAccount.getLastName());
        account.setPasswordAlgorithm(hibernateAccount.getPasswordAlgorithm());
        account.setPasswordHash(hibernateAccount.getPasswordHash());
        account.setPasswordModifiedOn(hibernateAccount.getPasswordModifiedOn());
        account.setReauthTokenAlgorithm(hibernateAccount.getReauthTokenAlgorithm());
        account.setReauthTokenHash(hibernateAccount.getReauthTokenHash());
        account.setReauthTokenModifiedOn(hibernateAccount.getReauthTokenModifiedOn());
        account.setHealthCode(hibernateAccount.getHealthCode());
        account.setHealthId(hibernateAccount.getHealthId());
        account.setStatus(hibernateAccount.getStatus());
        account.setRoles(hibernateAccount.getRoles());
        account.setVersion(hibernateAccount.getVersion());
        account.setTimeZone(DateUtils.parseZoneFromOffsetString(hibernateAccount.getTimeZone()));
        account.setSharingScope(hibernateAccount.getSharingScope());
        account.setNotifyByEmail(hibernateAccount.getNotifyByEmail());
        account.setExternalId(hibernateAccount.getExternalId());
        account.setDataGroups(hibernateAccount.getDataGroups());
        account.setLanguages(Sets.newLinkedHashSet(hibernateAccount.getLanguages()));
        account.setMigrationVersion(hibernateAccount.getMigrationVersion());
        
        // sharing scope defaults to no sharing
        if (account.getSharingScope() == null) {
            account.setSharingScope(SharingScope.NO_SHARING);
        }
        // email notifications are opt-out
        if (account.getNotifyByEmail() == null) {
            account.setNotifyByEmail(true);
        }
        
        // For accounts prior to the introduction of the email/phone verification flags, where 
        // the flag was not set on creation or verification of the email address, return the right value.
        if (account.getEmailVerified() == null) {
            if (account.getStatus() == AccountStatus.ENABLED) {
                account.setEmailVerified(Boolean.TRUE);
            } else if (account.getStatus() == AccountStatus.UNVERIFIED) {
                account.setEmailVerified(Boolean.FALSE);
            }
        }
        
        if (hibernateAccount.getClientData() != null) {
            try {
                JsonNode clientData = BridgeObjectMapper.get().readTree(hibernateAccount.getClientData());
                account.setClientData(clientData);
            } catch (IOException e) {
                throw new BridgeServiceException(e);
            }
        }

        // attributes that need parsing
        if (StringUtils.isNotBlank(hibernateAccount.getStudyId())) {
            account.setStudyId(new StudyIdentifierImpl(hibernateAccount.getStudyId()));
        }
        if (hibernateAccount.getCreatedOn() != null) {
            account.setCreatedOn(new DateTime(hibernateAccount.getCreatedOn()));
        }

        // Attributes
        for (Map.Entry<String, String> oneAttrEntry : hibernateAccount.getAttributes().entrySet()) {
            account.setAttribute(oneAttrEntry.getKey(), oneAttrEntry.getValue());
        }

        // Consents
        Map<SubpopulationGuid, List<ConsentSignature>> tempConsentsBySubpop = new HashMap<>();
        for (Map.Entry<HibernateAccountConsentKey, HibernateAccountConsent> oneConsent : hibernateAccount
                .getConsents().entrySet()) {
            // Consent key
            HibernateAccountConsentKey consentKey = oneConsent.getKey();
            SubpopulationGuid subpopGuid = SubpopulationGuid.create(consentKey.getSubpopulationGuid());
            long signedOn = consentKey.getSignedOn();

            // Unmarshall consent
            HibernateAccountConsent consentValue = oneConsent.getValue();
            ConsentSignature consentSignature = new ConsentSignature.Builder().withName(consentValue.getName())
                    .withBirthdate(consentValue.getBirthdate()).withImageData(consentValue.getSignatureImageData())
                    .withImageMimeType(consentValue.getSignatureImageMimeType())
                    .withConsentCreatedOn(consentValue.getConsentCreatedOn()).withSignedOn(signedOn)
                    .withWithdrewOn(consentValue.getWithdrewOn()).build();

            // Store in map.
            tempConsentsBySubpop.putIfAbsent(subpopGuid, new ArrayList<>());
            tempConsentsBySubpop.get(subpopGuid).add(consentSignature);
        }

        // Sort consents by signedOn, from oldest to newest.
        for (Map.Entry<SubpopulationGuid, List<ConsentSignature>> consentSignatureListForSubpop :
                tempConsentsBySubpop.entrySet()) {
            SubpopulationGuid subpopGuid = consentSignatureListForSubpop.getKey();
            List<ConsentSignature> consentListCopy = new ArrayList<>(consentSignatureListForSubpop.getValue());
            Collections.sort(consentListCopy, (c1, c2) -> Long.compare(c1.getSignedOn(), c2.getSignedOn()));
            account.setConsentSignatureHistory(subpopGuid, consentListCopy);
        }

        return account;
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
