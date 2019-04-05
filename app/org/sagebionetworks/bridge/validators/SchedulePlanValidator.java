package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.Set;

import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class SchedulePlanValidator implements Validator {

    private final Set<String> dataGroups;
    private final Set<String> substudyIds;
    private final Set<String> taskIdentifiers;
    
    public SchedulePlanValidator(Set<String> dataGroups, Set<String> substudyIds, Set<String> taskIdentifiers) {
        this.dataGroups = dataGroups;
        this.substudyIds = substudyIds;
        this.taskIdentifiers = taskIdentifiers;
    }
    
    @Override
    public boolean supports(Class<?> clazz) {
        return SchedulePlan.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object obj, Errors errors) {
        SchedulePlan plan = (SchedulePlan)obj;
        if (isBlank(plan.getStudyKey())) {
            errors.rejectValue("studyKey", "cannot be missing, null, or blank");
        }
        if (isBlank(plan.getLabel())) {
            errors.rejectValue("label", "cannot be missing, null, or blank");
        }
        if (plan.getStrategy() == null) {
            errors.rejectValue("strategy", "is required");
        } else {
            errors.pushNestedPath("strategy");
            plan.getStrategy().validate(dataGroups, substudyIds, taskIdentifiers, errors);
            errors.popNestedPath();
        }
    }

}
