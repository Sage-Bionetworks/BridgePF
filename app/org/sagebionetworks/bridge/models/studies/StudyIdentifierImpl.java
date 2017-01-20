package org.sagebionetworks.bridge.models.studies;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.sagebionetworks.bridge.exceptions.BadRequestException;

public final class StudyIdentifierImpl implements StudyIdentifier {

    private final String identifier;
    
    @JsonCreator
    public StudyIdentifierImpl(@JsonProperty("identifier") String identifier) {
        if (isBlank(identifier)) {
            throw new BadRequestException("Study Id is invalid.");
        }
        this.identifier = identifier;
    }
    
    public String getIdentifier() {
        return identifier;
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
        return String.format("StudyIdentifierImpl [identifier=%s]", identifier);
    }
}
