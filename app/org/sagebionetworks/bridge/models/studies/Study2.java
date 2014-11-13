package org.sagebionetworks.bridge.models.studies;

import java.util.List;

import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;

@BridgeTypeName("Study")
public interface Study2 extends BridgeEntity {

    public String getName();
    public void setName(String name);
    
    public String getIdentifier();
    public void setIdentifier(String identifier);
    
    public Long getVersion();
    public void setVersion(Long version);
    
    public String getStormpathUrl();
    public void setStormpathUrl(Environment env, String value);
    
    public String getResearcherRole();
    public void setResearcherRole(String role);
    
    public int getMinAgeOfConsent();
    public void setMinAgeOfConsent(int minAge);
    
    public int getMaxParticipants();
    public void setMaxParticipants(int maxParticipants);
    
    public List<String> getTrackerIdentifiers();
    
}
