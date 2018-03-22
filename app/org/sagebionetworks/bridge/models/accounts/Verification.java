package org.sagebionetworks.bridge.models.accounts;

import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * This class was originally named EmailVerification. Because it is not sent to the client, 
 * only used to deserialize a request payload, this class name change does not effect clients.
 */
public class Verification implements BridgeEntity {

    private final String sptoken;

    public Verification(@JsonProperty("sptoken") String sptoken) {
        this.sptoken = sptoken;
    }

    public String getSptoken() {
        return sptoken;
    }

}
