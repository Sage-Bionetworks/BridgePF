package org.sagebionetworks.bridge.models.accounts;

import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.annotation.JsonProperty;

public class VerificationToken implements BridgeEntity {

    private final String token;

    public VerificationToken(@JsonProperty("sptoken") String sptoken, @JsonProperty("token") String token) {
        this.token = (token == null && sptoken != null) ? sptoken : token;
    }

    public String getToken() {
        return token;
    }

}
