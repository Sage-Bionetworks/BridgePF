package org.sagebionetworks.bridge.models;

import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.json.JsonUtils;

import com.fasterxml.jackson.databind.JsonNode;

@BridgeTypeName("StudyConsent")
public class StudyConsentForm implements BridgeEntity {

    private final String path;
    private final int minAge;

    private static final String PATH_FIELD = "path";
    private static final String MIN_AGE_FIELD = "minAge";

    public StudyConsentForm(String path, int minAge) {
        this.path = path;
        this.minAge = minAge;
    }

    public static final StudyConsentForm fromJson(JsonNode node) {
        String path = JsonUtils.asText(node, PATH_FIELD);
        int minAge = JsonUtils.asIntPrimitive(node, MIN_AGE_FIELD); 
        return new StudyConsentForm(path, minAge);
    }

    public String getPath() {
        return path;
    }

    public int getMinAge() {
        return minAge;
    }
}
