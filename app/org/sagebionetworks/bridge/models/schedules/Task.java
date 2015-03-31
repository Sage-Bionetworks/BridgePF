package org.sagebionetworks.bridge.models.schedules;

import java.util.Objects;

import org.joda.time.DateTime;

public final class Task {

    private final DateTime startsOn;
    private final DateTime endsOn;
    
    public Task(DateTime startsOn, DateTime endsOn) {
        this.startsOn = startsOn;
        this.endsOn = endsOn;
    }

    public Task(DateTime startsOn) {
        this.startsOn = startsOn;
        this.endsOn = null;
    }
    
    public Task(Task task, DateTime endsOn) {
        this.startsOn = task.startsOn;
        this.endsOn = endsOn;
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
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        Task other = (Task) obj;
        return (Objects.equals(startsOn, other.startsOn) && Objects.equals(endsOn, other.endsOn));
    }
    
    @Override
    public String toString() {
        return String.format("Task [startsOn=%s, endsOn=%s]", startsOn, endsOn);
    }
}
