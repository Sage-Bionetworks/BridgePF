package org.sagebionetworks.bridge.models.schedules;

import java.text.ParseException;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.json.DateUtils;
import org.quartz.CronExpression;

/**
 * The purpose of this class is to validate that the schedule information we are collecting 
 * can be turned into discreet scheduled events with start and end dates. It is not used 
 * elsewhere in the code.
 * 
 * Some things to note:
 * - startsOn/endsOn seem pretty useless. You could use them to prevent tasks being sent out 
 *      before or after a date en masse, when starting or stopping a study, but basically useless.
 *      
 * - once/recurring is separate from when to schedule the first task. And the options are basically
 *      on or after enrollment. 
 *      
 * - if it's recurring, then it's a cron expression that determines how it recurs (once started).
 */
public class ScheduleEvaluator {

    /**
     * @param schedule
     * @param enrollment
     */
    public Task evaluate(Schedule schedule, DateTime enrollment) throws ParseException {
        // Right now, we can evaluate when the next task should occur, but we can't do ONCE tasks 
        // because we have no way to know a task has been done. Actually we can't even tell if 
        // we're calculating a task's times for the first time or the hundredth time.
        Task task = null;
        switch(schedule.getScheduleType()) {
        case ONCE:
            task = new Task(getNextTime(schedule));
            break;
        case RECURRING:
            task = new Task(getNextTime(schedule));
            break;
        case RECURRING_AFTER_ENROLLMENT:
            task = new Task(getNextTimeAfterEnrollment(schedule, enrollment));
            break;
        case RECURRING_ON_ENROLLMENT:
            task = new Task(getNextTimeOnEnrollment(schedule, enrollment));
            break;
        }
        if (isOutsideScheduleWindow(task, schedule)) {
            return null;
        }
        task = endBasedOnExpiration(task, schedule);
        
        return task;
    }
    
    private Task endBasedOnExpiration(Task task, Schedule schedule) {
        if (schedule.getExpires() != null) {
            return new Task(task.getStartsOn(), task.getStartsOn().plus(schedule.getExpires()));
        }
        return task;
    }
    
    private DateTime getNextTime(Schedule schedule) throws ParseException {
        return scheduleNextTime(DateUtils.getCurrentDateTime(), schedule);
    }
    
    private DateTime getNextTimeOnEnrollment(Schedule schedule, DateTime enrollment) throws ParseException {
        return scheduleNextTime(enrollment, schedule);
    }

    private DateTime getNextTimeAfterEnrollment(Schedule schedule, DateTime enrollment) throws ParseException {
        return scheduleNextTime(enrollment.plus(schedule.getDelay()), schedule);
    }
    
    private DateTime scheduleNextTime(DateTime start, Schedule schedule) throws ParseException {
        // Once the first time is past the current date, we simply schedule from current time.
        if (DateUtils.getCurrentDateTime().isAfter(start)) {
            start = DateUtils.getCurrentDateTime();
        }
        // If there's a cron trigger, calculate the next valid time
        if (schedule.getCronTrigger() != null) {
            CronExpression expression = new CronExpression(schedule.getCronTrigger());
            start = new DateTime(expression.getNextValidTimeAfter(start.toDate()));
        }
        return start;
    }
    
    
    /**
     * If startsOn/endsOn are specified, the individual task cannot be scheduled to start outside of this 
     * window.
     * @param task
     * @param schedule
     * @return
     */
    private boolean isOutsideScheduleWindow(Task task, Schedule schedule) {
        if (schedule.getStartsOn() != null) {
            if (task.getStartsOn().isBefore(schedule.getStartsOn()) ||
                task.getStartsOn().isAfter(schedule.getEndsOn())) {
                return true;
            }
        }
        return false;
    }
    
}
