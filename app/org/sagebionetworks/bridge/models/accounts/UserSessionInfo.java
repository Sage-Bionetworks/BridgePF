package org.sagebionetworks.bridge.models.accounts;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
    private static final String CONSENT_STATUSES = "consentStatuses";
    private static final String CONSENTED = "consented";
    private static final String SIGNED_MOST_RECENT_CONSENT = "signedMostRecentConsent";
    private static final String DATA_SHARING = "dataSharing";
    private static final String ENVIRONMENT = "environment";
    private static final String SESSION_TOKEN = "sessionToken";
    private static final String AUTHENTICATED = "authenticated";
    private static final String USER_SESSION_INFO = "UserSessionInfo";
    private static final String TYPE = "type";
    
    private static final String PRODUCTION = "production";
    private static final String STAGING = "staging";
    private static final String DEVELOP = "develop";
    private static final String LOCAL = "local";
    
    private static final BridgeObjectMapper MAPPER = BridgeObjectMapper.get();
    private static final Set<String> PROHIBITED_FIELD_NAMES = Sets.newHashSet(TYPE, ENCRYPTED_HEALTH_CODE, CONSENT_HISTORIES);
    private static final String USERNAME = "username";
    private static final Map<Environment,String> ENVIRONMENTS = new ImmutableMap.Builder<Environment,String>()
            .put(Environment.LOCAL, LOCAL)
            .put(Environment.DEV, DEVELOP)
            .put(Environment.UAT, STAGING)
            .put(Environment.PROD, PRODUCTION).build();

    /**
     * Collapse StudyParticipant properties into the UserSessionInfo JSON so that we 
     * do not need to copy these info the session when we update StudyParticipant. 
     * Unfortunately this makes for some gross JSON processing. This can be moved 
     * to a deserializer. 
     */
    public static JsonNode toJSON(UserSession session) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put(AUTHENTICATED, session.isAuthenticated());
        node.put(SESSION_TOKEN, session.getSessionToken());
        node.put(ENVIRONMENT, ENVIRONMENTS.get(session.getEnvironment()));
        node.put(DATA_SHARING, session.getParticipant().getSharingScope() != SharingScope.NO_SHARING);
        node.put(USERNAME, session.getParticipant().getEmail());
        node.put(SIGNED_MOST_RECENT_CONSENT, ConsentStatus.isConsentCurrent(session.getConsentStatuses()));
        node.put(CONSENTED, ConsentStatus.isUserConsented(session.getConsentStatuses()));
        node.put(TYPE, USER_SESSION_INFO);
        
        ObjectNode statuses = node.with(CONSENT_STATUSES);
        for (Map.Entry<SubpopulationGuid, ConsentStatus> stats : session.getConsentStatuses().entrySet()) {
            statuses.set(stats.getKey().getGuid(), MAPPER.valueToTree(stats.getValue()));
        }
        ObjectNode partNode = (ObjectNode)MAPPER.valueToTree(session.getParticipant());
        for (Iterator<String> i = partNode.fieldNames(); i.hasNext();) {
            String fieldName = i.next();
            JsonNode child = partNode.get(fieldName);
            
            if (PROHIBITED_FIELD_NAMES.contains(fieldName)) {
                // do nothing
            } else if (child.isTextual()) {
                node.put(fieldName, child.asText());    
            } else if (child.isBoolean()) {
                node.put(fieldName, child.asBoolean());
            } else if (child.isArray()) {
                node.putArray(fieldName).addAll((ArrayNode)child);
            } else if (child.isObject()) {
                node.putPOJO(fieldName, child);
            }
        }
        return node;
    }
}
