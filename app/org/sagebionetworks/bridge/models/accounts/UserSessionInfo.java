package org.sagebionetworks.bridge.models.accounts;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

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
    private final String environment;
    private final String email;
    private final Set<Roles> roles;
    private final Set<String> dataGroups;
    private final Map<SubpopulationGuid,ConsentStatus> consentStatuses;

    public UserSessionInfo(UserSession session) {
        this.authenticated = session.isAuthenticated();
        this.sessionToken = session.getSessionToken();
        this.sharingScope = session.getUser().getSharingScope();
        this.roles = BridgeUtils.nullSafeImmutableSet(session.getUser().getRoles());
        this.dataGroups = BridgeUtils.nullSafeImmutableSet(session.getUser().getDataGroups());
        this.environment = ENVIRONMENTS.get(session.getEnvironment());
        this.email = session.getUser().getEmail();
        this.consentStatuses = session.getUser().getConsentStatuses();
    }

    public boolean isAuthenticated() {
        return authenticated;
    }
    public Map<SubpopulationGuid,ConsentStatus> getConsentStatuses() {
        return consentStatuses;
    }
    public boolean isConsented() {
        return ConsentStatus.isUserConsented(consentStatuses);
    }
    public boolean isSignedMostRecentConsent() {
        return ConsentStatus.isConsentCurrent(consentStatuses);
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
    /**
     * Provided for API compatibility, this is always the email address of the account. 
     * @deprecated
     */
    public String getUsername() {
        return email;
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
