package org.sagebionetworks.bridge.models.accounts;

import org.sagebionetworks.bridge.models.BridgeEntity;

public interface UserConsent extends BridgeEntity {
    
    public long getSignedOn();

    public String getStudyKey();

    public long getConsentCreatedOn();

}
