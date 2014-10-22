package org.sagebionetworks.bridge.validators;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class SchedulePlanValidator implements Validator {

    @Override
    public boolean supports(Class<?> clazz) {
        return SchedulePlan.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object obj, Errors errors) {
        SchedulePlan plan = (SchedulePlan)obj;
        if (StringUtils.isBlank(plan.getStudyKey())) {
            errors.rejectValue("studyKey", "missing a study key");
        }
        if (plan.getStrategy() == null) {
            errors.rejectValue("strategy", "requires a strategy object");
        } else {
            plan.getStrategy().validate(errors);
        }
    }

}
