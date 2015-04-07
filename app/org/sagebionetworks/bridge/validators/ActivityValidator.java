package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;

import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.ActivityType;
import org.sagebionetworks.bridge.models.schedules.SurveyReference;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class ActivityValidator implements Validator {
    
    private static final String CANNOT_BE_BLANK = "cannot be missing, null, or blank";
    private static final String CANNOT_BE_NULL = "cannot be null";
    private static final String ACTIVITY_TYPE_PROPERTY = "activityType";
    private static final String REF_PROPERTY = "ref";
    private static final String SURVEY_PROPERTY = "survey";
    private static final String GUID_PROPERTY = "guid";
    private static final String CREATED_ON_PROPERTY = "createdOn";
    
    @Override
    public boolean supports(Class<?> clazz) {
        return Activity.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object obj, Errors errors) {
        Activity activity = (Activity)obj;
        if (activity.getActivityType() == null) {
            errors.rejectValue(ACTIVITY_TYPE_PROPERTY, CANNOT_BE_NULL);
        }
        if (isBlank(activity.getLabel())) {
            errors.rejectValue("label", CANNOT_BE_BLANK);
        }
        if (isBlank(activity.getRef())) {
            errors.rejectValue(REF_PROPERTY, CANNOT_BE_BLANK);
        }
        if (activity.getActivityType() == ActivityType.SURVEY) {
            if (!activity.getRef().startsWith("http://") && !activity.getRef().startsWith("https://")) {
                errors.rejectValue(REF_PROPERTY, "must be an absolute URL to a survey resource API");
            }                    
            // This never should be empty because it is generated from the ref property, which we know is not null.
            if (activity.getSurvey() == null) {
                errors.rejectValue(SURVEY_PROPERTY, CANNOT_BE_NULL);
            } else {
                errors.pushNestedPath(SURVEY_PROPERTY);    
                SurveyReference keys = activity.getSurvey();
                if (isBlank(keys.getGuid())) {
                    errors.rejectValue(GUID_PROPERTY, CANNOT_BE_BLANK);
                }
                if (!activity.getRef().contains(keys.getGuid())) {
                    errors.rejectValue(GUID_PROPERTY, "does not match the URL for this activity");
                }
                if (keys.getCreatedOn() != null && !activity.getRef().contains(keys.getCreatedOn())) {
                    errors.rejectValue(CREATED_ON_PROPERTY, "does not match the URL for this activity");
                }
                errors.popNestedPath();    
            }
        }
        
    }
}
