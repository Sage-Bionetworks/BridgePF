package org.sagebionetworks.bridge.models;

import org.sagebionetworks.bridge.json.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;

public final class ExternalIdentifier implements BridgeEntity {

    private static final String IDENTIFIER_PROPERTY = "identifier";
    
    private final String identifier;
    
    public ExternalIdentifier(String identifier) {
        this.identifier = identifier;
    }
    
    public static ExternalIdentifier fromJson(JsonNode node) {
        String identifier = JsonUtils.asText(node, IDENTIFIER_PROPERTY);
        return new ExternalIdentifier(identifier);
    }
    
    public String getIdentifier() {
        return identifier;
    }
    
}
