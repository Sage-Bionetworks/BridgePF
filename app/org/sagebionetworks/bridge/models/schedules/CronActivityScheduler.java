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
        
        if (scheduledTime != null) {
            MutableTrigger trigger = parseTrigger(scheduledTime);
            
            while (shouldContinueScheduling(context, scheduledTime, scheduledActivities)) {
                Date next = trigger.getFireTimeAfter(scheduledTime.toDate());
                scheduledTime = new DateTime(next);
                
                if (shouldContinueScheduling(context, scheduledTime, scheduledActivities)) {
                    addScheduledActivityForAllTimes(scheduledActivities, plan, context, scheduledTime);    
                }
            }
        }
        return trimScheduledActivities(scheduledActivities);
    }
    
    private MutableTrigger parseTrigger(DateTime scheduledTime) {
        MutableTrigger mutable = CronScheduleBuilder
            .cronSchedule(schedule.getCronTrigger())
            .inTimeZone(scheduledTime.getZone().toTimeZone()).build();
        mutable.setStartTime(scheduledTime.toDate());
        return mutable;
    }

}
