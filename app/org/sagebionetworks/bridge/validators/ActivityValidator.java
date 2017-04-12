package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.sagebionetworks.bridge.BridgeUtils.COMMA_SPACE_JOINER;

import java.util.Collections;
import java.util.Set;

import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.CompoundActivity;
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
        if (isBlank(activity.getGuid())) {
            errors.rejectValue("guid", CANNOT_BE_BLANK);   
        }

        // an activity must be exactly one of: compound, task, or survey
        int numSources = 0;

        if (activity.getCompoundActivity() != null) {
            numSources++;
            validate(errors, activity.getCompoundActivity());
        }

        if (activity.getTask() != null) {
            numSources++;
            validate(errors, activity.getTask());
        }

        if (activity.getSurvey() != null){
            numSources++;
            validate(errors, activity.getSurvey());
        }

        if (numSources != 1) {
            errors.rejectValue("activity", "must have exactly one of compound activity, task, or survey");
        }
    }

    private void validate(Errors errors, CompoundActivity compoundActivity) {
        errors.pushNestedPath("compoundActivity");

        // taskIdentifier must be specified and must be in the Study's list
        String taskIdentifier = compoundActivity.getTaskIdentifier();
        if (isBlank(taskIdentifier)) {
            errors.rejectValue("taskIdentifier", CANNOT_BE_BLANK);
        }

        errors.popNestedPath();
    }

    private void validate(Errors errors, TaskReference ref) {
        errors.pushNestedPath("task");

        // taskIdentifier must be specified and must be in the Study's list
        String taskIdentifier = ref.getIdentifier();
        if (isBlank(taskIdentifier)) {
            errors.rejectValue("identifier", CANNOT_BE_BLANK);
        } else if (!taskIdentifiers.contains(taskIdentifier)) {
            errors.rejectValue("identifier", getTaskIdentifierMessage(taskIdentifier));
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
    
    private String getTaskIdentifierMessage(String taskIdentifier) {
        String message = "'" + taskIdentifier + "' is not in enumeration: ";
        if (taskIdentifiers.isEmpty()) {
            message += "<no task identifiers declared>";
        } else {
            message += COMMA_SPACE_JOINER.join(taskIdentifiers);
        }
        return message;
    }
}
