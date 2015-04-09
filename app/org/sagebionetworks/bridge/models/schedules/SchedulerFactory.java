package org.sagebionetworks.bridge.models.schedules;

public class SchedulerFactory {

    public static TaskScheduler getScheduler(String schedulePlanGuid, Schedule schedule) {
        if (schedule.getCronTrigger() != null) {
            return new CronTaskScheduler(schedulePlanGuid, schedule);
        }
        return new IntervalTaskScheduler(schedulePlanGuid, schedule);
    };
    
}
