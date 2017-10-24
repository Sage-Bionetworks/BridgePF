package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;

import org.sagebionetworks.bridge.models.accounts.EmailVerification;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

@Component
public class EmailVerificationValidator implements Validator {
    
    public static final EmailVerificationValidator INSTANCE = new EmailVerificationValidator();

    @Override
    public boolean supports(Class<?> clazz) {
        return EmailVerification.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object obj, Errors errors) {
        EmailVerification email = (EmailVerification)obj;

        if (isBlank(email.getSpToken())) {
            errors.rejectValue("sptoken", "is required");
        }
    }

}
