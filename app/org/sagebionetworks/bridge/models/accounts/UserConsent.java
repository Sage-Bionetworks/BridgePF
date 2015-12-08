package org.sagebionetworks.bridge.models.accounts;

import org.sagebionetworks.bridge.models.BridgeEntity;

public interface UserConsent extends BridgeEntity {
    
    public String getSubpopulationGuid();

    public long getConsentCreatedOn();
    
    public long getSignedOn();
    
    public Long getWithdrewOn();

}
