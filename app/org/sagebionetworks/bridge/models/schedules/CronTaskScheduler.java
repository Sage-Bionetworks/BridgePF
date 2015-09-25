package org.sagebionetworks.bridge.models.schedules;

import java.util.Date;
import java.util.List;

import org.joda.time.DateTime;
import org.quartz.CronScheduleBuilder;
import org.quartz.spi.MutableTrigger;

import com.google.common.collect.Lists;

class CronTaskScheduler extends TaskScheduler {

    CronTaskScheduler(Schedule schedule) {
        super(schedule);
    }
    
    @Override
    public List<Task> getTasks(ScheduleContext context) {
        List<Task> tasks = Lists.newArrayList();
        DateTime scheduledTime = getScheduledTimeBasedOnEvent(context);
        
        if (scheduledTime != null) {
            MutableTrigger trigger = parseTrigger(scheduledTime);
            
            while (scheduledTime.isBefore(context.getEndsOn())) {
                Date next = trigger.getFireTimeAfter(scheduledTime.toDate());
                scheduledTime = new DateTime(next, context.getZone());
                
                if (scheduledTime.isBefore(context.getEndsOn())) {
                    addTaskForEachTime(tasks, context, scheduledTime);    
                }
            }
        }
        return trimTasks(tasks);
    }
    
    private MutableTrigger parseTrigger(DateTime scheduledTime) {
        MutableTrigger mutable = CronScheduleBuilder
            .cronSchedule(schedule.getCronTrigger())
            .inTimeZone(scheduledTime.getZone().toTimeZone()).build();
        mutable.setStartTime(scheduledTime.toDate());
        return mutable;
    }

}
