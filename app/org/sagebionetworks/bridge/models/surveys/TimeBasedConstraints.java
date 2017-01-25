package org.sagebionetworks.bridge.models.surveys;

public abstract class TimeBasedConstraints extends Constraints {
    
    protected boolean allowFuture = false;
    
    public boolean getAllowFuture() {
        return allowFuture;
    }
    public void setAllowFuture(boolean allowFuture) {
        this.allowFuture = allowFuture;
    }
}
