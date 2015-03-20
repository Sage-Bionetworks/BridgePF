package org.sagebionetworks.bridge.models.studies;

import java.util.Set;

import org.sagebionetworks.bridge.models.BridgeEntity;

public interface Study extends BridgeEntity, StudyIdentifier {

    public String getName();
    public void setName(String name);
    
    public String getIdentifier();
    public void setIdentifier(String identifier);
    
    public StudyIdentifier getStudyIdentifier();
    
    public Long getVersion();
    public void setVersion(Long version);
    
    public String getResearcherRole();
    public void setResearcherRole(String role);
    
    public int getMinAgeOfConsent();
    public void setMinAgeOfConsent(int minAge);
    
    public int getMaxNumOfParticipants();
    public void setMaxNumOfParticipants(int maxParticipants);

    public String getSupportEmail();
    public void setSupportEmail(String email);
    
    public String getConsentNotificationEmail();
    public void setConsentNotificationEmail(String email);
    
    public String getStormpathHref();
    public void setStormpathHref(String stormpathHref);
    
    public String getHostname();
    public void setHostname(String hostname);

    public Set<String> getUserProfileAttributes();
    public void setUserProfileAttributes(Set<String> attributes);
}
