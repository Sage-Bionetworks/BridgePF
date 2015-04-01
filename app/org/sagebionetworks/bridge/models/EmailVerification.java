package org.sagebionetworks.bridge.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public class EmailVerification {

    private final String sptoken;

    public EmailVerification(@JsonProperty("sptoken") String sptoken) {
        this.sptoken = sptoken;
    }

    public String getSptoken() {
        return sptoken;
    }

}
