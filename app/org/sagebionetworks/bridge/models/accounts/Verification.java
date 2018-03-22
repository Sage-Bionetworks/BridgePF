package org.sagebionetworks.bridge.models.accounts;

import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Verification implements BridgeEntity {

    private final String sptoken;

    public Verification(@JsonProperty("sptoken") String sptoken) {
        this.sptoken = sptoken;
    }

    public String getSptoken() {
        return sptoken;
    }

}
