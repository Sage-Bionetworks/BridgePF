package org.sagebionetworks.bridge.models.studies;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class StudyIdentifierImpl implements StudyIdentifier {

    private final String identifier;
    
    @JsonCreator
    public StudyIdentifierImpl(@JsonProperty("identifier") String identifier) {
        checkNotNull(identifier);
        this.identifier = identifier;
    }
    
    public String getIdentifier() {
        return identifier;
    }
    
    public String getResearcherRole() {
        return identifier + "_researcher";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Objects.hash(identifier);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        StudyIdentifierImpl other = (StudyIdentifierImpl) obj;
        return (Objects.equals(identifier, other.identifier));
    }

    @Override
    public String toString() {
        return String.format("StudyIdentifierImpl [identifier=%s, researcherRole=%s]", 
            getIdentifier(), getResearcherRole());
    }
}
