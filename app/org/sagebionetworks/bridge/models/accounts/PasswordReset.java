package org.sagebionetworks.bridge.models.accounts;

import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PasswordReset implements BridgeEntity {

    private final String password;
    private final String sptoken;
    private final String studyIdentifier;
    
    public PasswordReset(@JsonProperty("password") String password, @JsonProperty("sptoken") String sptoken,
            @JsonProperty("study") String studyId) {
        this.password = password;
        this.sptoken = sptoken;
        this.studyIdentifier = studyId;
    }
    
    public String getStudyIdentifier() {
        return studyIdentifier;
    }
    
    public String getPassword() {
        return password;
    }

    public String getSptoken() {
        return sptoken;
    }

}
