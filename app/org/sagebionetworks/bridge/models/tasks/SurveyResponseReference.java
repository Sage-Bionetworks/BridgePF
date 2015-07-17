package org.sagebionetworks.bridge.models.tasks;

import java.util.Objects;

import org.sagebionetworks.bridge.config.BridgeConfigFactory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class SurveyResponseReference {

    private static final String BASE_URL = BridgeConfigFactory.getConfig().getBaseURL() + "/v3/surveyresponses/";
    
    private final String guid;
    
    @JsonCreator
    public SurveyResponseReference(@JsonProperty("guid") String guid) {
        this.guid = guid;
    }
    
    public String getGuid() {
        return guid;
    }
    
    public String getHref() {
        return BASE_URL + guid;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Objects.hashCode(guid);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        SurveyResponseReference other = (SurveyResponseReference) obj;
        return (Objects.equals(guid, other.guid));
    }

    @Override
    public String toString() {
        return "SurveyResponseReference [guid=" + guid + ", href=" + getHref() + "]";
    }

}
