package org.sagebionetworks.bridge.models.accounts;

import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stormpath.sdk.lang.Objects;

public final class ExternalIdentifier implements BridgeEntity {

    private final String identifier;

    public ExternalIdentifier(@JsonProperty("identifier") String identifier) {
        this.identifier = identifier;
    }
    
    public String getIdentifier() {
        return identifier;
    }

    @Override
    public int hashCode() {
        return identifier.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj || obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ExternalIdentifier other = (ExternalIdentifier) obj;
        return Objects.nullSafeEquals(identifier, other.identifier);
    }
    
}
