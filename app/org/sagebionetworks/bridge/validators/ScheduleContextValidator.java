package org.sagebionetworks.bridge.validators;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class ScheduleContextValidator implements Validator {
    /**
     * Limit the number of days you can request for activities. The date range must be less than (not equal to) this
     * amount. We set this to 32 so that you can get activities for a whole month at a time.
     */
    public static final int MAX_DATE_RANGE_IN_DAYS = 32;
    
    /**
     * The maximum number of tasks you can force when scheduling. For our use case it's hard to argue 
     * for a large value here and it could cause the scheduler to run for a long time if the number is 
     * overly large. 
     */
    public static final int MAX_MIN_ACTIVITY_COUNT = 5;

    @Override
    public boolean supports(Class<?> cls) {
        return ScheduleContext.class.isAssignableFrom(cls);
    }

    @Override
    public void validate(Object object, Errors errors) {
        ScheduleContext context = (ScheduleContext)object;
        
        if (context.getInitialTimeZone() == null) {
            errors.rejectValue("offset", "must set a time zone offset in format '±HH:MM'");
        }
        if (context.getCriteriaContext().getHealthCode() == null) {
            errors.rejectValue("healthCode", "is required");
        }
        if (context.getCriteriaContext().getUserId() == null) {
            errors.rejectValue("userId", "is required");
        }
        if (context.getAccountCreatedOn() == null) {
            errors.rejectValue("accountCreatedOn", "is required");
        }
        // Very the ending timestamp is not invalid.
        DateTime startsOn = context.getStartsOn();
        if (context.getEndsOn() == null) {
            errors.rejectValue("endsOn", "is required");
        } else if (context.getEndsOn().isBefore(startsOn)) {
            errors.rejectValue("endsOn", "must be after startsOn");
        } else if (context.getEndsOn().minusDays(MAX_DATE_RANGE_IN_DAYS).isAfter(startsOn)) {
            errors.rejectValue("endsOn", "must be less than " + MAX_DATE_RANGE_IN_DAYS + " days");
        }
        if (context.getMinimumPerSchedule() < 0) {
            errors.rejectValue("minimumPerSchedule", "cannot be negative");
        } else if (context.getMinimumPerSchedule() > MAX_MIN_ACTIVITY_COUNT) {
            errors.rejectValue("minimumPerSchedule", "cannot be greater than " + MAX_MIN_ACTIVITY_COUNT);
        }
    }

}
