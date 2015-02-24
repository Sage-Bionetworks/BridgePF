package org.sagebionetworks.bridge.validators;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.models.PasswordReset;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

@Component
public class PasswordResetValidator implements Validator {

    @Override
    public boolean supports(Class<?> clazz) {
        return PasswordReset.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        PasswordReset passwordReset = (PasswordReset)object;
        
        if (StringUtils.isBlank(passwordReset.getSptoken())) {
            errors.rejectValue("sptoken", "required");
        } else if (StringUtils.isBlank(passwordReset.getPassword())) {
            errors.rejectValue("password", "required");
        }
    }

}
