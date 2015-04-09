
package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.models.schedules.Schedule.ACTIVITIES_PROPERTY;

import java.util.List;

import org.joda.time.DateTime;
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
        }
        if (oneTimeScheduleHasInterval(schedule)) {
            errors.reject("executing once should not have an interval");
        }
        if (recurringScheduleLacksRepeatingInfo(schedule)) {
            errors.reject("recurring schedules should have either a cron expression, or an interval, but not both");
        }
        if (cronExpressionInvalid(schedule)) {
            errors.rejectValue(Schedule.CRON_TRIGGER_PROPERTY, "is an invalid cron expression");
        }
        if (intervalMissingTimes(schedule)) {
            errors.rejectValue(Schedule.TIMES_PROPERTY, "are required for interval-based schedules");
        }
        if (intervalTooShort(schedule.getInterval())) {
            errors.rejectValue(Schedule.INTERVAL_PROPERTY, "must be at least one day");
        }
        if (periodTooShort(schedule.getExpires())) {
            errors.rejectValue(Schedule.EXPIRES_PROPERTY, "must be at least one hour");
        }
        if (delayWithTimesAreAmbiguous(schedule.getDelay(), schedule.getTimes())) {
            errors.rejectValue(Schedule.DELAY_PROPERTY, "is less than one day, and times of day are also set for this schedule, which is ambiguous");
        }
        if (timesNotOrderedChronologically(schedule)) {
            errors.rejectValue(Schedule.ENDS_ON_PROPERTY, "should be at least an hour after the startsOn time");
        }
        validateActivities(schedule, errors);
    }

    private boolean oneTimeScheduleHasInterval(Schedule schedule) {
        return (schedule.getScheduleType() == ScheduleType.ONCE && schedule.getInterval() != null);
    }
    
    private boolean recurringScheduleLacksRepeatingInfo(Schedule schedule) {
        boolean bothOrNeither = (schedule.getInterval() != null && isNotBlank(schedule.getCronTrigger()) || 
                                (schedule.getInterval() == null && isBlank(schedule.getCronTrigger())) );
        return (schedule.getScheduleType() == ScheduleType.RECURRING) && bothOrNeither;
    }        

    private boolean cronExpressionInvalid(Schedule schedule) {
        return schedule.getCronTrigger() != null && !CronExpression.isValidExpression(schedule.getCronTrigger());
    }
    
    private boolean intervalMissingTimes(Schedule schedule) {
        return schedule.getInterval() != null && (schedule.getTimes() == null || schedule.getTimes().isEmpty());
    }
    
    /**
     * Interval currently must be at least 24 hours (this is because it is ambiguous to have an interval under
     * twenty-four hours along with specific times of the day). For intervals under a day, a cron expression may work.
     * 
     * @param interval
     * @return
     */
    private boolean intervalTooShort(Period interval) {
        if (interval == null) {
            return false;
        }
        DateTime now = DateTime.now();
        DateTime dur = now.plus(interval);
        DateTime oneDay = now.plusDays(1);
        return dur.isBefore(oneDay);
    }
    
    private boolean periodTooShort(Period period) {
        if (period == null) {
            return false;
        }
        DateTime now = DateTime.now();
        DateTime dur = now.plus(period);
        DateTime oneHour = now.plusHours(1);
        return dur.isBefore(oneHour);
    }
    
    /**
     * It is ambiguous to have a delay less than one day long, as well as times. When using 
     * interval scheduling (where times are required), the delay cannot be less than a day.
     * @param delay
     * @param times
     * @return
     */
    private boolean delayWithTimesAreAmbiguous(Period delay, List<LocalTime> times) {
        if (delay == null || times == null || times.isEmpty()) {
            return false;
        }
        DateTime now = DateTime.now();
        DateTime dur = now.plus(delay);
        DateTime hours24 = now.plusHours(24);
        return !times.isEmpty() && dur.isBefore(hours24);
    }
    
    private boolean timesNotOrderedChronologically(Schedule schedule) {
        if (schedule.getStartsOn() != null && schedule.getEndsOn() != null) {
            DateTime oneHourLater = schedule.getStartsOn().plusHours(1);
            if (schedule.getEndsOn().isBefore(oneHourLater)) {
                return true;
            }
        }
        return false;
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
