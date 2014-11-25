package org.sagebionetworks.bridge.validators;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.ScheduleType;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class ScheduleValidator implements Validator {

    public static final String CANNOT_BE_BLANK = "cannot be missing, null, or blank";
    public static final String CANNOT_BE_NULL = "cannot be missing or null";
    
    @Override
    public boolean supports(Class<?> clazz) {
        return Schedule.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        Schedule schedule = (Schedule)object;
        
        if (StringUtils.isBlank(schedule.getLabel())) {
            errors.rejectValue("label", CANNOT_BE_BLANK);
        }
        if (StringUtils.isBlank(schedule.getActivityRef())) {
            errors.rejectValue("activityRef", CANNOT_BE_BLANK);
        }
        /* You validate before these are set as part of schedule finalization per user.
         * (We copy the schedule as a kind of template, for each user, and set these 
         * values, before persisting that copy).
        if (StringUtils.isBlank(schedule.getSchedulePlanGuid())) {
            errors.rejectValue("schedulePlanGuid", CANNOT_BE_BLANK);
        }
        if (StringUtils.isBlank(schedule.getStudyUserCompoundKey())) {
            errors.rejectValue("studyUserCompoundKey", CANNOT_BE_BLANK);
        }
        */
        if (schedule.getActivityType() == null) {
            errors.rejectValue("activityType", CANNOT_BE_NULL);
        }
        if (schedule.getScheduleType() == null) {
            errors.rejectValue("scheduleType", CANNOT_BE_NULL);
        }
        if (schedule.getScheduleType() == ScheduleType.ONCE) {
            if (StringUtils.isNotBlank(schedule.getCronTrigger())) {
                errors.rejectValue("cronTrigger", "One-time schedule should not have a cron trigger");
            }
        }
        if (schedule.getScheduleType() == ScheduleType.RECURRING) {
            // Pretty much everything is valid
            if (StringUtils.isBlank(schedule.getCronTrigger())) {
                errors.rejectValue("cronTrigger", "Recurring schedule must have a cron trigger");
            }
        }
    }

}
