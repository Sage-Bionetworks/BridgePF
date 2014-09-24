package org.sagebionetworks.bridge.events;

import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.springframework.context.ApplicationEvent;

public class SchedulePlanUpdatedEvent extends ApplicationEvent {
    private static final long serialVersionUID = -8843536950708762028L;

    public SchedulePlanUpdatedEvent(Object source) {
        super(source);
    }

    public SchedulePlan getSchedulePlan() {
        return (SchedulePlan)getSource();
    }
    
}
