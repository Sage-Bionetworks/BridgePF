package org.sagebionetworks.bridge.models.schedules;

import java.util.Objects;

import org.sagebionetworks.bridge.config.BridgeConfigFactory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class SurveyResponseReference {

    private static final String BASE_URL = BridgeConfigFactory.getConfig().getWebservicesURL() + "/v3/surveyresponses/";
    
    private final String identifier;
    
    @JsonCreator
    SurveyResponseReference(@JsonProperty("identifier") String identifier) {
        this.identifier = identifier;
    }
    
    public String getIdentifier() {
        return identifier;
    }
    
    public String getHref() {
        return BASE_URL + identifier;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Objects.hashCode(identifier);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        SurveyResponseReference other = (SurveyResponseReference) obj;
        return (Objects.equals(identifier, other.identifier));
    }

    @Override
    public String toString() {
        return "SurveyResponseReference [identifier=" + identifier + ", href=" + getHref() + "]";
    }

}
