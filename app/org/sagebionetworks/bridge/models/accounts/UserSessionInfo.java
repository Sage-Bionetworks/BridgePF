package org.sagebionetworks.bridge.models.accounts;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

/**
 * A view of the user session that maintains the current user session fields and will include fields 
 * as they are added to the StudyParticipant.
 */
public class UserSessionInfo {
    
    private static final String CONSENT_HISTORIES = "consentHistories";
    private static final String ENCRYPTED_HEALTH_CODE = "encryptedHealthCode";
    private static final String HEALTH_CODE = "healthCode";
    private static final String CONSENT_STATUSES = "consentStatuses";
    private static final String CONSENTED = "consented";
    private static final String SIGNED_MOST_RECENT_CONSENT = "signedMostRecentConsent";
    private static final String DATA_SHARING = "dataSharing";
    private static final String ENVIRONMENT = "environment";
    private static final String SESSION_TOKEN = "sessionToken";
    private static final String REAUTH_TOKEN = "reauthToken";
    private static final String AUTHENTICATED = "authenticated";
    private static final String USERNAME = "username";
    private static final String USER_SESSION_INFO = "UserSessionInfo";
    private static final String TYPE = "type";
    
    private static final String PRODUCTION = "production";
    private static final String STAGING = "staging";
    private static final String DEVELOP = "develop";
    private static final String LOCAL = "local";
    
    private static final BridgeObjectMapper MAPPER = BridgeObjectMapper.get();
    private static final Set<String> PROHIBITED_FIELD_NAMES = Sets.newHashSet(TYPE, HEALTH_CODE, ENCRYPTED_HEALTH_CODE, CONSENT_HISTORIES);
    private static final Map<Environment,String> ENVIRONMENTS = new ImmutableMap.Builder<Environment,String>()
            .put(Environment.LOCAL, LOCAL)
            .put(Environment.DEV, DEVELOP)
            .put(Environment.UAT, STAGING)
            .put(Environment.PROD, PRODUCTION).build();

    /**
     * Collapse StudyParticipant properties into the UserSessionInfo JSON so that we 
     * do not need to copy these info the session when we update StudyParticipant. This does
     * make for some horky JSON processing.
     */
    public static JsonNode toJSON(UserSession session) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put(AUTHENTICATED, session.isAuthenticated());
        node.put(DATA_SHARING, session.getParticipant().getSharingScope() != SharingScope.NO_SHARING);
        node.put(SIGNED_MOST_RECENT_CONSENT, ConsentStatus.isConsentCurrent(session.getConsentStatuses()));
        node.put(CONSENTED, ConsentStatus.isUserConsented(session.getConsentStatuses()));
        addNotNull(node, USERNAME, session.getParticipant().getEmail());
        addNotNull(node, REAUTH_TOKEN, session.getReauthToken());
        addNotNull(node, SESSION_TOKEN, session.getSessionToken());
        addNotNull(node, ENVIRONMENT, ENVIRONMENTS.get(session.getEnvironment()));
        ObjectNode statuses = node.with(CONSENT_STATUSES);
        for (Map.Entry<SubpopulationGuid, ConsentStatus> stats : session.getConsentStatuses().entrySet()) {
            statuses.set(stats.getKey().getGuid(), MAPPER.valueToTree(stats.getValue()));
        }
        ObjectNode partNode = (ObjectNode)MAPPER.valueToTree(session.getParticipant());
        for (Iterator<String> i = partNode.fieldNames(); i.hasNext();) {
            String fieldName = i.next();
            
            if (!PROHIBITED_FIELD_NAMES.contains(fieldName)) {
                JsonNode child = partNode.get(fieldName);
                node.set(fieldName, child);
            }
        }
        node.put(TYPE, USER_SESSION_INFO);

        return node;
    }
    
    private static void addNotNull(ObjectNode node, String fieldName, String value) {
        if (value != null) {
            node.put(fieldName, value);
        }
    }
}
