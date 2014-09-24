package org.sagebionetworks.bridge.events;

import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.springframework.context.ApplicationEvent;

public class SchedulePlanDeletedEvent extends ApplicationEvent {
    private static final long serialVersionUID = 438296915233315318L;

    public SchedulePlanDeletedEvent(Object source) {
        super(source);
    }

    public SchedulePlan getSchedulePlan() {
        return (SchedulePlan)getSource();
    }
    
}
