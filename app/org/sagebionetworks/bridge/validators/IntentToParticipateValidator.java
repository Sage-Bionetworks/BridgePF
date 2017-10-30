package org.sagebionetworks.bridge.validators;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.models.itp.IntentToParticipate;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class IntentToParticipateValidator implements Validator {

    public static final IntentToParticipateValidator INSTANCE = new IntentToParticipateValidator();
    
    private IntentToParticipateValidator() { 
    }
    
    public boolean supports(Class<?> clazz) {
        return IntentToParticipate.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        IntentToParticipate itp = (IntentToParticipate)object;
        
        if (StringUtils.isBlank(itp.getStudy())) {
            errors.rejectValue("study", "is required");
        }
        if (StringUtils.isBlank(itp.getSubpopGuid())) {
            errors.rejectValue("subpopGuid", "is required");
        }
        if (itp.getScope() == null) {
            errors.rejectValue("scope", "is required");
        }
        int identifiers = 0;
        if (StringUtils.isNotBlank(itp.getEmail())) {
            identifiers++;
        }
        if (StringUtils.isNotBlank(itp.getPhone())) {
            identifiers++;
        }
        if (identifiers == 0) {
            errors.reject("must include email or phone");
        } else if (identifiers > 1) {
            errors.reject("must include email or phone, but not both");
        }
        if (itp.getConsentSignature() == null) {
            errors.rejectValue("consentSignature", "is required");
        } else {
            // consent signature is validated during construction, which
            // prevents us from doing this here
        }
    }

}
