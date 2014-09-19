package org.sagebionetworks.bridge.models.surveys;

import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.validators.Messages;

import com.fasterxml.jackson.annotation.JsonInclude;

public abstract class TimeBasedConstraints extends Constraints {
    
    private static final long FIVE_MINUTES = 5 * 60 * 1000;
    
    protected boolean allowFuture = false;
    
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public boolean getAllowFuture() {
        return allowFuture;
    }
    public void setAllowFuture(boolean allowFuture) {
        this.allowFuture = allowFuture;
    }
    
    public void validate(Messages messages, SurveyAnswer answer) {
        long time = (Long)answer.getAnswer();
        // add 5 minutes of leniency to this test because different machines may 
        // report different times, we're really trying to catch user input at a 
        // coarser level of time reporting than milliseconds.
        if (!allowFuture && time > (DateUtils.getCurrentMillisFromEpoch()+FIVE_MINUTES)) {
            messages.add("it is not allowed to have a future date value");
        }
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + (allowFuture ? 1231 : 1237);
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
        return true;
    }

}
