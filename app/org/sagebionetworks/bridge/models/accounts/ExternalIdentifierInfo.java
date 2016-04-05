package org.sagebionetworks.bridge.models.accounts;

import java.util.Objects;

import org.sagebionetworks.bridge.json.BridgeTypeName;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * We expose only limited information about this entity.
 */
@BridgeTypeName("ExternalIdentifier")
public final class ExternalIdentifierInfo {

    private final String identifier;
    private final boolean isAssigned;

    @JsonCreator
    public ExternalIdentifierInfo(@JsonProperty("identifier") String identifier, @JsonProperty("assigned") boolean isAssigned) {
        this.identifier = identifier;
        this.isAssigned = isAssigned;
    }
    
    public String getIdentifier() {
        return identifier;
    }

    public boolean isAssigned() {
        return isAssigned;
    }

    @Override
    public int hashCode() {
        return Objects.hash(identifier, isAssigned);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        ExternalIdentifierInfo other = (ExternalIdentifierInfo) obj;
        return Objects.equals(identifier, other.identifier) && Objects.equals(isAssigned, other.isAssigned);
    }

    @Override
    public String toString() {
        return "ExternalIdentifierInfo [identifier=" + identifier + ", isAssigned=" + isAssigned + "]";
    }
}
