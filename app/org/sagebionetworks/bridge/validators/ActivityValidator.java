package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.Set;

import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.SurveyReference;
import org.sagebionetworks.bridge.models.schedules.SurveyResponseReference;
import org.sagebionetworks.bridge.models.schedules.TaskReference;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import com.google.common.base.Joiner;

public class ActivityValidator implements Validator {
    
    private static final String CANNOT_BE_BLANK = "cannot be missing, null, or blank";
    
    private final Set<String> taskIdentifiers;
    
    public ActivityValidator(Set<String> taskIdentifiers) {
        this.taskIdentifiers = taskIdentifiers;
    }
    
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
            if (activity.getSurvey() != null) {
                validate(errors, activity.getSurvey());
            } else {
                errors.reject("has a survey response, so it must also reference the survey");
            }
            validate(errors, activity.getSurveyResponse());
        } else {
            validate(errors, activity.getSurvey());
        }
    }
    
    private void validate(Errors errors, TaskReference ref) {
        errors.pushNestedPath("task");
        if (isBlank(ref.getIdentifier())) {
            errors.rejectValue("identifier", CANNOT_BE_BLANK);
        } else if (taskIdentifiers == null || !taskIdentifiers.contains(ref.getIdentifier())) {
            errors.rejectValue("identifier", geTaskIdentifierMessage(ref));
        }
        errors.popNestedPath();
    }
   
    private void validate(Errors errors, SurveyReference ref) {
        errors.pushNestedPath("survey");
        if (isBlank(ref.getGuid())) {
            errors.rejectValue("guid", CANNOT_BE_BLANK);
        }
        errors.popNestedPath();
    }
    
    private void validate(Errors errors, SurveyResponseReference ref) {
        errors.pushNestedPath("surveyResponse");
        if (isBlank(ref.getIdentifier())) {
            errors.rejectValue("identifier", CANNOT_BE_BLANK);
        }
        errors.popNestedPath();
    }
    
    private String geTaskIdentifierMessage(TaskReference ref) {
        String message = "'" + ref.getIdentifier() + "' is not in enumeration: ";
        if (taskIdentifiers != null && !taskIdentifiers.isEmpty()) {
            message += Joiner.on(", ").join(taskIdentifiers);
        } else {
            message += "<no task identifiers declared>";
        }
        return message;
    }
}
