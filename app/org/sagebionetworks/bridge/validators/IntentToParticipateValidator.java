package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;

import org.sagebionetworks.bridge.models.accounts.Phone;
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
        IntentToParticipate intent = (IntentToParticipate)object;
        
        if (isBlank(intent.getStudy())) {
            errors.rejectValue("study", "is required");
        }
        if (isBlank(intent.getSubpopGuid())) {
            errors.rejectValue("subpopGuid", "is required");
        }
        if (intent.getScope() == null) {
            errors.rejectValue("scope", "is required");
        }
        if (intent.getPhone() == null) {
            errors.rejectValue("phone", "is required");
        } else if (!Phone.isValid(intent.getPhone())) {
            errors.rejectValue("phone", "does not appear to be a phone number");
        }
        if (intent.getConsentSignature() == null) {
            errors.rejectValue("consentSignature", "is required");
        } else {
            // consent signature is validated during construction, which
            // prevents us from doing this here
        }
    }
}
