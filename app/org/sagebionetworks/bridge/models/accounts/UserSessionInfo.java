package org.sagebionetworks.bridge.models.accounts;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.json.SubpopulationGuidDeserializer;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * Greatly trimmed user session object that is embedded in the initial render of the
 * web application.
 *
 */
public class UserSessionInfo {
    
    private static final Map<Environment,String> ENVIRONMENTS = new HashMap<>();
    static {
        ENVIRONMENTS.put(Environment.LOCAL, "local");
        ENVIRONMENTS.put(Environment.DEV, "develop");
        ENVIRONMENTS.put(Environment.UAT, "staging");
        ENVIRONMENTS.put(Environment.PROD, "production");
    }

    private final boolean authenticated;
    private final SharingScope sharingScope;
    private final String sessionToken;
    private final String username;
    private final String environment;
    private final Set<Roles> roles;
    private final Set<String> dataGroups;
    @JsonDeserialize(keyUsing = SubpopulationGuidDeserializer.class)
    private final Map<SubpopulationGuid,ConsentStatus> consentStatuses;

    public UserSessionInfo(UserSession session) {
        this.authenticated = session.isAuthenticated();
        this.sessionToken = session.getSessionToken();
        this.sharingScope = session.getUser().getSharingScope();
        this.username = session.getUser().getUsername();
        this.roles = BridgeUtils.nullSafeImmutableSet(session.getUser().getRoles());
        this.dataGroups = BridgeUtils.nullSafeImmutableSet(session.getUser().getDataGroups());
        this.environment = ENVIRONMENTS.get(session.getEnvironment());
        this.consentStatuses = session.getUser().getConsentStatuses();
    }

    public boolean isAuthenticated() {
        return authenticated;
    }
    public Map<SubpopulationGuid,ConsentStatus> getConsentStatuses() {
        return consentStatuses;
    }
    public boolean isConsented() {
        return ConsentStatus.isUserConsented(consentStatuses.values());
    }
    public boolean isSignedMostRecentConsent() {
        return ConsentStatus.isConsentCurrent(consentStatuses.values());
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
    public String getEnvironment() {
        return environment;
    }
    public Set<Roles> getRoles() {
        return roles;
    }
    public Set<String> getDataGroups() {
        return dataGroups;
    }
}
