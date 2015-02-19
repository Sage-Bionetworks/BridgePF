package org.sagebionetworks.bridge.models.accounts;

import java.util.Set;

import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.models.studies.ConsentSignature;

/**
 * Encryption of account values is handled transparently by the account implementation. 
 * All values are set and retrieved in clear text.
 */
public interface Account extends BridgeEntity {

    public String getUsername();
    public void setUsername(String username);
    
    public String getFirstName();
    public void setFirstName(String firstName);
    
    public String getLastName();
    public void setLastName(String lastName);
    
    public String getPhone();
    public void setPhone(String phone);
    
    public String getEmail();
    public void setEmail(String email);
    
    public ConsentSignature getConsentSignature();
    public void setConsentSignature(ConsentSignature signature);
    
    public String getHealthId();
    public void setHealthId(String healthId);
    
    public Set<String> getRoles();

    /**
     * This is the store-specific identifier for the account (in the case of 
     * Stormpath, it's the unique part of the href they return for this account).
     * @return
     */
    public String getId();
}
