package org.sagebionetworks.bridge.models.subpopulations;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Objects;

public final class SubpopulationGuidImpl implements SubpopulationGuid {
    private final String guid;
    
    SubpopulationGuidImpl(String guid) {
        checkNotNull(guid);
        this.guid = guid;
    }
    @Override
    public String getGuid() {
        return guid;
    }
    @Override
    public int hashCode() {
        return Objects.hash(guid);
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        SubpopulationGuidImpl other = (SubpopulationGuidImpl) obj;
        return Objects.equals(guid, other.guid);
    }
    @Override
    public String toString() {
        return guid;
    }

}
