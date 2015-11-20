package org.sagebionetworks.bridge.models.accounts;

import java.util.Objects;
import java.util.Set;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class DataGroups implements BridgeEntity {

    private final Set<String> dataGroups;
    
    @JsonCreator
    public DataGroups(@JsonProperty("dataGroups") Set<String> dataGroups) {
        this.dataGroups = BridgeUtils.nullSafeImmutableSet(dataGroups);
    }
    
    public Set<String> getDataGroups() {
        return dataGroups;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(dataGroups);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        DataGroups other = (DataGroups) obj;
        return Objects.equals(dataGroups, other.dataGroups);
    }

    @Override
    public String toString() {
        return "DataGroups [dataGroups=" + dataGroups + "]";
    }
    
}
