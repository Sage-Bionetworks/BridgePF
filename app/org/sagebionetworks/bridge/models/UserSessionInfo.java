package org.sagebionetworks.bridge.models;

import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;

/**
 * Greatly trimmed user session object that is embedded in the initial render of the
 * web application.
 *
 */
public class UserSessionInfo {

    private final boolean authenticated;
    private final boolean signedMostRecentConsent;
    private final boolean consented;
    private final SharingScope sharingScope;
    private final String sessionToken;
    private final String username;

    public UserSessionInfo(UserSession session) {
        this.authenticated = session.isAuthenticated();
        this.sessionToken = session.getSessionToken();
        this.signedMostRecentConsent = session.getUser().hasSignedMostRecentConsent();
        this.consented = session.getUser().doesConsent();
        this.sharingScope = session.getUser().getSharingScope();
        this.username = session.getUser().getUsername();
    }

    public boolean isAuthenticated() {
        return authenticated;
    }
    public boolean isConsented() {
        return consented;
    }
    public boolean isSignedMostRecentConsent() {
        return signedMostRecentConsent;
    }
    public SharingScope getSharingScope() {
        return sharingScope;
    }
    public boolean isDataSharing() {
        return (sharingScope != SharingScope.NO_SHARING);
    }
    public String getSessionToken() {
        return sessionToken;
    }
    public String getUsername() {
        return username;
    }
}
