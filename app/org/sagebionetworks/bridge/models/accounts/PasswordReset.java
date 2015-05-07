package org.sagebionetworks.bridge.models.accounts;

import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PasswordReset implements BridgeEntity {

    private final String password;
    private final String sptoken;
    
    public PasswordReset(@JsonProperty("password") String password, @JsonProperty("sptoken") String sptoken) {
        this.password = password;
        this.sptoken = sptoken;
    }
    
    public String getPassword() {
        return password;
    }

    public String getSptoken() {
        return sptoken;
    }

}
