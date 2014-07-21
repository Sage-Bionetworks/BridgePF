package org.sagebionetworks.bridge.models;

public class UserSession {

    private boolean authenticated;
    private boolean consent;
    private String environment;
    private String healthDataCode;
    private String sessionToken;
    private String studyKey;
    private User user;
    
    public UserSession() {
        this.user = new User();
    }

    public String getSessionToken() {
        return sessionToken;
    }
    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
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
    public String getStudyKey() {
        return studyKey;
    }
    public void setStudyKey(String studyKey) {
        this.studyKey = studyKey;
    }
    public boolean doesConsent() {
        return consent;
    }
    // Jackson serialization needs this method, even though linguistically, it makes no sense.
    public boolean isConsent() {
        return consent;
    }
    public void setConsent(boolean consent) {
        this.consent = consent;
    }
    public String getHealthDataCode() {
        return healthDataCode;
    }
    public void setHealthDataCode(String healthDataCode) {
        this.healthDataCode = healthDataCode;
    }
    
    /*
     * User Object
     */
    public User getUser() {
        return user;
    }
    public void setUser(User user) {
        this.user.setEmail(user.getEmail()); // Deep copy? I'm not sure if this matters here.
        this.user.setUsername(user.getUsername());
        this.user.setStormpathHref(user.getStormpathHref());
        this.user.setFirstName(user.getFirstName());
        this.user.setLastName(user.getLastName());
    }
    public String getUsername() {
        return user.getUsername();
    }
    public void setUsername(String username) {
        user.setUsername(username);
    }
    public String getStormpathHref() {
        return user.getStormpathHref();
    }
    public void setStormpathHref(String stormpathHref) {
        user.setStormpathHref(stormpathHref);
    }
    
   
}
