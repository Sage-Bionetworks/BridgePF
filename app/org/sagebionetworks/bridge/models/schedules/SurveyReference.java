package org.sagebionetworks.bridge.models.schedules;

import org.sagebionetworks.bridge.json.BridgeTypeName;

/**
 * This is a "soft" reference to a survey that does not need to include a createdOn timestamp. 
 * It can be used to create JSON for published versions of surveys as well as hard references 
 * to a specific version.
 */
@BridgeTypeName("GuidCreatedOnVersionHolder")
public class SurveyReference {

    private final String guid;
    private final String createdOn;
    
    public SurveyReference(String ref) {
        String[] parts = ref.split("/surveys/")[1].split("/");
        this.guid = parts[0];
        this.createdOn = "published".equals(parts[1]) ? null : parts[1];
    }

    public String getGuid() {
        return guid;
    }

    public String getCreatedOn() {
        return createdOn;
    }
    
}
