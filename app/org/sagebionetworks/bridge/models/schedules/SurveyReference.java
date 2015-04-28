package org.sagebionetworks.bridge.models.schedules;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;
import org.sagebionetworks.bridge.models.surveys.Survey;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * This is a "soft" reference to a survey that does not need to include a createdOn timestamp. 
 * It can be used to create JSON for published versions of surveys as well as hard references 
 * to a specific version.
 */
@BridgeTypeName("GuidCreatedOnVersionHolder")
public class SurveyReference {

    public static final String SURVEY_RESPONSE_PATH_FRAGMENT = "/surveys/response/";
    public static final String SURVEY_PATH_FRAGMENT = "/surveys/";
    public static final String PUBLISHED_FRAGMENT = "published";
    
    public static final boolean isSurveyRef(String ref) {
        return (ref != null && ref.contains(SURVEY_PATH_FRAGMENT));
    }
    
    private final String guid;
    private final String createdOn;
    
    public SurveyReference(Survey survey) {
        this.guid = survey.getGuid();
        this.createdOn = new DateTime(survey.getCreatedOn()).toString();
    }
    
    public SurveyReference(String ref) {
        String[] parts = ref.split(SURVEY_PATH_FRAGMENT)[1].split("/");
        this.guid = parts[0];
        this.createdOn = PUBLISHED_FRAGMENT.equals(parts[1]) ? null : parts[1];
    }
    
    public String getGuid() {
        return guid;
    }

    public String getCreatedOn() {
        return createdOn;
    }
    
    @JsonIgnore
    public DateTime getCreatedOnTimestamp() {
        return (createdOn == null) ? null : DateTime.parse(createdOn);
    }
    
    @JsonIgnore
    public GuidCreatedOnVersionHolder getGuidCreatedOnVersionHolder() {
        if (createdOn == null) {
            return null;
        }
        return new GuidCreatedOnVersionHolderImpl(guid, getCreatedOnTimestamp().getMillis());
    }
    
    public boolean isPublishedReference() {
        return (createdOn == null);
    }
}
