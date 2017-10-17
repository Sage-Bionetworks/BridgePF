package org.sagebionetworks.bridge.hibernate;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.SecureTokenGenerator;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.exceptions.AccountDisabledException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountStatus;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.accounts.Email;
import org.sagebionetworks.bridge.models.accounts.EmailVerification;
import org.sagebionetworks.bridge.models.accounts.GenericAccount;
import org.sagebionetworks.bridge.models.accounts.HealthId;
import org.sagebionetworks.bridge.models.accounts.PasswordAlgorithm;
import org.sagebionetworks.bridge.models.accounts.PasswordReset;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.AccountWorkflowService;
import org.sagebionetworks.bridge.services.HealthCodeService;

import com.fasterxml.jackson.databind.JsonNode;

/** Hibernate implementation of Account Dao. */
@Component
public class HibernateAccountDao implements AccountDao {
    
    private static final Logger LOG = LoggerFactory.getLogger(HibernateAccountDao.class);

    static final String ACCOUNT_SUMMARY_QUERY_PREFIX = "select new " + HibernateAccount.class.getCanonicalName() +
            "(createdOn, studyId, firstName, lastName, email, id, status) ";
    
    private AccountWorkflowService accountWorkflowService;
    private HealthCodeService healthCodeService;
    private HibernateHelper hibernateHelper;

    /** Service that handles email verification, password reset, etc. */
    @Autowired
    public final void setAccountWorkflowService(AccountWorkflowService accountWorkflowService){
        this.accountWorkflowService = accountWorkflowService;
    }

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

    /** {@inheritDoc} */
    @Override
    public void verifyEmail(EmailVerification verification) {
        accountWorkflowService.verifyEmail(verification);
    }

    /** {@inheritDoc} */
    @Override
    public void resendEmailVerificationToken(StudyIdentifier studyIdentifier, Email email) {
        accountWorkflowService.resendEmailVerificationToken(studyIdentifier, email);
    }

    /** {@inheritDoc} */
    @Override
    public void requestResetPassword(Study study, Email email) {
        accountWorkflowService.requestResetPassword(study, email);
    }

    /** {@inheritDoc} */
    @Override
    public void resetPassword(PasswordReset passwordReset) {
        accountWorkflowService.resetPassword(passwordReset);
    }

    /** {@inheritDoc} */
    @Override
    public void changePassword(Account account, String newPassword) {
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
        hibernateHelper.update(hibernateAccount);
    }

    /** {@inheritDoc} */
    @Override
    public Account authenticate(Study study, SignIn signIn) {
        HibernateAccount hibernateAccount = fetchHibernateAccount(study, signIn);
        return authenticateInternal(hibernateAccount, hibernateAccount.getPasswordAlgorithm(),
                hibernateAccount.getPasswordHash(), signIn.getPassword(), "password");
    }

    /** {@inheritDoc} */
    @Override
    public Account reauthenticate(Study study, SignIn signIn) {
        HibernateAccount hibernateAccount = fetchHibernateAccount(study, signIn);
        return authenticateInternal(hibernateAccount, hibernateAccount.getReauthTokenAlgorithm(),
                hibernateAccount.getReauthTokenHash(), signIn.getReauthToken(), "reauth token");
    }
    
    private Account authenticateInternal(HibernateAccount hibernateAccount, PasswordAlgorithm algorithm,
            String hash, String credentialValue, String credentialName) {

        verifyCredential(hibernateAccount.getId(), credentialName, algorithm, hash, credentialValue);

        // Unmarshall account
        validateHealthCode(new StudyIdentifierImpl(hibernateAccount.getStudyId()), hibernateAccount, false);
        Account account = unmarshallAccount(hibernateAccount);
        updateReauthToken(hibernateAccount, account);
        return account;
    }

    @Override
    public Account getAccountAfterAuthentication(Study study, String email) {
        HibernateAccount hibernateAccount = getHibernateAccountByEmail(study, email);
        
        if (hibernateAccount != null) {
            validateHealthCode(study.getStudyIdentifier(), hibernateAccount, false);
            Account account = unmarshallAccount(hibernateAccount);
            updateReauthToken(hibernateAccount, account);
            return account;
        } else {
            // In keeping with the email implementation, just return null
            return null;
        }
    }

