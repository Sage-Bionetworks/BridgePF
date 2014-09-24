package org.sagebionetworks.bridge.models.schedules;

import java.util.List;

import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.validators.Messages;

import com.google.common.collect.Lists;

/**
 * Each schedule plan has a strategy for creating schedules that can take contextual 
 * information, like the total set of users. This allows for the implementation of 
 * schedules that perform A/B tests, and probably other strategies.
 */
public class SimpleScheduleStrategy implements ScheduleStrategy {

    private Schedule schedule;

    public Schedule getSchedule() {
        return schedule;
    }

    public void setSchedule(Schedule schedule) {
        this.schedule = schedule;
    }

    @Override
    public List<Schedule> generateSchedules(ScheduleContext context) {
        List<Schedule> schedules = Lists.newArrayListWithCapacity(context.getUsers().size());
        for (User user : context.getUsers()) {
            Schedule sch = new Schedule(schedule);
            sch.setStudyAndUser(context.getStudy(), user);
            schedules.add(sch);
        }
        return schedules;
    }
    @Override
    public void validate(Messages messages) {
        if (schedule == null) {
            messages.add("simple schedule plan is missing a schedule");
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
