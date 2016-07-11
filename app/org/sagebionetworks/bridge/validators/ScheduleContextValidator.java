package org.sagebionetworks.bridge.validators;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class ScheduleContextValidator implements Validator {
    
    /**
     * We allow up to four days and that's what we document. Unfortunately, we also adjust endsOn
     * in the controller to the end of the day, so if you pass in 4 days, it is not valid. Add 
     * one day for now... the exact amount is not that critical, we're just trying to prevent
     * something like daysAhead=10000.
     */
    public static final int MAX_EXPIRES_ON_DAYS = 5;

    @Override
    public boolean supports(Class<?> cls) {
        return ScheduleContext.class.isAssignableFrom(cls);
    }

    @Override
    public void validate(Object object, Errors errors) {
        ScheduleContext context = (ScheduleContext)object;
        
        if (context.getZone() == null) {
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
        DateTime now = context.getNow();
        if (context.getEndsOn() == null) {
            errors.rejectValue("endsOn", "is required");
        } else if (context.getEndsOn().isBefore(now)) {
            errors.rejectValue("endsOn", "must be after the time of the request");
        } else if (context.getEndsOn().minusDays(MAX_EXPIRES_ON_DAYS).isAfter(now)) {
            errors.rejectValue("endsOn", "must be "+MAX_EXPIRES_ON_DAYS+" days or less");
        }
    }

}
