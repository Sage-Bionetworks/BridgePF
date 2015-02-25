package org.sagebionetworks.bridge.models;

import org.sagebionetworks.bridge.dao.ParticipantOption.ScopeOfSharing;
import org.sagebionetworks.bridge.json.LowercaseEnumJsonSerializer;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Greatly trimmed user session object that is embedded in the initial render of the
 * web application.
 *
 */
public class UserSessionInfo {

    private final boolean authenticated;
    private final boolean signedMostRecentConsent;
    private final boolean consented;
    private final ScopeOfSharing dataSharing;
    private final String sessionToken;
    private final String username;

    public UserSessionInfo(UserSession session) {
        this.authenticated = session.isAuthenticated();
        this.sessionToken = session.getSessionToken();
        this.signedMostRecentConsent = session.getUser().hasSignedMostRecentConsent();
        this.consented = session.getUser().doesConsent();
        this.dataSharing = session.getUser().getDataSharing();
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
    // BridgeObjectMapper is not correctly using this serializer, even though it's added as a module.
    @JsonSerialize(using = LowercaseEnumJsonSerializer.class)
    public ScopeOfSharing getDataSharing() {
        return dataSharing;
    }
    public String getSessionToken() {
        return sessionToken;
    }
    public String getUsername() {
        return username;
    }
}
