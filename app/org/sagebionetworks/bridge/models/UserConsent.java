package org.sagebionetworks.bridge.models;

public interface UserConsent {
    
    public boolean getDataSharing();
    
    public String getStudyKey();
    
    public long getConsentCreatedOn();

}
