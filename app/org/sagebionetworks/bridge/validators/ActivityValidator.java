package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.sagebionetworks.bridge.BridgeUtils.COMMA_SPACE_JOINER;

import java.util.Collections;
import java.util.Set;

import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.SurveyReference;
import org.sagebionetworks.bridge.models.schedules.TaskReference;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class ActivityValidator implements Validator {
    
    private static final String CANNOT_BE_BLANK = "cannot be missing, null, or blank";
    
    private final Set<String> taskIdentifiers;
    
    public ActivityValidator(Set<String> taskIdentifiers) {
        this.taskIdentifiers = (taskIdentifiers == null) ? Collections.emptySet() : taskIdentifiers;
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
        if (activity.getTask() != null) {
            validate(errors, activity.getTask());
        } else if (activity.getSurvey() != null){
            validate(errors, activity.getSurvey());
        } else {
            errors.rejectValue("", "must have a task or survey reference");
        }
    }
    
    private void validate(Errors errors, TaskReference ref) {
        errors.pushNestedPath("task");
        if (isBlank(ref.getIdentifier())) {
            errors.rejectValue("identifier", CANNOT_BE_BLANK);
        } else if (!taskIdentifiers.contains(ref.getIdentifier())) {
            errors.rejectValue("identifier", getTaskIdentifierMessage(ref));
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
    
    private String getTaskIdentifierMessage(TaskReference ref) {
        String message = "'" + ref.getIdentifier() + "' is not in enumeration: ";
        if (taskIdentifiers.isEmpty()) {
            message += "<no task identifiers declared>";
        } else {
            message += COMMA_SPACE_JOINER.join(taskIdentifiers) + ".";
        }
        return message;
    }
}
