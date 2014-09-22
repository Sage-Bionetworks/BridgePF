package org.sagebionetworks.bridge.models.schedules;

import java.util.List;

import org.sagebionetworks.bridge.validators.Messages;

import com.fasterxml.jackson.databind.node.ObjectNode;

public interface ScheduleStrategy {

    public void initialize(ObjectNode node);
    
    public void persist(ObjectNode node);
    
    public List<Schedule> generateSchedules(ScheduleContext context);
    
    public void validate(Messages messages);

}
