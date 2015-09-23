package org.sagebionetworks.bridge.validators;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.models.schedules.ScheduleContext;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class ScheduleContextValidator implements Validator {
    
    public static final int MAX_EXPIRES_ON_DAYS = 4;

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
        // Very the ending timestamp is not invalid.
        DateTime now = context.getNow();
        if (context.getEndsOn().isBefore(now)) {
            errors.rejectValue("endsOn", "must be after the time of the request");
        } else if (context.getEndsOn().minusDays(MAX_EXPIRES_ON_DAYS).isAfter(now)) {
            errors.rejectValue("endsOn", "must be "+MAX_EXPIRES_ON_DAYS+" days or less");
        }

    }

}
