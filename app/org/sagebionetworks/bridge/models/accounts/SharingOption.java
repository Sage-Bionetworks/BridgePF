package org.sagebionetworks.bridge.models.accounts;

import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.JsonUtils;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * SharingOption deserializes from JSON, validates, and defaults to the right value for the version 
 * of the call being made.
 */
public class SharingOption {
    
    private static final String SCOPE_FIELD = "scope";
    
    private final SharingScope sharingScope;
    
    SharingOption(SharingScope scope) {
        this.sharingScope = scope;
    }
    
    public static final SharingOption fromJson(JsonNode node, int version) {
        if (version == 1) {
            return new SharingOption(SharingScope.NO_SHARING);
        }
        try {
            String scope = JsonUtils.asText(node, SCOPE_FIELD);
            if (scope != null) {
                return new SharingOption(SharingScope.valueOf(scope.toUpperCase()));
            }                
        } catch(Exception e) {
        }
        throw new InvalidEntityException("ConsentSignature: "+SCOPE_FIELD+" is required.");
    }

    public SharingScope getSharingScope() {
        return sharingScope;
    }

}
