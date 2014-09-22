package org.sagebionetworks.bridge.validators;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.exceptions.EntityAlreadyExistsException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;

public class SchedulePlanValidator implements Validator<SchedulePlan> {

    @Override
    public void validateNew(SchedulePlan plan) throws InvalidEntityException, EntityAlreadyExistsException {
        if (StringUtils.isNotBlank(plan.getGuid())) {
            throw new EntityAlreadyExistsException(plan, "Plan appears to exist, it has been assigned a GUID");
        }
    }

    @Override
    public void validate(SchedulePlan plan) throws InvalidEntityException {
        Messages messages = new Messages();
        if (StringUtils.isBlank(plan.getStudyKey())) {
            messages.add("missing a study key");
        }
        if (StringUtils.isBlank(plan.getStrategyType())) {
            messages.add("missing a strategy type class name");
        }
        if (plan.getStrategy() == null) {
            messages.add("requires a strategy object");
        }
        if (!messages.isEmpty()) {
            throw new InvalidEntityException(plan, "Schedule plan is invalid: " + messages.join());
        }
    }

}
