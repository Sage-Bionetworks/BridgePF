package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;

import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

@Component
public class SchedulePlanValidator implements Validator {

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
            plan.getStrategy().validate(errors);
            errors.popNestedPath();
        }
    }

}
