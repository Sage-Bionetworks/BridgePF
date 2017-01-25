package org.sagebionetworks.bridge.models.schedules;

import java.util.Objects;

import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * This is a "soft" reference to a survey that does not need to include a createdOn timestamp. 
 * It can be used to create JSON for published versions of surveys as well as hard references 
 * to a specific version.
 */
public final class SurveyReference {

    private static final String BASE_URL = BridgeConfigFactory.getConfig().getWebservicesURL() + "/v3/surveys/";
    
    private final String identifier;
    private final String guid;
    private final DateTime createdOn;
    
    @JsonCreator
    public SurveyReference(@JsonProperty("identifier") String identifier, @JsonProperty("guid") String guid,
                    @JsonProperty("createdOn") DateTime createdOn) {
        this.identifier = identifier;
        this.guid = guid;
        this.createdOn = (createdOn == null) ? null : createdOn;
    }

    public String getIdentifier() {
        return identifier;
    }
    public String getGuid() {
        return guid;
    }
    public DateTime getCreatedOn() {
        return createdOn;
    }
    public String getHref() {
        if (createdOn == null) {
            return BASE_URL + guid + "/revisions/published";
        }
        return BASE_URL + guid + "/revisions/" + createdOn.toString(ISODateTimeFormat.dateTime());
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Objects.hashCode(createdOn);
        result = prime * result + Objects.hashCode(guid);
        result = prime * result + Objects.hashCode(identifier);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        SurveyReference other = (SurveyReference) obj;
        return (Objects.equals(createdOn, other.createdOn) && Objects.equals(guid, other.guid) &&
            Objects.equals(identifier, other.identifier));
    }

    @Override
    public String toString() {
        return String.format("SurveyReference [identifier=%s, guid=%s, createdOn=%s, href=%s]",
            identifier, guid, createdOn, getHref());
    }
}
