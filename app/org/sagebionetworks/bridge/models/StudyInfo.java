package org.sagebionetworks.bridge.models;

import java.util.Set;

import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.studies.Study;

/**
 * Study decorator for JSON production. The Stormpath hostname isn't visible.
 * 
 * NOTE: Could just use @JsonIgnore, but we want to serialize this into Redis and in
 * that case, the value would be lost on deserialization. 
 */
@BridgeTypeName("Study")
public class StudyInfo {

    private Study study;
    
    public StudyInfo(Study study) {
        this.study = study;
    }
    
    public String getName() {
        return study.getName();
    }
    public String getIdentifier() {
        return study.getIdentifier();
    }
    public Long getVersion() {
        return study.getVersion();
    }
    public String getResearcherRole() {
        return study.getResearcherRole();
    }
    public int getMinAgeOfConsent() {
        return study.getMinAgeOfConsent();
    }
    public int getMaxNumOfParticipants() {
        return study.getMaxNumOfParticipants();
    }
    public String getHostname() {
        return study.getHostname();
    }
    public String getSupportEmail() {
        return study.getSupportEmail();
    }
    public String getConsentNotificationEmail() {
        return study.getConsentNotificationEmail();
    }
    public Set<String> getUserProfileAttributes() {
        return study.getUserProfileAttributes();
    }
}
