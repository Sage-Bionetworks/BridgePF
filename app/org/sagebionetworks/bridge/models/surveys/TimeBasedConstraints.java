package org.sagebionetworks.bridge.models.surveys;

import org.sagebionetworks.bridge.json.DateTimeJsonDeserializer;
import org.sagebionetworks.bridge.json.DateTimeJsonSerializer;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

public abstract class TimeBasedConstraints extends Constraints {
    
    protected boolean allowFuture = false;
    protected Long earliestValue;
    protected Long latestValue;
    
    public boolean getAllowFuture() {
        return allowFuture;
    }
    public void setAllowFuture(boolean allowFuture) {
        this.allowFuture = allowFuture;
    }
    @JsonSerialize(using = DateTimeJsonSerializer.class)
    public Long getEarliestValue() {
        return earliestValue;
    }
    @JsonDeserialize(using = DateTimeJsonDeserializer.class)
    public void setEarliestValue(Long earliestValue) {
        this.earliestValue = earliestValue;
    }
    @JsonSerialize(using = DateTimeJsonSerializer.class)
    public Long getLatestValue() {
        return latestValue;
    }
    @JsonDeserialize(using = DateTimeJsonDeserializer.class)
    public void setLatestValue(Long latestValue) {
        this.latestValue = latestValue;
    }
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + (allowFuture ? 1231 : 1237);
        result = prime * result + ((earliestValue == null) ? 0 : earliestValue.hashCode());
        result = prime * result + ((latestValue == null) ? 0 : latestValue.hashCode());
        return result;
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        TimeBasedConstraints other = (TimeBasedConstraints) obj;
        if (allowFuture != other.allowFuture)
            return false;
        if (earliestValue == null) {
            if (other.earliestValue != null)
                return false;
        } else if (!earliestValue.equals(other.earliestValue))
            return false;
        if (latestValue == null) {
            if (other.latestValue != null)
                return false;
        } else if (!latestValue.equals(other.latestValue))
            return false;
        return true;
    }
}
