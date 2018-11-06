package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.models.substudies.Substudy;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class SubstudyValidator implements Validator {
    public static final SubstudyValidator INSTANCE = new SubstudyValidator();

    @Override
    public boolean supports(Class<?> clazz) {
        return Substudy.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        Substudy substudy = (Substudy)object;
        
        if (isBlank(substudy.getId())) {
            errors.rejectValue("id", "is required");
        } else if (!substudy.getId().matches(BridgeConstants.BRIDGE_EVENT_ID_PATTERN)) {
            errors.rejectValue("id", BridgeConstants.BRIDGE_EVENT_ID_ERROR);
        }
        if (isBlank(substudy.getStudyId())) {
            errors.rejectValue("studyId", "is required");
        }
        if (isBlank(substudy.getName())) {
            errors.rejectValue("name", "is required");
        }
    }
}
