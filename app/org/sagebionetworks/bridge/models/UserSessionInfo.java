package org.sagebionetworks.bridge.models;

/**
 * Greatly trimmed user session object that is embedded in the initial render of the 
 * web application.
 *
 */
public class UserSessionInfo {

    private final boolean authenticated;
    private final String sessionToken;
    private final String username;
    
    public UserSessionInfo(UserSession session) {
        authenticated = session.isAuthenticated();
        sessionToken = session.getSessionToken();
        username = session.getUsername();
    }

    public boolean isAuthenticated() {
        return authenticated;
    }
    public String getSessionToken() {
        return sessionToken;
    }
    public String getUsername() {
        return username;
    }
}
