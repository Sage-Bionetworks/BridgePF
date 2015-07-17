package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;

import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.SurveyReference;
import org.sagebionetworks.bridge.models.schedules.SurveyResponseReference;
import org.sagebionetworks.bridge.models.schedules.TaskReference;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class ActivityValidator implements Validator {
    
    private static final String CANNOT_BE_BLANK = "cannot be missing, null, or blank";
    
    @Override
    public boolean supports(Class<?> clazz) {
        return Activity.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object obj, Errors errors) {
        Activity activity = (Activity)obj;
        
        if (isBlank(activity.getLabel())) {
            errors.rejectValue("label", CANNOT_BE_BLANK);
        }
        if (activity.getTask() == null && activity.getSurvey() == null && activity.getSurveyResponse() == null) {
            errors.reject("Activity must have a task, survey, and/or a survey response");
            return;
        }
        if (activity.getTask() != null) {
            validate(errors, activity.getTask());
        } else if (activity.getSurveyResponse() != null) {
            if (activity.getSurvey() == null) {
                errors.reject("has a survey reference, so it must also reference the survey");
                return;
            }
            validate(errors, activity.getSurvey());
            validate(errors, activity.getSurveyResponse());
        } else {
            validate(errors, activity.getSurvey());
        }
    }
    
    private void validate(Errors errors, TaskReference ref) {
        errors.pushNestedPath("task");
        if (isBlank(ref.getIdentifier())) {
            errors.rejectValue("identifier", CANNOT_BE_BLANK);
        }
        errors.popNestedPath();
    }
    
    private void validate(Errors errors, SurveyReference ref) {
        errors.pushNestedPath("survey");
        if (isBlank(ref.getIdentifier())) {
            errors.rejectValue("identifier", CANNOT_BE_BLANK);
        }
        if (isBlank(ref.getGuid())) {
            errors.rejectValue("guid", CANNOT_BE_BLANK);
        }
        errors.popNestedPath();
    }
    
    private void validate(Errors errors, SurveyResponseReference ref) {
        errors.pushNestedPath("surveyResponse");
        if (isBlank(ref.getGuid())) {
            errors.rejectValue("guid", CANNOT_BE_BLANK);
        }
        errors.popNestedPath();
    }
    
}
