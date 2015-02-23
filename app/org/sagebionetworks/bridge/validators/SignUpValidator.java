package org.sagebionetworks.bridge.validators;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.EmailValidator;
import org.sagebionetworks.bridge.models.SignUp;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

@Component
public class SignUpValidator implements Validator {
    
    private EmailValidator emailValidator = EmailValidator.getInstance();
    
    @Override
    public boolean supports(Class<?> clazz) {
        return SignUp.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        SignUp signUp = (SignUp) object;

        if (StringUtils.isBlank(signUp.getEmail())) {
            errors.rejectValue("email", "null or blank");
        } else if (!emailValidator.isValid(signUp.getEmail())){
            errors.rejectValue("email", "not a valid email address");
        }
        if (StringUtils.isBlank(signUp.getUsername())) {
            errors.rejectValue("username", "null or blank");
        }
        if (StringUtils.isBlank(signUp.getPassword())) {
            errors.rejectValue("password", "null or blank");
        }
    }

}
