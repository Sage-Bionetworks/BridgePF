package org.sagebionetworks.bridge.models.accounts;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Greatly trimmed user session object that is embedded in the initial render of the
 * web application.
 *
 */
public class UserSessionInfo {
    
    private static final String CONSENT_HISTORIES = "consentHistories";
    private static final String STUDY_PARTICIPANT = "studyParticipant";
    private static final String TYPE = "type";

    /**
     * Collapse StudyParticipant properties into the UserSessionInfo JSON so that we 
     * do not need to copy these info the session when we update StudyParticipant. 
     * Unfortunately this makes for some gross JSON processing. This can be moved 
     * to a deserializer. 
     */
    public static JsonNode toJSON(UserSession session) {
        UserSessionInfo info = new UserSessionInfo(session);
        ObjectNode node = (ObjectNode)BridgeObjectMapper.get().valueToTree(info);
        if (node.has(STUDY_PARTICIPANT)) {
            ObjectNode partNode = (ObjectNode)node.get(STUDY_PARTICIPANT);    
            node.remove(STUDY_PARTICIPANT);
            for (Iterator<String> i = partNode.fieldNames(); i.hasNext();) {
                String fieldName = i.next();
                JsonNode child = partNode.get(fieldName);
                
                if (TYPE.equals(fieldName)) {
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
            node.remove(CONSENT_HISTORIES);
        }
        if (node.has("email")) {
            node.put("username", node.get("email").asText());    
        }
        return node;
    }
    
    private static final Map<Environment,String> ENVIRONMENTS = new HashMap<>();
    static {
        ENVIRONMENTS.put(Environment.LOCAL, "local");
        ENVIRONMENTS.put(Environment.DEV, "develop");
        ENVIRONMENTS.put(Environment.UAT, "staging");
        ENVIRONMENTS.put(Environment.PROD, "production");
    }

    private final boolean authenticated;
    private final String sessionToken;
    private final String environment;
    private final Map<SubpopulationGuid,ConsentStatus> consentStatuses;
    private StudyParticipant studyParticipant;

    private UserSessionInfo(UserSession session) {
        this.authenticated = session.isAuthenticated();
        this.sessionToken = session.getSessionToken();
        this.environment = ENVIRONMENTS.get(session.getEnvironment());
        this.consentStatuses = session.getConsentStatuses();
        this.studyParticipant = session.getStudyParticipant();
    }

    public StudyParticipant getStudyParticipant() {
        return studyParticipant;
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
    public String getSessionToken() {
        return sessionToken;
    }
    public String getEnvironment() {
        return environment;
    }
    public boolean getDataSharing() {
        return studyParticipant.getSharingScope() != SharingScope.NO_SHARING;
    }
}
