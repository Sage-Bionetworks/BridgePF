package org.sagebionetworks.bridge.models.schedules;

import java.util.List;

import org.sagebionetworks.bridge.validators.Messages;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo( use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @Type(name="SimpleScheduleStrategy", value=SimpleScheduleStrategy.class),
    @Type(name="ABTestScheduleStrategy", value=ABTestScheduleStrategy.class)
})
public interface ScheduleStrategy {
    
    public List<Schedule> generateSchedules(ScheduleContext context);
    
    public void validate(Messages messages);

}
