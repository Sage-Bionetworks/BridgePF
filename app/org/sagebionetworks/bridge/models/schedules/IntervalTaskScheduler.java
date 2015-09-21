package org.sagebionetworks.bridge.models.schedules;

import java.util.List;

import org.joda.time.DateTime;

import com.google.common.collect.Lists;

/**
 * This scheduler handles schedules that include an interval, times of day, and/or a delay 
 * in order to schedule (rather than a cron expression). In addition, it also handles one-time, 
 * event-based task scheduling with no recurring schedule.
 */
class IntervalTaskScheduler extends TaskScheduler {
    
    IntervalTaskScheduler(Schedule schedule) {
        super(schedule);
    }
    
    @Override
    public List<Task> getTasks(ScheduleContext context) {
        List<Task> tasks = Lists.newArrayList();
        DateTime datetime = getScheduledTimeBasedOnEvent(context);
        if (datetime != null) {
            while(datetime.isBefore(context.getEndsOn())) {
                addTaskForEachTime(tasks, context, datetime);
                // A one-time task with no interval (for example); don't loop
                if (schedule.getInterval() == null) {
                    return trimTasks(tasks ,schedule);
                }
                datetime = datetime.plus(schedule.getInterval());
            }
        }
        return trimTasks(tasks, schedule);
    }

}
