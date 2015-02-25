package org.sagebionetworks.bridge.models;

import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.JsonUtils;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * SharingOption deserializes from JSON, validates, and defaults to the right value for the version 
 * of the call being made.
 */
public class SharingOption {
    
    private static final String SCOPE_FIELD = "scope";
    
    private final ParticipantOption.ScopeOfSharing scope;
    
    private SharingOption(ParticipantOption.ScopeOfSharing scope) {
        this.scope = scope;
    }
    
    public static final SharingOption fromJson(JsonNode node, int version) {
        if (version == 1) {
            return new SharingOption(ParticipantOption.ScopeOfSharing.NO_SHARING);
        }
        try {
            String scopeOfConsent = JsonUtils.asText(node, SCOPE_FIELD);
            if (scopeOfConsent != null) {
                return new SharingOption(ParticipantOption.ScopeOfSharing.valueOf(scopeOfConsent.toUpperCase()));
            }                
        } catch(Exception e) {
        }
        throw new InvalidEntityException("ConsentSignature: "+SCOPE_FIELD+" is required.");
    }

    public ParticipantOption.ScopeOfSharing getScopeOfSharing() {
        return scope;
    }

}
