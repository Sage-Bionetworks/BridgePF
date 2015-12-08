package org.sagebionetworks.bridge.models.accounts;

import java.util.Objects;

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

    @Override
    public int hashCode() {
        return Objects.hash(reason);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        Withdrawal other = (Withdrawal) obj;
        return (Objects.equals(reason, other.reason));
    }

    @Override
    public String toString() {
        return "Withdrawal [reason=" + reason + "]";
    }
    
}
