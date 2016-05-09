package org.sagebionetworks.bridge.models.accounts;

import java.util.Map;

import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Maps;

public class UserSession {

    private boolean authenticated;
    private Environment environment;
    private String sessionToken;
    private String internalSessionToken;
    private StudyIdentifier studyIdentifier;
    private StudyParticipant participant;
    private Map<SubpopulationGuid,ConsentStatus> consentStatuses = Maps.newHashMap();

    public UserSession(StudyParticipant participant) {
        this.participant = (participant == null) ? new StudyParticipant.Builder().build() : participant;
    }

    public StudyParticipant getStudyParticipant() {
        return participant;
    }
    public void setStudyParticipant(StudyParticipant participant) {
        this.participant = participant;
    }
    public String getSessionToken() {
        return sessionToken;
    }
    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }
    public String getInternalSessionToken() {
        return internalSessionToken;
    }
    public void setInternalSessionToken(String internalSessionToken) {
        this.internalSessionToken = internalSessionToken;
    }
    public boolean isAuthenticated() {
        return authenticated;
    }
    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
    }
    public Environment getEnvironment() {
        return environment;
    }
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }
    public StudyIdentifier getStudyIdentifier() {
        return studyIdentifier;
    }
    public void setStudyIdentifier(StudyIdentifier studyIdentifier) {
        this.studyIdentifier = studyIdentifier;
    }
    @JsonIgnore
    public User getUser() {
        User user = new User();
        user.setId(participant.getId());
        user.setFirstName(participant.getFirstName());
        user.setLastName(participant.getLastName());
        user.setEmail(participant.getEmail());
        user.setHealthCode(participant.getHealthCode());
        user.setSharingScope(participant.getSharingScope());
        user.setAccountCreatedOn(participant.getCreatedOn());
        user.setRoles(participant.getRoles());
        user.setDataGroups(participant.getDataGroups());
        user.setConsentStatuses(consentStatuses);
        user.setLanguages(participant.getLanguages());
        if (studyIdentifier != null) {
            user.setStudyKey(studyIdentifier.getIdentifier());    
        }
        return user;
    }
    public Map<SubpopulationGuid,ConsentStatus> getConsentStatuses() {
        return consentStatuses;
    }
    public void setConsentStatuses(Map<SubpopulationGuid,ConsentStatus> consentStatuses) {
        this.consentStatuses = consentStatuses;
    }
}
