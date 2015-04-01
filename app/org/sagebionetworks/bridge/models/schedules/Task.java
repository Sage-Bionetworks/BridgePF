package org.sagebionetworks.bridge.models.schedules;

import java.util.Objects;

import org.joda.time.DateTime;

public final class Task {

    private final String guid;
    private final Activity activity;
    private final String eventId;
    private final DateTime startsOn;
    private final DateTime endsOn;
    
    Task(String guid, Activity activity, String eventId, DateTime startsOn, DateTime endsOn) {
        this.guid = guid;
        this.activity = activity;
        this.eventId = eventId;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
    }
    
    public String getGuid() {
        return guid;
    }
    
    public Activity getActivity() {
        return activity;
    }
    
    public String getEventId() {
        return eventId;
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
        result = prime * result + Objects.hashCode(startsOn);
        result = prime * result + Objects.hashCode(endsOn);
        result = prime * result + Objects.hashCode(eventId);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        Task other = (Task) obj;
        return (Objects.equals(startsOn, other.startsOn) && Objects.equals(endsOn, other.endsOn) 
                && Objects.equals(eventId, other.eventId));
    }
    
    @Override
    public String toString() {
        return String.format("Task [startsOn=%s, endsOn=%s, eventId=%s]", startsOn, endsOn, eventId);
    }
}
