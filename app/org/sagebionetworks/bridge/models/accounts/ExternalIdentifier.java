package org.sagebionetworks.bridge.models.accounts;

import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.annotation.JsonProperty;

public final class ExternalIdentifier implements BridgeEntity {

    private final String identifier;

    public ExternalIdentifier(@JsonProperty("identifier") String identifier) {
        this.identifier = identifier;
    }
    
    @JsonProperty("externalId")
    public String getIdentifier() {
        return identifier;
    }
    
}
