package org.sagebionetworks.bridge.models.schedules;

import java.util.Objects;

import static com.google.common.base.Preconditions.checkNotNull;

import org.joda.time.DateTime;

public final class Task {

    private final String guid;
    private final String schedulePlanGuid;
    private final Activity activity;
    private final DateTime scheduledOn;
    private final DateTime expiresOn;
    
    Task(String guid, String schedulePlanGuid, Activity activity, DateTime scheduledOn, DateTime expiresOn) {
        checkNotNull(guid);
        checkNotNull(schedulePlanGuid);
        checkNotNull(activity);
        checkNotNull(scheduledOn);
        // expiresOn can be null
        this.guid = guid;
        this.schedulePlanGuid = schedulePlanGuid;
        this.activity = activity;
        this.scheduledOn = scheduledOn;
        this.expiresOn = expiresOn;
    }
    
    public String getGuid() {
        return guid;
    }
    
    public String getSchedulePlanGuid() {
        return schedulePlanGuid;
    }
    
    public Activity getActivity() {
        return activity;
    }
    
    public DateTime getScheduledOn() {
        return scheduledOn;
    }

    public DateTime getExpiresOn() {
        return expiresOn;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Objects.hashCode(activity);
        result = prime * result + Objects.hashCode(guid);
        result = prime * result + Objects.hashCode(schedulePlanGuid);
        result = prime * result + Objects.hashCode(scheduledOn);
        result = prime * result + Objects.hashCode(expiresOn);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        Task other = (Task) obj;
        return (Objects.equals(activity, other.activity) && Objects.equals(guid, other.guid)
            && Objects.equals(schedulePlanGuid, other.schedulePlanGuid)
            && Objects.equals(scheduledOn, other.scheduledOn) && Objects.equals(expiresOn, other.expiresOn));
    }
    
    @Override
    public String toString() {
        return String.format("Task [guid=%s, activity=%s, schedulePlanGuid=%s, scheduledOn=%s, expiresOn=%s]", 
            guid, activity, schedulePlanGuid, scheduledOn, expiresOn);
    }
}
