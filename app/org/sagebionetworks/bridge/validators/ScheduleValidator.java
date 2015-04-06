
package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.models.schedules.Schedule.ACTIVITIES_PROPERTY;

import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.Hours;
import org.joda.time.LocalTime;
import org.joda.time.Period;
import org.quartz.CronExpression;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.ScheduleType;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

@Component
public class ScheduleValidator implements Validator {

    private static final ActivityValidator ACTIVITY_VALIDATOR = new ActivityValidator();
    
    public static final String CANNOT_BE_BLANK = "cannot be missing, null, or blank";
    public static final String CANNOT_BE_NULL = "cannot be null";
    
    @Override
    public boolean supports(Class<?> clazz) {
        return Schedule.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        Schedule schedule = (Schedule)object;
        
        if (schedule.getScheduleType() == null) {
            errors.rejectValue(Schedule.SCHEDULE_TYPE_PROPERTY, CANNOT_BE_NULL);
        } else if (schedule.getScheduleType() == ScheduleType.ONCE){
            // If it's a one-time task, it shouldn't have an interval. It can have a cron trigger, although this
            // sounds like a totally worthless schedule. Still, "schedule this one time, the next Monday after 
            // enrollment" or somesuch, is a thing.
            if (schedule.getInterval() != null) {
                errors.reject("executing once should not have an interval");
            }
        } else {
            // Otherwise for recurring tasks, should have either a cron expression or an interval, but not both.
            if (isNotBlank(schedule.getCronTrigger()) && schedule.getInterval() != null) {
                errors.reject("recurring schedules should have either a cron expression, or an interval (not both)");
            } else if (isBlank(schedule.getCronTrigger()) && schedule.getInterval() == null) {
                errors.reject("recurring schedules should have either a cron expression, or an interval");
            }
            if (schedule.getCronTrigger() != null && !CronExpression.isValidExpression(schedule.getCronTrigger())) {
                errors.rejectValue(Schedule.CRON_TRIGGER_PROPERTY, "is an invalid cron expression");
            }
            if (schedule.getInterval() != null && (schedule.getTimes() == null || schedule.getTimes().isEmpty())) {
                errors.rejectValue(Schedule.TIMES_PROPERTY, "are required for interval-based schedules");
            }
        }

        // If the delay is smaller than 1 day, *and* there are times listed, this is ambiguous. Either increase the 
        // delay or drop the times.
        if (timesAmbiguous(schedule.getDelay(), schedule.getTimes())) {
            errors.rejectValue(Schedule.DELAY_PROPERTY, "is less than one day, and times of day are also set for this schedule, which is ambiguous");
        }
        if (isBlank(schedule.getCronTrigger()) && intervalTooShort(schedule.getInterval())) {
            errors.rejectValue(Schedule.INTERVAL_PROPERTY, "must be at least one day");
        }
        if (schedule.getStartsOn() != null && schedule.getEndsOn() != null) {
            DateTime oneHourLater = schedule.getStartsOn().plusHours(1);
            if (schedule.getEndsOn().isBefore(oneHourLater)) {
                errors.rejectValue(Schedule.ENDS_ON_PROPERTY, "should be at least an hour after the startsOn time");
            }
        }
        validateActivities(schedule, errors);
    }
    
    private boolean intervalTooShort(Period interval) {
        if (interval == null) {
            return false;
        }
        DateTime now = DateTime.now();
        DateTime dur = now.plus(interval);
        DateTime hours24 = now.plusHours(24);
        return dur.isBefore(hours24);
    }
    
    private boolean timesAmbiguous(Period delay, List<LocalTime> times) {
        if (delay == null || times == null || times.isEmpty()) {
            return false;
        }
        DateTime now = DateTime.now();
        DateTime dur = now.plus(delay);
        DateTime hours24 = now.plusHours(24);
        return !times.isEmpty() && dur.isBefore(hours24);
    }

    private void validateActivities(Schedule schedule, Errors errors) {
        if (schedule.getActivities() == null || schedule.getActivities().isEmpty()) {
            errors.rejectValue(ACTIVITIES_PROPERTY, CANNOT_BE_NULL);
        } else {
            for (int i=0; i < schedule.getActivities().size(); i++) {
                Activity activity = schedule.getActivities().get(i);
                errors.pushNestedPath(ACTIVITIES_PROPERTY+"["+i+"]");
                ACTIVITY_VALIDATOR.validate(activity, errors);
                errors.popNestedPath();
            }
        }
    }

}
