package org.sagebionetworks.bridge.models;

import org.sagebionetworks.bridge.BridgeConstants;

/**
 * Greatly trimmed user session object that is embedded in the initial render of the 
 * web application.
 *
 */
public class UserSessionInfo {

    private final boolean authenticated;
    private final boolean consented;
    private final String sessionToken;
    private final String username;
    
    public UserSessionInfo(UserSession session) {
        this.authenticated = session.isAuthenticated();
        this.sessionToken = session.getSessionToken();
        this.username = session.getUsername();
        this.consented = session.doesConsent();
    }

    public boolean isAuthenticated() {
        return authenticated;
    }
    public boolean isConsented() {
        return consented;
    }
    public String getSessionToken() {
        return sessionToken;
    }
    public String getUsername() {
        return username;
    }
    public String getMessage() {
        return BridgeConstants.CONSENT_REQUIRED_MESSAGE;
    }
}
