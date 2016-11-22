package org.sagebionetworks.bridge.models.schedules;

import java.util.List;

import org.joda.time.DateTime;

import com.google.common.collect.Lists;

/**
 * This scheduler handles schedules that include an interval, times of day, and/or a delay 
 * in order to schedule (rather than a cron expression). In addition, it also handles one-time, 
 * event-based activity scheduling with no recurring schedule.
 */
class IntervalActivityScheduler extends ActivityScheduler {
    
    IntervalActivityScheduler(Schedule schedule) {
        super(schedule);
    }
    
    @Override
    public List<ScheduledActivity> getScheduledActivities(SchedulePlan plan, ScheduleContext context) {
        List<ScheduledActivity> scheduledActivities = Lists.newArrayList();
        DateTime datetime = getScheduledTimeBasedOnEvent(context);

        if (datetime != null) {
            // Remove the time component at this point because we're going to be adding the times in the array
            // of times, and then we'll test again. If this moves past the day where smoe times are in and some 
            // times are out, then the loop will break.
            while(shouldContinueScheduling(context, datetime, scheduledActivities)) {
                addScheduledActivityForAllTimes(scheduledActivities, plan, context, datetime.toLocalDate());
                // A one-time activity with no interval (for example); don't loop
                if (schedule.getInterval() == null) {
                    return trimScheduledActivities(scheduledActivities);
                }
                datetime = datetime.plus(schedule.getInterval());
            }
        }
        return trimScheduledActivities(scheduledActivities);
    }
    
}
