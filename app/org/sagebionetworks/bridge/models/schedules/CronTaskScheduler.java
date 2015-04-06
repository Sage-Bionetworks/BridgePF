package org.sagebionetworks.bridge.models.schedules;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.quartz.CronScheduleBuilder;
import org.quartz.spi.MutableTrigger;

import com.google.common.collect.Lists;

class CronTaskScheduler extends BaseTaskScheduler {

    CronTaskScheduler(String schedulePlanGuid, Schedule schedule) {
        super(schedulePlanGuid, schedule);
    }
    
    @Override
    public List<Task> getTasks(Map<String, DateTime> events, DateTime until) {
        List<Task> tasks = Lists.newArrayList();
        try {
            DateTime datetime = getStartTimeBasedOnEvent(schedule, events);
            if (events.get("now").isAfter(datetime)) {
                datetime = events.get("now");
            }
            MutableTrigger trigger = parseTrigger(datetime, schedule.getCronTrigger());
            while (datetime.isBefore(until)) {
                Date next = trigger.getFireTimeAfter(datetime.toDate());
                datetime = new DateTime(next, datetime.getZone());
                addTaskForEachTime(tasks, datetime);
            }
        } catch(IllegalStateException e) {
            
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
