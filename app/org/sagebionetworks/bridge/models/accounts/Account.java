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
    
    List<ConsentSignature> getConsentSignatureHistory(SubpopulationGuid subpopGuid);
    
    Map<SubpopulationGuid,List<ConsentSignature>> getAllConsentSignatureHistories();
    
    String getHealthCode();

    void setHealthId(HealthId healthId);

    AccountStatus getStatus();
    void setStatus(AccountStatus status);

    StudyIdentifier getStudyIdentifier();
    
    Set<Roles> getRoles();
    void setRoles(Set<Roles> roles);

    /**
     * This is the store-specific identifier for the account (in the case of 
     * Stormpath, it's the unique part of the href they return for this account).
     */
    String getId();
    
    DateTime getCreatedOn();
}
