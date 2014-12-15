package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.models.schedules.Schedule.ACTIVITIES_PROPERTY;

import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.ActivityType;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.ScheduleType;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class ScheduleValidator implements Validator {

    public static final String CANNOT_BE_BLANK = "cannot be missing, null, or blank";
    public static final String CANNOT_BE_NULL = "cannot be null";
    
    public static final String ACTIVITY_TYPE_PROPERTY = "activityType";
    public static final String REF_PROPERTY = "ref";
    public static final String SURVEY_PROPERTY = "survey";
    public static final String GUID_PROPERTY = "guid";
    public static final String CREATED_ON_PROPERTY = "createdOn";
    
    @Override
    public boolean supports(Class<?> clazz) {
        return Schedule.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        Schedule schedule = (Schedule)object;
        
        if (schedule.getActivities() == null || schedule.getActivities().isEmpty()) {
            errors.rejectValue(ACTIVITIES_PROPERTY, CANNOT_BE_NULL);
        } else {
            for (int i=0; i < schedule.getActivities().size(); i++) {
                Activity activity = schedule.getActivities().get(i);
                errors.pushNestedPath(ACTIVITIES_PROPERTY+"["+i+"]");
                if (activity.getActivityType() == null) {
                    errors.rejectValue(ACTIVITY_TYPE_PROPERTY, CANNOT_BE_NULL);
                }
                /* We would like to enforce this, but it is new prior to 1.0. After
                 * removing support for activityRef and activityType from schedules,
                 * enable this.
                if (isBlank(activity.getLabel())) {
                    errors.rejectValue("label", CANNOT_BE_BLANK);
                }
                */
                if (isBlank(activity.getRef())) {
                    errors.rejectValue(REF_PROPERTY, CANNOT_BE_BLANK);
                }
                if (activity.getActivityType() == ActivityType.SURVEY) {
                    if (activity.getSurvey() == null) {
                        errors.rejectValue(SURVEY_PROPERTY, CANNOT_BE_NULL);
                    } else {
                        errors.pushNestedPath(SURVEY_PROPERTY);    
                        GuidCreatedOnVersionHolder keys = activity.getSurvey();
                        if (isBlank(keys.getGuid())) {
                            errors.rejectValue(GUID_PROPERTY, CANNOT_BE_BLANK);
                        }
                        if (keys.getCreatedOn() == 0L) {
                            errors.rejectValue(CREATED_ON_PROPERTY, CANNOT_BE_NULL);
                        }
                        if (!activity.getRef().contains(keys.getGuid())) {
                            errors.rejectValue(GUID_PROPERTY, "does not match the URL for this activity");
                        }
                        String timestamp = DateUtils.convertToISODateTime(keys.getCreatedOn());
                        if (!activity.getRef().contains(timestamp)) {
                            errors.rejectValue(CREATED_ON_PROPERTY, "does not match the URL for this activity");
                        }
                        errors.popNestedPath();    
                    }
                }
                errors.popNestedPath();
            }
        }
        if (schedule.getStartsOn() != null && schedule.getEndsOn() != null) {
            long oneHourLater = schedule.getStartsOn() + (1000 * 60 * 60);
            if (schedule.getEndsOn() < oneHourLater) {
                errors.rejectValue(Schedule.ENDS_ON_PROPERTY, "should be at least an hour after the startsOn time");
            }
        }
        if (schedule.getScheduleType() == null) {
            errors.rejectValue(Schedule.SCHEDULE_TYPE_PROPERTY, CANNOT_BE_NULL);
        } else if (schedule.getScheduleType() == ScheduleType.ONCE) {
            if (isNotBlank(schedule.getCronTrigger())) {
                errors.rejectValue(Schedule.CRON_TRIGGER_PROPERTY, "One-time schedule should not have a cron trigger");
            }
        } else if (schedule.getScheduleType() == ScheduleType.RECURRING) {
            // Pretty much everything is valid
            if (isBlank(schedule.getCronTrigger())) {
                errors.rejectValue(Schedule.CRON_TRIGGER_PROPERTY, "Recurring schedule must have a cron trigger");
            }
        }
    }

}
