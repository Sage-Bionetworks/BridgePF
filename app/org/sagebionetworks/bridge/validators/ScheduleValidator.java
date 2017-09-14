
package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.models.schedules.Schedule.ACTIVITIES_PROPERTY;

import java.util.List;
import java.util.Set;

import org.joda.time.DateTime;
import org.joda.time.LocalTime;
import org.joda.time.Period;
import org.quartz.CronExpression;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.ScheduleType;

import com.google.common.collect.Sets;

import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class ScheduleValidator implements Validator {
    
    public static final String CANNOT_BE_BLANK = "cannot be missing, null, or blank";
    public static final String CANNOT_BE_NULL = "cannot be null";
    
    private final ActivityValidator activityValidator;
    
    public ScheduleValidator(Set<String> taskIdentifiers) {
        activityValidator = new ActivityValidator(taskIdentifiers);
    }
    
    @Override
    public boolean supports(Class<?> clazz) {
        return Schedule.class.isAssignableFrom(clazz);
    }
    
    @Override
    public void validate(Object object, Errors errors) {
        Schedule schedule = (Schedule)object;
        
        if (schedule.getScheduleType() == null) {
            errors.rejectValue(Schedule.SCHEDULE_TYPE_PROPERTY, "is required");
        }
        if (persistentScheduleHasRecurringConfiguration(schedule)) {
            errors.rejectValue(Schedule.SCHEDULE_TYPE_PROPERTY, "should not have delay, interval, cron expression, times, or an expiration");
        }
        if (oneTimeScheduleHasIntervalOrCronExpression(schedule)) {
            errors.rejectValue(Schedule.SCHEDULE_TYPE_PROPERTY, "set to once, but also has an interval and/or cron expression");
        }
        if (recurringScheduleLacksRepeatingInfo(schedule)) {
            errors.rejectValue(Schedule.INTERVAL_PROPERTY, "or cron expression must be set when a schedule repeats");
        }
        if (recurringScheduleHasTooMuchRepeatingInfo(schedule)) {
            errors.rejectValue(Schedule.INTERVAL_PROPERTY, "and cron expression cannot both be set when a schedule repeats (results are ambiguous)");
        }
        if (recurringScheduleLacksExpirationPeriod(schedule)) {
            errors.rejectValue(Schedule.EXPIRES_PROPERTY, "must be set if schedule repeats");
        }
        if (cronExpressionInvalid(schedule)) {
            errors.rejectValue(Schedule.CRON_TRIGGER_PROPERTY, "is an invalid cron expression");
        }
        if (cronExpressionHasTimes(schedule)) {
            errors.rejectValue(Schedule.CRON_TRIGGER_PROPERTY, "cannot have times (they are included in the expression)");
        }
        if (intervalMissingTimes(schedule)) {
            errors.rejectValue(Schedule.TIMES_PROPERTY, "are required for interval-based schedules");
        }
        if (timesAreDuplicated(schedule)) {
            errors.rejectValue(Schedule.TIMES_PROPERTY, "cannot contain duplicates");
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
        if (iterationPeriodForNonRecurringSchedule(schedule)) {
            errors.rejectValue(Schedule.SEQUENCE_PERIOD_PROPERTY, "cannot be set unless schedule is recurring");
        }
        validateActivities(schedule, errors);
    }
    
    /**
     * Iteration period is specifically intended to limit the production of activities from recurring schedules 
     * (whether interval based or cron based). It is an error to include it in other schedule types.
     */
    private boolean iterationPeriodForNonRecurringSchedule(Schedule schedule) {
        return (schedule.getSequencePeriod() != null && schedule.getScheduleType() != ScheduleType.RECURRING);
    }
    
    /**
     * Persistent schedules are very simple, they should not have a delay, interval, cron expression, or 
     * times.
     * @param schedule
     * @return
     */
    private boolean persistentScheduleHasRecurringConfiguration(Schedule schedule) {
        if (schedule.getScheduleType() != ScheduleType.PERSISTENT) {
            return false;
        }
        return (schedule.getInterval() != null || schedule.getCronTrigger() != null ||
                schedule.getDelay() != null || schedule.getExpires() != null || 
                !schedule.getTimes().isEmpty());
    }
    /**
     * A one time schedule should not have an interval or cron expression.
     * @param schedule
     * @return
     */
    private boolean oneTimeScheduleHasIntervalOrCronExpression(Schedule schedule) {
        boolean repeats = (schedule.getInterval() != null || schedule.getCronTrigger() != null);
        return (schedule.getScheduleType() == ScheduleType.ONCE && repeats);
    }
    
    /**
     * A recurring schedule must either have an interval with times, or a cron expression. Otherwise we don't
     * know how to repeat it.
     * @param schedule
     * @return
     */
    private boolean recurringScheduleLacksRepeatingInfo(Schedule schedule) {
        return schedule.getScheduleType() == ScheduleType.RECURRING && 
               schedule.getInterval() == null &&
               isBlank(schedule.getCronTrigger());
    }
    
    /**
     * A recurring schedule cannot have both an interval and a cron expression. Otherwise we don't
     * know how to repeat it.
     * @param schedule
     * @return
     */
    private boolean recurringScheduleHasTooMuchRepeatingInfo(Schedule schedule) {
        return schedule.getScheduleType() == ScheduleType.RECURRING && 
                schedule.getInterval() != null &&
                isNotBlank(schedule.getCronTrigger());
    }

    /**
     * Schedules that repeat without expiring activities can stack those activities all the way back to the first event 
     * that triggers the activity sequence. The most common problem this causes is adding a schedule that then 
     * creates activities all the way back to enrollment, dumping 100s of activities on the client. Expires is required 
     * for recurring schedules.
     * @param schedule
     * @return
     */
    private boolean recurringScheduleLacksExpirationPeriod(Schedule schedule) {
        return (schedule.getScheduleType() == ScheduleType.RECURRING && schedule.getExpires() == null);
    }
    
    private boolean cronExpressionInvalid(Schedule schedule) {
        return schedule.getCronTrigger() != null && !CronExpression.isValidExpression(schedule.getCronTrigger());
    }
    
    private boolean cronExpressionHasTimes(Schedule schedule) {
        return schedule.getCronTrigger() != null && schedule.getTimes() != null && !schedule.getTimes().isEmpty();
    }
    
    /**
     * Interval scheduling requires that a time of the day must be provided (since the interval itself cannot 
     * be less than a day, so we don't know the time of day to start the activity). If more than one time is provided, 
     * we schedule a activity for each time, so beware, this can add up quickly. 
     * @param schedule
     * @return
     */
    private boolean intervalMissingTimes(Schedule schedule) {
        return schedule.getInterval() != null && (schedule.getTimes() == null || schedule.getTimes().isEmpty());
    }
    
    /**
     * Do not allow times array to contain duplicates. See BRIDGE-1765. 
     */
    private boolean timesAreDuplicated(Schedule schedule) {
        if (schedule.getTimes() == null) {
            return false;
        }
        Set<LocalTime> times = Sets.newHashSet(schedule.getTimes());
        return times.size() != schedule.getTimes().size();
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
    
    /**
     * The expires period must be at least one hour.
     * @param period
     * @return
     */
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
    
    /**
     * The scheduling window (startsOn, endsOn) must be in a chronologically correct 
     * order with at least an hour between the two times (if both are present).
     */
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
            errors.rejectValue(ACTIVITIES_PROPERTY, "are required");
        } else {
            for (int i=0; i < schedule.getActivities().size(); i++) {
                Activity activity = schedule.getActivities().get(i);
                errors.pushNestedPath(ACTIVITIES_PROPERTY+"["+i+"]");
                activityValidator.validate(activity, errors);
                errors.popNestedPath();
            }
        }
    }

}
