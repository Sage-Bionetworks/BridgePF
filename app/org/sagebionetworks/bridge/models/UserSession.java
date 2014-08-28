package org.sagebionetworks.bridge.models;

public class UserSession {

    private boolean authenticated;
    private String environment;
    private String sessionToken;
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
    public User getUser() {
        return user;
    }
    public void setUser(User user) {
        this.user = user;
    }
    public String getType() {
        return this.getClass().getSimpleName();
    }

    @Override
    public String toString() {
        return "UserSession [authenticated=" + authenticated + ", environment=" + environment + ", sessionToken="
                + sessionToken + ", user=" + user + "]";
    }
}
