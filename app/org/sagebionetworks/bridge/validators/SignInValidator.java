package org.sagebionetworks.bridge.validators;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.models.SignIn;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class SignInValidator implements Validator {

    @Override
    public boolean supports(Class<?> clazz) {
        return SignIn.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        SignIn signIn = (SignIn)object;
        
        if (StringUtils.isBlank(signIn.getUsername())) {
            errors.rejectValue("username", "required");
        }
        if (StringUtils.isBlank(signIn.getPassword())) {
            errors.rejectValue("password", "required");
        }
        
    }

}
