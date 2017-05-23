package org.sagebionetworks.bridge.hibernate;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.exceptions.AccountDisabledException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.PagedResourceList;
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
import org.sagebionetworks.bridge.services.HealthCodeService;

/** Hibernate implementation of Account Dao. */
public class HibernateAccountDao implements AccountDao {
    private static final Logger LOG = LoggerFactory.getLogger(HibernateAccountDao.class);

    private HealthCodeService healthCodeService;
    private HibernateHelper hibernateHelper;

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
        // Not yet implemented. See https://sagebionetworks.jira.com/browse/BRIDGE-1838
        // Until then, the only way to verify an account in MySQL is to edit the database directly.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /** {@inheritDoc} */
    @Override
    public void resendEmailVerificationToken(StudyIdentifier studyIdentifier, Email email) {
        // Not yet implemented. See https://sagebionetworks.jira.com/browse/BRIDGE-1838
        // Until then, the only way to verify an account in MySQL is to edit the database directly.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /** {@inheritDoc} */
    @Override
    public void requestResetPassword(Study study, Email email) {
        // Not yet implemented. See https://sagebionetworks.jira.com/browse/BRIDGE-1838
        // Until then, the only way to reset a password in MySQL is to call changePassword() or edit the database
        // directly.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /** {@inheritDoc} */
    @Override
    public void resetPassword(PasswordReset passwordReset) {
        // Not yet implemented. See https://sagebionetworks.jira.com/browse/BRIDGE-1838
        // Until then, the only way to reset a password in MySQL is to call changePassword() or edit the database
        // directly.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /** {@inheritDoc} */
    @Override
    public void changePassword(Account account, String newPassword) {
        String accountId = account.getId();
        PasswordAlgorithm passwordAlgorithm = PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM;
        String passwordHash;
        try {
            passwordHash = passwordAlgorithm.generateHash(newPassword);
        } catch (InvalidKeyException | InvalidKeySpecException | NoSuchAlgorithmException ex) {
            throw new BridgeServiceException("Error changing password: " + ex.getMessage(), ex);
        }

        long modifiedOn = DateUtils.getCurrentMillisFromEpoch();
        int numRowsUpdated = hibernateHelper.queryUpdate("update HibernateAccount set modifiedOn=" + modifiedOn +
                ", passwordAlgorithm='" + passwordAlgorithm.name() + "', passwordHash='" +  passwordHash +
                "', passwordModifiedOn=" + modifiedOn + " where id='" + accountId + "'");
        if (numRowsUpdated == 0) {
            throw new BridgeServiceException("Failed to update password for account " + accountId);
        }
    }

    /** {@inheritDoc} */
    @Override
    public Account authenticate(Study study, SignIn signIn) {
        // Fetch account
        HibernateAccount hibernateAccount = getHibernateAccountByEmail(study.getStudyIdentifier(), signIn.getEmail());
        if (hibernateAccount == null || hibernateAccount.getStatus() == AccountStatus.UNVERIFIED) {
            throw new EntityNotFoundException(Account.class);
        }
        if (hibernateAccount.getStatus() == AccountStatus.DISABLED) {
            throw new AccountDisabledException();
        }

        // Verify password
        PasswordAlgorithm passwordAlgorithm = hibernateAccount.getPasswordAlgorithm();
        String passwordHash = hibernateAccount.getPasswordHash();
        if (passwordAlgorithm == null || StringUtils.isBlank(passwordHash)) {
            LOG.error("Account " + hibernateAccount.getId() + " is enabled but has no password");
            throw new EntityNotFoundException(Account.class);
        }
        try {
            if (!passwordAlgorithm.checkHash(passwordHash, signIn.getPassword())) {
                // To prevent enumeration attacks, if the password doesn't match, throw 404 account not found.
                throw new EntityNotFoundException(Account.class);
            }
        } catch (InvalidKeyException | InvalidKeySpecException | NoSuchAlgorithmException ex) {
            throw new BridgeServiceException("Error validating password: " + ex.getMessage(), ex);
        }

        // Unmarshall account
        return unmarshallAccount(hibernateAccount);
    }

    /** {@inheritDoc} */
    @Override
    public Account constructAccount(Study study, String email, String password) {
        // Set basic params from inputs.
        GenericAccount account = new GenericAccount();
        account.setStudyId(study.getStudyIdentifier());
        account.setEmail(email);

        // Hash password.
        PasswordAlgorithm passwordAlgorithm = PasswordAlgorithm.DEFAULT_PASSWORD_ALGORITHM;
        String passwordHash;
        try {
            passwordHash = passwordAlgorithm.generateHash(password);
        } catch (InvalidKeyException | InvalidKeySpecException | NoSuchAlgorithmException ex) {
            throw new BridgeServiceException("Error creating password: " + ex.getMessage(), ex);
        }
        account.setPasswordAlgorithm(passwordAlgorithm);
        account.setPasswordHash(passwordHash);

        // Generate health code.
        HealthId healthId = healthCodeService.createMapping(study.getStudyIdentifier());
        account.setHealthId(healthId);

        return account;
    }

    /** {@inheritDoc} */
    @Override
    public String createAccount(Study study, Account account, boolean sendVerifyEmail) {
        // Initial creation of account. Fill in basic initial parameters.
        String accountId = BridgeUtils.generateGuid();

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

        // TODO send verify email

        return accountId;
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
            return unmarshallAccount(hibernateAccount);
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
            return unmarshallAccount(hibernateAccount);
        } else {
            return null;
        }
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
        HibernateAccount key = new HibernateAccount();
        key.setId(id);
        hibernateHelper.delete(key);
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
            String emailFilter, DateTime startDate, DateTime endDate) {
        // Note: emailFilter can be any substring, not just prefix/suffix
        // Note: start- and endDate are inclusive.
        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("from HibernateAccount where studyId='");
        queryBuilder.append(study.getIdentifier());
        queryBuilder.append("'");
        if (StringUtils.isNotBlank(emailFilter)) {
            queryBuilder.append(" and email like '%");
            queryBuilder.append(emailFilter);
            queryBuilder.append("%'");
        }
        if (startDate != null) {
            queryBuilder.append(" and createdOn >= ");
            queryBuilder.append(startDate.getMillis());
        }
        if (endDate != null) {
            queryBuilder.append(" and createdOn <= ");
            queryBuilder.append(endDate.getMillis());
        }
        String query = queryBuilder.toString();

        // Get page of accounts.
        List<HibernateAccount> hibernateAccountList = hibernateHelper.queryGet(query, offsetBy, pageSize,
                HibernateAccount.class);
        List<AccountSummary> accountSummaryList = hibernateAccountList.stream()
                .map(HibernateAccountDao::unmarshallAccountSummary).collect(Collectors.toList());

        // Get count of accounts.
        int count = hibernateHelper.queryCount(query);

        // Package results and return.
        return new PagedResourceList<>(accountSummaryList, offsetBy, pageSize, count)
                .withFilter("emailFilter", emailFilter).withFilter("startDate", startDate)
                .withFilter("endDate", endDate);
    }

    /** {@inheritDoc} */
    @Override
    public String getHealthCodeForEmail(Study study, String email) {
        HibernateAccount hibernateAccount = getHibernateAccountByEmail(study.getStudyIdentifier(), email);
        if (hibernateAccount != null) {
            return hibernateAccount.getHealthCode();
        } else {
            return null;
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
        hibernateAccount.setHealthCode(genericAccount.getHealthCode());
        hibernateAccount.setHealthId(genericAccount.getHealthId());
        hibernateAccount.setFirstName(genericAccount.getFirstName());
        hibernateAccount.setLastName(genericAccount.getLastName());
        hibernateAccount.setPasswordAlgorithm(genericAccount.getPasswordAlgorithm());
        hibernateAccount.setPasswordHash(genericAccount.getPasswordHash());
        hibernateAccount.setRoles(genericAccount.getRoles());
        hibernateAccount.setStatus(genericAccount.getStatus());

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
            account.getConsentSignatureHistory(subpopGuid).add(consentSignature);
        }

        // Sort consents by signedOn, from oldest to newest.
        for (List<ConsentSignature> consentSignatureListForSubpop : account.getAllConsentSignatureHistories()
                .values()) {
            Collections.sort(consentSignatureListForSubpop, (c1, c2) -> Long.compare(c1.getSignedOn(),
                    c2.getSignedOn()));
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
