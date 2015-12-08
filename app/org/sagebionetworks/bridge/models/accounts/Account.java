package org.sagebionetworks.bridge.models.accounts;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.models.studies.ConsentSignature;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

/**
 * Encryption of account values is handled transparently by the account implementation. 
 * All values are set and retrieved in clear text.
 */
public interface Account extends BridgeEntity {

    public default ConsentSignature getActiveConsentSignature(String subpopGuid) {
        List<ConsentSignature> history = getConsentSignatureHistory(subpopGuid);
        if (!history.isEmpty()) {
            ConsentSignature signature = history.get(history.size()-1);
            return (signature.getWithdrewOn() == null) ? signature : null;
        }
        return null;
    }
    
    public String getUsername();
    public void setUsername(String username);
    
    public String getFirstName();
    public void setFirstName(String firstName);
    
    public String getLastName();
    public void setLastName(String lastName);
    
    public String getAttribute(String name);
    public void setAttribute(String name, String value);

    public String getEmail();
    public void setEmail(String email);
    
    public List<ConsentSignature> getConsentSignatureHistory(String subpopGuid);
    
    public Map<String,List<ConsentSignature>> getAllConsentSignatureHistories();
    
    public String getHealthId();
    public void setHealthId(String healthId);

    public StudyIdentifier getStudyIdentifier();
    
    public Set<Roles> getRoles();

    /**
     * This is the store-specific identifier for the account (in the case of 
     * Stormpath, it's the unique part of the href they return for this account).
     * @return
     */
    public String getId();
}
