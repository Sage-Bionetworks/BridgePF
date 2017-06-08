package org.sagebionetworks.bridge.models.accounts;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

/**
 * Encryption of account values is handled transparently by the account implementation. 
 * All values are set and retrieved in clear text.
 */
public interface Account extends BridgeEntity {

    default ConsentSignature getActiveConsentSignature(SubpopulationGuid subpopGuid) {
        List<ConsentSignature> history = getConsentSignatureHistory(subpopGuid);
        if (!history.isEmpty()) {
            ConsentSignature signature = history.get(history.size()-1);
            return (signature.getWithdrewOn() == null) ? signature : null;
        }
        return null;
    }
    
    String getFirstName();
    void setFirstName(String firstName);
    
    String getLastName();
    void setLastName(String lastName);
    
    String getAttribute(String name);
    void setAttribute(String name, String value);

    String getEmail();
    void setEmail(String email);

    /**
     * Gets an immutable copy of the consents this account has for the given subpopulation. Consents should be returned
     * in the order they were signed (earliest first). Returns an empty list if there are no consents.
     */
    List<ConsentSignature> getConsentSignatureHistory(SubpopulationGuid subpopGuid);

    /**
     * Sets the consents for the given subpopulation into the account. The consents should be in the order they were
     * signed (earliest first). A copy of the consent list is mode, so that changes to the input list does not
     * propagate to the account.
     */
    void setConsentSignatureHistory(SubpopulationGuid subpopGuid, List<ConsentSignature> consentSignatureList);

    /** Returns an immutable copy of all consents in the account, keyed by subpopulation. */
    Map<SubpopulationGuid,List<ConsentSignature>> getAllConsentSignatureHistories();
    
    String getHealthCode();

    void setHealthId(HealthId healthId);

    AccountStatus getStatus();
    void setStatus(AccountStatus status);

    StudyIdentifier getStudyIdentifier();

    /** Gets an immutable copy of the set of roles attached to this account. */
    Set<Roles> getRoles();

    /**
     * Sets the roles in this account to match the roles specified by the input. A copy of the roles is made, so that
     * changes to the input list does not propagate to the account.
     */
    void setRoles(Set<Roles> roles);

    /**
     * This is the store-specific identifier for the account (in the case of 
     * Stormpath, it's the unique part of the href they return for this account).
     */
    String getId();
    
    DateTime getCreatedOn();
}
