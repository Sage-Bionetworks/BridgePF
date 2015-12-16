package org.sagebionetworks.bridge.models.subpopulations;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Objects;

public final class SubpopulationGuid {
    
    public static SubpopulationGuid create(String guid) {
        return new SubpopulationGuid(guid);
    }
    
    private final String guid;
    
    private SubpopulationGuid(String guid) {
        checkNotNull(guid);
        this.guid = guid;
    }
    public String getGuid() {
        return guid;
    }
    public int hashCode() {
        return Objects.hash(guid);
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        SubpopulationGuid other = (SubpopulationGuid) obj;
        return Objects.equals(guid, other.guid);
    }
    @Override
    public String toString() {
        return guid;
    }
}
