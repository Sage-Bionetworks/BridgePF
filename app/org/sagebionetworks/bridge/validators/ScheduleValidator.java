package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.models.schedules.Schedule.ACTIVITIES_PROPERTY;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.Schedule;
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
        
        // Should have either a cron expression or an interval, but not both.
        if (isNotBlank(schedule.getCronTrigger()) && schedule.getInterval() != null) {
            errors.reject("should have either a cron expression, or an interval (not both)");
        } else if (isBlank(schedule.getCronTrigger()) && schedule.getInterval() == null) {
            errors.reject("should have either a cron expression, or an interval");
        }
        /* I would like to do this, but I'm not absolutely certain it's necessary
        if (schedule.getExpires() == null && schedule.getEndsOn() == null) {
            errors.reject("should have either a duration until it expires, or an endsOn date and time");
        }
        */
        if (schedule.getStartsOn() != null && schedule.getEndsOn() != null) {
            DateTime oneHourLater = schedule.getStartsOn().plusHours(1);
            if (schedule.getEndsOn().isBefore(oneHourLater)) {
                errors.rejectValue(Schedule.ENDS_ON_PROPERTY, "should be at least an hour after the startsOn time");
            }
        }
        if (schedule.getScheduleType() == null) {
            errors.rejectValue(Schedule.SCHEDULE_TYPE_PROPERTY, CANNOT_BE_NULL);
        }
        validateActivities(schedule, errors);
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
