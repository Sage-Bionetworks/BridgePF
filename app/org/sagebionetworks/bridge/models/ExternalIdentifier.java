package org.sagebionetworks.bridge.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public final class ExternalIdentifier implements BridgeEntity {

    private final String identifier;

    public ExternalIdentifier(@JsonProperty("identifier") String identifier) {
        this.identifier = identifier;
    }
    
    public String getIdentifier() {
        return identifier;
    }
    
}
