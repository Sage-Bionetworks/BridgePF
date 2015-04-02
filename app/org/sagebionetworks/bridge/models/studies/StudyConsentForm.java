package org.sagebionetworks.bridge.models.studies;

import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.annotation.JsonProperty;

@BridgeTypeName("StudyConsent")
public class StudyConsentForm implements BridgeEntity {

    private final String path;
    private final int minAge;

    public StudyConsentForm(@JsonProperty("path") String path, @JsonProperty("minAge") int minAge) {
        this.path = path;
        this.minAge = minAge;
    }

    public String getPath() {
        return path;
    }

    public int getMinAge() {
        return minAge;
    }
}
