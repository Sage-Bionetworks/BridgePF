package org.sagebionetworks.bridge.models;

/**
 * Greatly trimmed user session object that is embedded in the initial render of the 
 * web application.
 *
 */
public class UserSessionInfo {

    private final boolean authenticated;
    private final boolean consented;
    private final boolean dataSharing;
    private final String sessionToken;
    private final String username;
    
    public UserSessionInfo(UserSession session) {
        this.authenticated = session.isAuthenticated();
        this.sessionToken = session.getSessionToken();
        this.consented = session.getUser().doesConsent();
        this.dataSharing = session.getUser().isDataSharing();
        this.username = session.getUser().getUsername();
    }

    public boolean isAuthenticated() {
        return authenticated;
    }
    public boolean isConsented() {
        return consented;
    }
    public boolean isDataSharing() {
        return dataSharing;
    }
    public String getSessionToken() {
        return sessionToken;
    }
    public String getUsername() {
        return username;
    }
}
