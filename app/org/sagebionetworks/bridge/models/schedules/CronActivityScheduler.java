package org.sagebionetworks.bridge.models.schedules;

import java.util.Date;
import java.util.List;

import org.joda.time.DateTime;
import org.quartz.CronScheduleBuilder;
import org.quartz.spi.MutableTrigger;

import com.google.common.collect.Lists;

class CronActivityScheduler extends ActivityScheduler {

    CronActivityScheduler(Schedule schedule) {
        super(schedule);
    }
    
    @Override
    public List<ScheduledActivity> getScheduledActivities(SchedulePlan plan, ScheduleContext context) {
        List<ScheduledActivity> scheduledActivities = Lists.newArrayList();
        DateTime scheduledTime = getScheduledTimeBasedOnEvent(context);
        
        if (keepScheduling(context, scheduledTime, scheduledActivities)) {
            MutableTrigger trigger = parseTrigger(scheduledTime);
            
            while (keepScheduling(context, scheduledTime, scheduledActivities)) {
                Date next = trigger.getFireTimeAfter(scheduledTime.toDate());
                scheduledTime = new DateTime(next, context.getZone());
                
                if (keepScheduling(context, scheduledTime, scheduledActivities)) {
                    addScheduledActivityForAllTimes(scheduledActivities, plan, context, scheduledTime);    
                }
            }
        }
        return trimScheduledActivities(scheduledActivities);
    }
    
    /**
     * If scheduling hasn't reached the end time, or hasn't accumulated the minimum number of tasks, returns true, or 
     * false otherwise. 
     */
    private boolean keepScheduling(ScheduleContext context, DateTime scheduledTime, List<ScheduledActivity> scheduledActivities) {
        return scheduledTime.isBefore(context.getEndsOn()) || 
                hasNotMetMinimumCount(context, schedule.getScheduleType(), scheduledActivities.size());
    }
    
    private MutableTrigger parseTrigger(DateTime scheduledTime) {
        MutableTrigger mutable = CronScheduleBuilder
            .cronSchedule(schedule.getCronTrigger())
            .inTimeZone(scheduledTime.getZone().toTimeZone()).build();
        mutable.setStartTime(scheduledTime.toDate());
        return mutable;
    }

}
