package org.sagebionetworks.bridge.models.studies;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class StudyIdentifierImpl implements StudyIdentifier {

    private final String identifier;
    
    @JsonCreator
    public StudyIdentifierImpl(@JsonProperty("identifier") String identifier) {
        this.identifier = identifier;
    }
    
    public String getIdentifier() {
        return identifier;
    }
    
    public String getResearcherRole() {
        return identifier + "_researcher";
    }

    @Override
    public String toString() {
        return String.format("StudyIdentifierImpl [identifier=%s]", identifier);
    }
}
