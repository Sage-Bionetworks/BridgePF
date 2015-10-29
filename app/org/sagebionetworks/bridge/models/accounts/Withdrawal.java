package org.sagebionetworks.bridge.models.accounts;

import org.joda.time.DateTime;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request to withdraw from the current study.
 *
 */
public final class Withdrawal {

    private final String reason;
    private final long withdrewOn;
    
    @JsonCreator
    public Withdrawal(@JsonProperty("reason") String reason) {
        this(reason, DateTime.now().getMillis());
    }
    
    public Withdrawal(String reason, long withdrewOn) {
        this.reason = reason;
        this.withdrewOn = withdrewOn;
    }
    
    public String getReason() {
        return reason;
    }
    
    public long getWithdrewOn() {
        return withdrewOn;
    }
    
}
