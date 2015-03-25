package org.sagebionetworks.bridge.models.schedules;

import org.joda.time.DateTime;

public class Task {

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
    
}