    @Override
    public void signOut(StudyIdentifier studyId, String email) {
        HibernateAccount hibernateAccount = getHibernateAccountByEmail(studyId, email);
        if (hibernateAccount != null) {
            hibernateAccount.setReauthTokenHash(null);
            hibernateAccount.setReauthTokenAlgorithm(null);
            hibernateAccount.setReauthTokenModifiedOn(null);
            hibernateHelper.update(hibernateAccount);
        }
    }
    
    private void updateReauthToken(HibernateAccount hibernateAccount, Account account) {
        // Re-create and persist the authentication token.
        String reauthToken = SecureTokenGenerator.INSTANCE.nextToken();
        account.setReauthToken(reauthToken);
        
        PasswordAlgorithm passwordAlgorithm = PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM;
        String reauthTokenHash = hashCredential(passwordAlgorithm, "reauth token", reauthToken);
        hibernateAccount.setReauthTokenHash(reauthTokenHash);
        hibernateAccount.setReauthTokenAlgorithm(passwordAlgorithm);
        hibernateAccount.setReauthTokenModifiedOn(DateUtils.getCurrentMillisFromEpoch());
        
        hibernateHelper.update(hibernateAccount);
    }
    
    /** {@inheritDoc} */
    @Override
    public Account constructAccount(Study study, String email, String password) {
        HealthId healthId = healthCodeService.createMapping(study.getStudyIdentifier());
        return constructAccountForMigration(study, email, password, healthId);
    }

    /**
     * Helper method that does all the work for constructAccount(), except we pass in the Health Code mapping instead
     * of creating it ourselves. This allows us to create an account in both MySQL and Stormpath with the same Health
     * Code mapping.
     */
    public Account constructAccountForMigration(Study study, String email, String password, HealthId healthId) {
        // Set basic params from inputs.
        GenericAccount account = new GenericAccount();
        account.setStudyId(study.getStudyIdentifier());
        account.setEmail(email);
        account.setHealthId(healthId);

        // Hash password.
        PasswordAlgorithm passwordAlgorithm = PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM;
        String passwordHash = hashCredential(passwordAlgorithm, "password", password);

        account.setPasswordAlgorithm(passwordAlgorithm);
        account.setPasswordHash(passwordHash);

        return account;
    }

    /** {@inheritDoc} */
    @Override
    public String createAccount(Study study, Account account, boolean sendVerifyEmail) {
        String accountId = BridgeUtils.generateGuid();
        createAccountForMigration(study, account, accountId, sendVerifyEmail);

        // send verify email
        if (sendVerifyEmail) {
            accountWorkflowService.sendEmailVerificationToken(study, accountId, account.getEmail());
        }

        return accountId;
    }

