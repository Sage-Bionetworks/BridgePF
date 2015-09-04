package org.sagebionetworks.bridge.models.schedules;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.quartz.CronScheduleBuilder;
import org.quartz.spi.MutableTrigger;

import com.google.common.collect.Lists;

class CronTaskScheduler extends TaskScheduler {

    CronTaskScheduler(String schedulePlanGuid, Schedule schedule) {
        super(schedulePlanGuid, schedule);
    }
    
    @Override
    public List<Task> getTasks(Map<String, DateTime> events, DateTime until) {
        List<Task> tasks = Lists.newArrayList();
        DateTime scheduledTime = getScheduledTimeBasedOnEvent(schedule, events);
        if (scheduledTime != null) {
            MutableTrigger trigger = parseTrigger(scheduledTime);
            while (scheduledTime.isBefore(until)) {
                Date next = trigger.getFireTimeAfter(scheduledTime.toDate());
                scheduledTime = new DateTime(next, scheduledTime.getZone());
                if (scheduledTime.isBefore(until)) {
                    addTaskForEachTime(tasks, scheduledTime);    
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
