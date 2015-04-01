package org.sagebionetworks.bridge.validators;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.models.EmailVerification;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

@Component
public class EmailVerificationValidator implements Validator {
    
    @Override
    public boolean supports(Class<?> clazz) {
        return EmailVerification.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object obj, Errors errors) {
        EmailVerification email = (EmailVerification)obj;

        if (StringUtils.isBlank(email.getSptoken())) {
            errors.rejectValue("sptoken", "required");
        }
    }

}
