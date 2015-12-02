package org.sagebionetworks.bridge.models.accounts;

import java.util.Objects;

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

    @Override
    public int hashCode() {
        return Objects.hash(identifier);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (this == obj || obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ExternalIdentifier other = (ExternalIdentifier) obj;
        return Objects.equals(identifier, other.identifier);
    }
    
}
