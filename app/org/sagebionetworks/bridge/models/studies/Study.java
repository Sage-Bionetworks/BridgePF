package org.sagebionetworks.bridge.models.studies;

import java.util.Set;

import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

// This is required or Jackson searches for, and eventually, finds the same annotation for StudyIdentifer, 
// and attempts to use that to deserialize study (not what you want).
@JsonDeserialize(as=DynamoStudy.class)
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
    
    public Set<String> getUserProfileAttributes();
    public void setUserProfileAttributes(Set<String> attributes);

    public PasswordPolicy getPasswordPolicy();
    public void setPasswordPolicy(PasswordPolicy passwordPolicy);

    public EmailTemplate getVerifyEmailTemplate();
    public void setVerifyEmailTemplate(EmailTemplate template);
    
    public EmailTemplate getResetPasswordTemplate();
    public void setResetPasswordTemplate(EmailTemplate template);
}
