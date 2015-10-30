package org.sagebionetworks.bridge.models.accounts;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request to withdraw from the current study.
 *
 */
public final class Withdrawal {

    private final String reason;
    
    @JsonCreator
    public Withdrawal(@JsonProperty("reason") String reason) {
        this.reason = reason;
    }
    
    public String getReason() {
        return reason;
    }
    
}
