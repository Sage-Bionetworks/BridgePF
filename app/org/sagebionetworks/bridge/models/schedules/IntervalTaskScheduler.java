package org.sagebionetworks.bridge.models.schedules;

import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;

import com.google.common.collect.Lists;

/**
 * This scheduler handles schedules that include an interval, times of day, and/or a delay 
 * in order to schedule (rather than a cron expression). In addition, it also handles one-time, 
 * event-based task scheduling with no recurring schedule.
 */
class IntervalTaskScheduler extends BaseTaskScheduler {
    
    IntervalTaskScheduler(String schedulePlanGuid, Schedule schedule) {
        super(schedulePlanGuid, schedule);
    }
    
    @Override
    public List<Task> getTasks(Map<String, DateTime> events, DateTime until) {
        List<Task> tasks = Lists.newArrayList();
        DateTime datetime = getStartTimeBasedOnEvent(schedule, events);
        if (datetime != null) {
            while(datetime.isBefore(until)) {
                addTaskForEachTime(tasks, datetime);
                // A one-time task with no interval (for example); don't loop
                if (schedule.getInterval() == null) {
                    return trimTasks(tasks);
                }
                datetime = datetime.plus(schedule.getInterval());
            }
        }
        return trimTasks(tasks);
    }

}
