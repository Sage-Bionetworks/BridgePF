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
        DateTime datetime = getStartTimeBasedOnEvent(schedule, events);
        if (datetime != null) {
            MutableTrigger trigger = parseTrigger(datetime, schedule.getCronTrigger());
            while (datetime.isBefore(until)) {
                Date next = trigger.getFireTimeAfter(datetime.toDate());
                datetime = new DateTime(next, datetime.getZone());
                if (datetime.isBefore(until)) {
                    addTaskForEachTime(tasks, datetime);    
                }
            }
        }
        return trimTasks(tasks);
    }
    
    private MutableTrigger parseTrigger(DateTime datetime, String cronTrigger) {
        MutableTrigger mutable = CronScheduleBuilder
            .cronSchedule(schedule.getCronTrigger())
            .inTimeZone(datetime.getZone().toTimeZone()).build();
        mutable.setStartTime(datetime.toDate());
        return mutable;
    }

}