    /**
     * Helper method that does the same work as createAccount(), except account ID generation and email verification
     * happen outside of this method. This is used during migration so that Stormpath does ID generation and so we
     * don't verify email twice.
     */
    public void createAccountForMigration(Study study, Account account, String accountId, boolean sendVerifyEmail) {
        // Initial creation of account. Fill in basic initial parameters.
        HibernateAccount hibernateAccount = marshallAccount(account);
        hibernateAccount.setId(accountId);
        hibernateAccount.setStudyId(study.getIdentifier());
        hibernateAccount.setCreatedOn(DateUtils.getCurrentMillisFromEpoch());
        hibernateAccount.setModifiedOn(DateUtils.getCurrentMillisFromEpoch());
        hibernateAccount.setPasswordModifiedOn(DateUtils.getCurrentMillisFromEpoch());
        hibernateAccount.setStatus(sendVerifyEmail ? AccountStatus.UNVERIFIED : AccountStatus.ENABLED);

        // Create account
        try {
            hibernateHelper.create(hibernateAccount);
        } catch (ConcurrentModificationException ex) {
            // account exists, but we don't have the userId, load the account
            HibernateAccount otherAccount = getHibernateAccountByEmail(study, account.getEmail());
            if (otherAccount != null) {
                throw new EntityAlreadyExistsException(Account.class, "userId", otherAccount.getId());
            } else {
                throw new BridgeServiceException("Conflict creating an account, but can't find an existing " +
                        "account with the same study and email");
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void updateAccount(Account account) {
        String accountId = account.getId();
        HibernateAccount accountToUpdate = marshallAccount(account);

        // Can't change study, email, createdOn, or passwordModifiedOn.
        HibernateAccount persistedAccount = hibernateHelper.getById(HibernateAccount.class, accountId);
        if (persistedAccount == null) {
            throw new EntityNotFoundException(Account.class, "Account " + accountId + " not found");
        }
        accountToUpdate.setStudyId(persistedAccount.getStudyId());
        accountToUpdate.setEmail(persistedAccount.getEmail());
        accountToUpdate.setCreatedOn(persistedAccount.getCreatedOn());
        accountToUpdate.setPasswordModifiedOn(persistedAccount.getPasswordModifiedOn());

        // Update modifiedOn.
        accountToUpdate.setModifiedOn(DateUtils.getCurrentMillisFromEpoch());

        // Update
        hibernateHelper.update(accountToUpdate);
    }

    /** {@inheritDoc} */
    @Override
    public Account getAccount(Study study, String id) {
        HibernateAccount hibernateAccount = hibernateHelper.getById(HibernateAccount.class, id);
        if (hibernateAccount != null) {
            validateHealthCode(study.getStudyIdentifier(), hibernateAccount, true);
            Account account = unmarshallAccount(hibernateAccount);
            return account;
        } else {
            // In keeping with the email implementation, just return null
            return null;
        }
    }

    /** {@inheritDoc} */
    @Override
    public Account getAccountWithEmail(Study study, String email) {
        HibernateAccount hibernateAccount = getHibernateAccountByEmail(study.getStudyIdentifier(), email);
        if (hibernateAccount != null) {
            validateHealthCode(study.getStudyIdentifier(), hibernateAccount, true);
            return unmarshallAccount(hibernateAccount);
        } else {
            return null;
        }
    }

    private HibernateAccount fetchHibernateAccount(Study study, SignIn signIn) {
        // Fetch account
        HibernateAccount hibernateAccount = getHibernateAccountByEmail(study.getStudyIdentifier(), signIn.getEmail());
        if (hibernateAccount == null || hibernateAccount.getStatus() == AccountStatus.UNVERIFIED) {
            throw new EntityNotFoundException(Account.class);
        }
        if (hibernateAccount.getStatus() == AccountStatus.DISABLED) {
            throw new AccountDisabledException();
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

    // Helper method to get a single account for a given study and email address.
    private HibernateAccount getHibernateAccountByEmail(StudyIdentifier studyId, String email) {
        List<HibernateAccount> accountList = hibernateHelper.queryGet("from HibernateAccount where studyId='" +
                studyId.getIdentifier() + "' and email='" + email + "'", null, null, HibernateAccount.class);
        if (accountList.isEmpty()) {
            return null;
        }
        HibernateAccount hibernateAccount = accountList.get(0);

        if (accountList.size() > 1) {
            LOG.warn("Multiple accounts found for the same study and email address; example accountId=" +
                    hibernateAccount.getId());
        }

        return hibernateAccount;
    }

    /** {@inheritDoc} */
    @Override
    public void deleteAccount(Study study, String id) {
        hibernateHelper.deleteById(HibernateAccount.class, id);
    }

    /** {@inheritDoc} */
    @Override
    public Iterator<AccountSummary> getAllAccounts() {
        List<HibernateAccount> hibernateAccountList = hibernateHelper.queryGet("from HibernateAccount", null, null,
                HibernateAccount.class);
        return hibernateAccountList.stream().map(HibernateAccountDao::unmarshallAccountSummary).iterator();
    }

    /** {@inheritDoc} */
    @Override
    public Iterator<AccountSummary> getStudyAccounts(Study study) {
        List<HibernateAccount> hibernateAccountList = hibernateHelper.queryGet("from HibernateAccount where " +
                "studyId='" + study.getIdentifier() + "'", null, null, HibernateAccount.class);
        return hibernateAccountList.stream().map(HibernateAccountDao::unmarshallAccountSummary).iterator();
    }

    /** {@inheritDoc} */
    @Override
    public PagedResourceList<AccountSummary> getPagedAccountSummaries(Study study, int offsetBy, int pageSize,
            String emailFilter, DateTime startTime, DateTime endTime) {
        // Note: emailFilter can be any substring, not just prefix/suffix
        // Note: start- and endTime are inclusive.
        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("from HibernateAccount where studyId='");
        queryBuilder.append(study.getIdentifier());
        queryBuilder.append("'");
        if (StringUtils.isNotBlank(emailFilter)) {
            queryBuilder.append(" and email like '%");
            queryBuilder.append(emailFilter);
            queryBuilder.append("%'");
        }
        if (startTime != null) {
            queryBuilder.append(" and createdOn >= ");
            queryBuilder.append(startTime.getMillis());
        }
        if (endTime != null) {
            queryBuilder.append(" and createdOn <= ");
            queryBuilder.append(endTime.getMillis());
        }
        String query = queryBuilder.toString();
        
        // Don't retrieve unused columns, clientData can be large
        String getQuery = ACCOUNT_SUMMARY_QUERY_PREFIX + query;

        // Get page of accounts.
        List<HibernateAccount> hibernateAccountList = hibernateHelper.queryGet(getQuery, offsetBy, pageSize,
                HibernateAccount.class);
        List<AccountSummary> accountSummaryList = hibernateAccountList.stream()
                .map(HibernateAccountDao::unmarshallAccountSummary).collect(Collectors.toList());

        // Get count of accounts.
        int count = hibernateHelper.queryCount(query);

        // Package results and return.
        return new PagedResourceList<>(accountSummaryList, count)
                .withRequestParam(ResourceList.OFFSET_BY, offsetBy)
                .withRequestParam(ResourceList.PAGE_SIZE, pageSize)
                .withRequestParam(ResourceList.EMAIL_FILTER, emailFilter)
                .withRequestParam(ResourceList.START_TIME, startTime)
                .withRequestParam(ResourceList.END_TIME, endTime);
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
        hibernateAccount.setHealthCode(genericAccount.getHealthCode());
        hibernateAccount.setHealthId(genericAccount.getHealthId());
        hibernateAccount.setFirstName(genericAccount.getFirstName());
        hibernateAccount.setLastName(genericAccount.getLastName());
        hibernateAccount.setPasswordAlgorithm(genericAccount.getPasswordAlgorithm());
        hibernateAccount.setPasswordHash(genericAccount.getPasswordHash());
        hibernateAccount.setRoles(genericAccount.getRoles());
        hibernateAccount.setStatus(genericAccount.getStatus());
        hibernateAccount.setVersion(genericAccount.getVersion());
        
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
    private void validateHealthCode(StudyIdentifier studyId, HibernateAccount hibernateAccount, boolean doSave) {
        if (StringUtils.isBlank(hibernateAccount.getHealthCode()) ||
                StringUtils.isBlank(hibernateAccount.getHealthId())) {
            // Generate health code mapping.
            HealthId healthId = healthCodeService.createMapping(studyId);
            hibernateAccount.setHealthCode(healthId.getCode());
            hibernateAccount.setHealthId(healthId.getId());

            // We modified it. Update modifiedOn.
            long modifiedOn = DateUtils.getCurrentMillisFromEpoch();
            hibernateAccount.setModifiedOn(modifiedOn);
            
            // If called from a get method, we do need to update the account. If called as part of an
            // authentication pathway, we don't save here because we will save the account after we rotate 
            // the reauthentication token.
            if (doSave) {
                hibernateHelper.update(hibernateAccount);    
            }
        }
    }

    // Helper method which unmarshall a HibernateAccount into a GenericAccount.
    // Package-scoped to facilitate unit tests.
    static Account unmarshallAccount(HibernateAccount hibernateAccount) {
        // Simple attributes
        GenericAccount account = new GenericAccount();
        account.setId(hibernateAccount.getId());
        account.setEmail(hibernateAccount.getEmail());
        account.setFirstName(hibernateAccount.getFirstName());
        account.setLastName(hibernateAccount.getLastName());
        account.setPasswordAlgorithm(hibernateAccount.getPasswordAlgorithm());
        account.setPasswordHash(hibernateAccount.getPasswordHash());
        account.setHealthCode(hibernateAccount.getHealthCode());
        account.setHealthId(hibernateAccount.getHealthId());
        account.setStatus(hibernateAccount.getStatus());
        account.setRoles(hibernateAccount.getRoles());
        account.setVersion(hibernateAccount.getVersion());
        
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
        return new AccountSummary(hibernateAccount.getFirstName(),
                hibernateAccount.getLastName(), hibernateAccount.getEmail(), hibernateAccount.getId(), createdOn,
                hibernateAccount.getStatus(), studyId);
    }
}
