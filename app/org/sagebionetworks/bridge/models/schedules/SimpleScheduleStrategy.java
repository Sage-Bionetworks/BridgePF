package org.sagebionetworks.bridge.models.schedules;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.validators.ScheduleValidator;
import org.springframework.validation.Errors;

import com.google.common.collect.ImmutableList;

/**
 * Each schedule plan has a strategy for creating schedules that can take contextual 
 * information, like the total set of users. This allows for the implementation of 
 * schedules that perform A/B tests, and probably other strategies.
 */
@BridgeTypeName("SimpleScheduleStrategy")
public final class SimpleScheduleStrategy implements ScheduleStrategy {

    private Schedule schedule;

    public Schedule getSchedule() {
        return schedule;
    }

    public void setSchedule(Schedule schedule) {
        this.schedule = schedule;
    }
    
    @Override
    public Schedule getScheduleForUser(SchedulePlan plan, ScheduleContext context) {
        return schedule;
    }
    
    @Override
    public void validate(Set<String> dataGroups, Set<String> substudyIds, Set<String> taskIdentifiers, Errors errors) {
        if (schedule == null) {
            errors.rejectValue("schedule", "is required");
        } else {
            errors.pushNestedPath("schedule");
            new ScheduleValidator(taskIdentifiers).validate(schedule, errors);
            errors.popNestedPath();
        }
    }

    @Override
    public List<Schedule> getAllPossibleSchedules() {
        if (schedule == null) {
            return ImmutableList.of();
        }
        return ImmutableList.of(schedule);
    }
    
    @Override
    public int hashCode() {
        return Objects.hashCode(schedule);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        SimpleScheduleStrategy other = (SimpleScheduleStrategy) obj;
        return Objects.equals(schedule, other.schedule);
    }

    @Override
    public String toString() {
        return "SimpleScheduleStrategy [schedule=" + schedule + "]";
    }
    
}
