package org.sagebionetworks.bridge.models;

public interface UserConsent extends BridgeEntity {
    
    public long getSignedOn();

    public String getStudyKey();

    public long getConsentCreatedOn();

}
