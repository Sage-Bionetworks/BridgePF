package org.sagebionetworks.bridge.validators;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

@Component
public class SignInValidator implements Validator {

    @Override
    public boolean supports(Class<?> clazz) {
        return SignIn.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        SignIn signIn = (SignIn)object;
        
        if (StringUtils.isBlank(signIn.getEmail())) {
            errors.rejectValue("email", "required");
        }
        if (StringUtils.isBlank(signIn.getPassword())) {
            errors.rejectValue("password", "required");
        }
        
    }

}
