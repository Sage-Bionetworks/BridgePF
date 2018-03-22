package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;

import org.sagebionetworks.bridge.models.accounts.Verification;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

@Component
public class VerificationValidator implements Validator {
    
    public static final VerificationValidator INSTANCE = new VerificationValidator();

    @Override
    public boolean supports(Class<?> clazz) {
        return Verification.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object obj, Errors errors) {
        Verification email = (Verification)obj;

        if (isBlank(email.getSptoken())) {
            errors.rejectValue("sptoken", "is required");
        }
    }

}
