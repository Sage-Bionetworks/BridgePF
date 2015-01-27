package org.sagebionetworks.bridge.models.schedules;

import org.sagebionetworks.bridge.json.BridgeTypeName;

/**
 * This is a "soft" reference to a survey that does not need to include a createdOn timestamp. 
 * It can be used to create JSON for published versions of surveys as well as hard references 
 * to a specific version.
 */
@BridgeTypeName("GuidCreatedOnVersionHolder")
public class SurveyReference {

    private static final String SURVEY_PATH_FRAGMENT = "/surveys/";
    private static final String PUBLISHED_FRAGMENT = "published";
    
    public static final boolean isSurveyRef(String ref) {
        return (ref != null && ref.contains(SURVEY_PATH_FRAGMENT));
    }
    
    private final String guid;
    private final String createdOn;
    
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
    
}
