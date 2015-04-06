package org.sagebionetworks.bridge.models.schedules;

import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;

import com.google.common.collect.Lists;

class IntervalTaskScheduler extends BaseTaskScheduler {

    IntervalTaskScheduler(String schedulePlanGuid, Schedule schedule) {
        super(schedulePlanGuid, schedule);
    }
    
    @Override
    public List<Task> getTasks(Map<String, DateTime> events, DateTime until) {
        List<Task> tasks = Lists.newArrayList();
        try {
            
            DateTime datetime = getStartTimeBasedOnEvent(schedule, events);
            while(datetime.isBefore(until)) {
                addTaskForEachTime(tasks, datetime);
                // These are one-time tasks; may want to move out to a separate scheduler at some point.
                if (schedule.getInterval() == null) {
                    throw new IllegalStateException();
                }
                datetime = datetime.plus(schedule.getInterval());
            }
            
        } catch(IllegalStateException e) {
        }
        return trimTasks(tasks);
    }

}
