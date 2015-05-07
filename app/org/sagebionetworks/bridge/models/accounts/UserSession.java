package org.sagebionetworks.bridge.models.accounts;

import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

public class UserSession {

    private boolean authenticated;
    private String environment;
    private String sessionToken;
    private String internalSessionToken;
    private User user;
    private StudyIdentifier studyIdentifier;

    public UserSession() {
        this.user = new User();
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
    public String getEnvironment() {
        return environment;
    }
    public void setEnvironment(String environment) {
        this.environment = environment;
    }
    public StudyIdentifier getStudyIdentifier() {
        return studyIdentifier;
    }
    public void setStudyIdentifier(StudyIdentifier studyIdentifier) {
        this.studyIdentifier = studyIdentifier;
    }
    public User getUser() {
        return user;
    }
    public void setUser(User user) {
        this.user = user;
    }
}
