package org.sagebionetworks.bridge.models.schedules;

import java.util.Objects;

import static com.google.common.base.Preconditions.checkNotNull;

import org.joda.time.DateTime;

public final class Task {

    private final String guid;
    private final String schedulePlanGuid;
    private final Activity activity;
    private final DateTime startsOn;
    private final DateTime endsOn;
    
    Task(String guid, String schedulePlanGuid, Activity activity, DateTime startsOn, DateTime endsOn) {
        checkNotNull(guid);
        checkNotNull(schedulePlanGuid);
        checkNotNull(activity);
        checkNotNull(startsOn);
        // endsOn is okay to be null
        this.guid = guid;
        this.schedulePlanGuid = schedulePlanGuid;
        this.activity = activity;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
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
    
    public DateTime getStartsOn() {
        return startsOn;
    }

    public DateTime getEndsOn() {
        return endsOn;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Objects.hashCode(activity);
        result = prime * result + Objects.hashCode(guid);
        result = prime * result + Objects.hashCode(schedulePlanGuid);
        result = prime * result + Objects.hashCode(startsOn);
        result = prime * result + Objects.hashCode(endsOn);
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
            && Objects.equals(startsOn, other.startsOn) && Objects.equals(endsOn, other.endsOn));
    }
    
    @Override
    public String toString() {
        return String.format("Task [guid=%s, activity=%s, schedulePlanGuid=%s, startsOn=%s, endsOn=%s]", 
            guid, activity, schedulePlanGuid, startsOn, endsOn);
    }
}
