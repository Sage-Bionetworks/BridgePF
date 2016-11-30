package org.sagebionetworks.bridge.models.accounts;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.Roles.ADMINISTRATIVE_ROLES;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ImmutableMap;

public class UserSession {

    private static final StudyParticipant EMPTY_PARTICIPANT = new StudyParticipant.Builder().build();
    
    private boolean authenticated;
    private Environment environment;
    private String sessionToken;
    private String internalSessionToken;
    private StudyIdentifier studyIdentifier;
    private StudyParticipant participant;
    private Map<SubpopulationGuid,ConsentStatus> consentStatuses = ImmutableMap.of();

    public UserSession() {
        this.participant = EMPTY_PARTICIPANT;
    }
    
    public UserSession(StudyParticipant participant) {
        checkNotNull(participant);
        this.participant = participant;
    }
    
    public StudyParticipant getParticipant() {
        return participant;
    }
    public void setParticipant(StudyParticipant participant) {
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
    public boolean isAdmin() {
        return !Collections.disjoint(ADMINISTRATIVE_ROLES, this.participant.getRoles());
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
    public boolean doesConsent() {
        return ConsentStatus.isUserConsented(consentStatuses);
    }
    public boolean hasSignedMostRecentConsent() {
        return ConsentStatus.isConsentCurrent(consentStatuses);
    }
    public boolean isInRole(Roles role) {
        return (role != null && participant.getRoles().contains(role));
    }
    public boolean isInRole(Set<Roles> roleSet) {
        return roleSet != null && !Collections.disjoint(participant.getRoles(), roleSet);
    }
    // These are accessed so frequently it is worth having convenience accessors
    @JsonIgnore
    public String getId() {
        return participant.getId();
    }
    @JsonIgnore
    public String getHealthCode() {
        return participant.getHealthCode();
    }
    public Map<SubpopulationGuid,ConsentStatus> getConsentStatuses() {
        return consentStatuses;
    }
    public void setConsentStatuses(Map<SubpopulationGuid,ConsentStatus> consentStatuses) {
        this.consentStatuses = ImmutableMap.copyOf(consentStatuses);
    }
}
