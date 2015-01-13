package org.sagebionetworks.bridge.models.studies;

import java.util.List;

import org.sagebionetworks.bridge.models.BridgeEntity;

public interface Study extends BridgeEntity {

    public String getName();
    public void setName(String name);
    
    public String getIdentifier();
    public void setIdentifier(String identifier);
    
    public Long getVersion();
    public void setVersion(Long version);
    
    public String getResearcherRole();
    public void setResearcherRole(String role);
    
    public int getMinAgeOfConsent();
    public void setMinAgeOfConsent(int minAge);
    
    public int getMaxNumOfParticipants();
    public void setMaxNumOfParticipants(int maxParticipants);
    
    public List<String> getTrackers();
    
    public String getStormpathHref();
    public void setStormpathHref(String stormpathHref);
    
    public String getHostname();
    public void setHostname(String hostname);
    
}
