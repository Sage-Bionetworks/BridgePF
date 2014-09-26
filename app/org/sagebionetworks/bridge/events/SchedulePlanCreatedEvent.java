package org.sagebionetworks.bridge.events;

import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.springframework.context.ApplicationEvent;

public class SchedulePlanCreatedEvent extends ApplicationEvent {
    private static final long serialVersionUID = -878109578367198182L;

    public SchedulePlanCreatedEvent(Object source) {
        super(source);
    }

    public SchedulePlan getSchedulePlan() {
        return (SchedulePlan)getSource();
    }
    
}
