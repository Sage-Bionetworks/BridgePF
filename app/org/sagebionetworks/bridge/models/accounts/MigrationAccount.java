package org.sagebionetworks.bridge.models.accounts;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.stormpath.StormpathAccount;

/**
 * This class encapsulates both a GenericAccount and a StormpathAccount. These classes are not cross-compatible. This
 * class provides a wrapper that writes to both, so that accounts still work while the migration is in progress.
 */
public class MigrationAccount implements Account {
    private final GenericAccount genericAccount;
    private final StormpathAccount stormpathAccount;

    /** Simple constructor. */
    public MigrationAccount(GenericAccount genericAccount, StormpathAccount stormpathAccount) {
        this.genericAccount = genericAccount;
        this.stormpathAccount = stormpathAccount;
    }

    /** Gets the underlying GenericAccount, which represents the account in SQL. */
    public GenericAccount getGenericAccount() {
        return genericAccount;
    }

    /** Gets the underlying StormpathAccount. */
    public StormpathAccount getStormpathAccount() {
        return stormpathAccount;
    }

    /** {@inheritDoc} */
    @Override
    public String getFirstName() {
        return getWithFallback(Account::getFirstName);
    }

    /** {@inheritDoc} */
    @Override
    public void setFirstName(String firstName) {
        setWithFallback(Account::setFirstName, firstName);
    }

    /** {@inheritDoc} */
    @Override
    public String getLastName() {
        return getWithFallback(Account::getLastName);
    }

    /** {@inheritDoc} */
    @Override
    public void setLastName(String lastName) {
        setWithFallback(Account::setLastName, lastName);
    }

    /** {@inheritDoc} */
    @Override
    public String getAttribute(String name) {
        return getWithFallback(account -> account.getAttribute(name));
    }

    /** {@inheritDoc} */
    @Override
    public void setAttribute(String name, String value) {
        setWithFallback((account, v) -> account.setAttribute(name, v), value);
    }

    /** {@inheritDoc} */
    @Override
    public String getEmail() {
        return getWithFallback(Account::getEmail);
    }

    /** {@inheritDoc} */
    @Override
    public void setEmail(String email) {
        setWithFallback(Account::setEmail, email);
    }

    /** {@inheritDoc} */
    @Override
    public List<ConsentSignature> getConsentSignatureHistory(SubpopulationGuid subpopGuid) {
        return getWithFallback(account -> account.getConsentSignatureHistory(subpopGuid));
    }

    /** {@inheritDoc} */
    @Override
    public void setConsentSignatureHistory(SubpopulationGuid subpopGuid, List<ConsentSignature> consentSignatureList) {
        setWithFallback((account, l) -> account.setConsentSignatureHistory(subpopGuid, l), consentSignatureList);
    }

    /** {@inheritDoc} */
    @Override
    public Map<SubpopulationGuid, List<ConsentSignature>> getAllConsentSignatureHistories() {
        return getWithFallback(Account::getAllConsentSignatureHistories);
    }

    /** {@inheritDoc} */
    @Override
    public String getHealthCode() {
        return getWithFallback(Account::getHealthCode);
    }

    /** {@inheritDoc} */
    @Override
    public void setHealthId(HealthId healthId) {
        setWithFallback(Account::setHealthId, healthId);
    }

    /** {@inheritDoc} */
    @Override
    public AccountStatus getStatus() {
        return getWithFallback(Account::getStatus);
    }

    /** {@inheritDoc} */
    @Override
    public void setStatus(AccountStatus status) {
        setWithFallback(Account::setStatus, status);
    }

    /** {@inheritDoc} */
    @Override
    public StudyIdentifier getStudyIdentifier() {
        return getWithFallback(Account::getStudyIdentifier);
    }

    /** {@inheritDoc} */
    @Override
    public Set<Roles> getRoles() {
        return getWithFallback(Account::getRoles);
    }

    /** {@inheritDoc} */
    @Override
    public void setRoles(Set<Roles> roles) {
        setWithFallback(Account::setRoles, roles);
    }

    /** {@inheritDoc} */
    @Override
    public String getId() {
        return getWithFallback(Account::getId);
    }

    /** {@inheritDoc} */
    @Override
    public DateTime getCreatedOn() {
        return getWithFallback(Account::getCreatedOn);
    }

    /**
     * Helper function to call a getter, with fallback logic. If the GenericAccount is present, we use that. Otherwise,
     * if the StormpathAccount is present, we use that. If somehow neither is present, we return null.
     */
    private <T> T getWithFallback(Function<Account, T> func) {
        if (genericAccount != null) {
            return func.apply(genericAccount);
        }
        if (stormpathAccount != null) {
            return func.apply(stormpathAccount);
        }
        return null;
    }

    /**
     * Helper function to call a setter on both the GenericAccount and StormpathAccount, with the appropriate null
     * checks.
     */
    private <T> void setWithFallback(BiConsumer<Account, T> func, T value) {
        if (genericAccount != null) {
            func.accept(genericAccount, value);
        }
        if (stormpathAccount != null) {
            func.accept(stormpathAccount, value);
        }
    }
}
