package org.sagebionetworks.bridge.validators;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.EmailValidator;
import org.sagebionetworks.bridge.models.SignUp;
import org.sagebionetworks.bridge.services.AuthenticationService;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class SignUpValidator implements Validator {
    
    private EmailValidator emailValidator = EmailValidator.getInstance();
    private final AuthenticationService authService;
    
    public SignUpValidator(AuthenticationService authService) {
        this.authService = authService;
    }
    
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
        if (!errors.hasErrors()) {
            // Check both the email and the user name. Both must be unique
            String fieldTaken = authService.isAccountInUse(signUp.getUsername(), signUp.getEmail());
            if (fieldTaken != null) {
                errors.rejectValue(fieldTaken, "has already been registered");
            }
        }
        if (StringUtils.isBlank(signUp.getUsername())) {
            errors.rejectValue("username", "null or blank");
        }
        if (StringUtils.isBlank(signUp.getPassword())) {
            errors.rejectValue("password", "null or blank");
        }
    }

}
