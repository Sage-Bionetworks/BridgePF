package org.sagebionetworks.bridge.models.schedules;

import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.validators.ScheduleValidator;
import org.springframework.validation.Errors;

/**
 * Each schedule plan has a strategy for creating schedules that can take contextual 
 * information, like the total set of users. This allows for the implementation of 
 * schedules that perform A/B tests, and probably other strategies.
 */
@BridgeTypeName("SimpleScheduleStrategy")
public class SimpleScheduleStrategy implements ScheduleStrategy {

    private Schedule schedule;

    public Schedule getSchedule() {
        return schedule;
    }

    public void setSchedule(Schedule schedule) {
        this.schedule = schedule;
    }
    
    @Override
    public Schedule getScheduleForUser(Study study, SchedulePlan plan, User user) {
        Schedule clone = schedule.copy();
        clone.setStudyAndUser(study, user);
        return clone;
    }
    @Override
    public void validate(Errors errors) {
        if (schedule == null) {
            errors.reject("simple schedule plan is missing a schedule");
        } else {
            errors.pushNestedPath("schedule");
            new ScheduleValidator().validate(schedule, errors);
            errors.popNestedPath();
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((schedule == null) ? 0 : schedule.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        SimpleScheduleStrategy other = (SimpleScheduleStrategy) obj;
        if (schedule == null) {
            if (other.schedule != null)
                return false;
        } else if (!schedule.equals(other.schedule))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "SimpleScheduleStrategy [schedule=" + schedule + "]";
    }
    
}
