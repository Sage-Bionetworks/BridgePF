package org.sagebionetworks.bridge.validators;

import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.EmailValidator;

import org.sagebionetworks.bridge.models.accounts.DataGroups;
import org.sagebionetworks.bridge.models.accounts.SignUp;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class SignUpValidator implements Validator {
    
    private final EmailValidator emailValidator = EmailValidator.getInstance();
    
    private final PasswordPolicy passwordPolicy;
    private final Set<String> dataGroups;
    
    public SignUpValidator(PasswordPolicy passwordPolicy, Set<String> dataGroups) {
        this.passwordPolicy = passwordPolicy;
        this.dataGroups = dataGroups;
    }
    
    @Override
    public boolean supports(Class<?> clazz) {
        return SignUp.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        SignUp signUp = (SignUp) object;

        if (StringUtils.isBlank(signUp.getEmail())) {
            errors.rejectValue("email", "is required");
        } else if (!emailValidator.isValid(signUp.getEmail())){
            errors.rejectValue("email", "must be a valid email address");
        }
        if (StringUtils.isBlank(signUp.getUsername())) {
            errors.rejectValue("username", "is required");
        }
        if (StringUtils.isBlank(signUp.getPassword())) {
            errors.rejectValue("password", "is required");
            return;
        }
        // validate password
        String password = signUp.getPassword();
        if (passwordPolicy.getMinLength() > 0 && password.length() < passwordPolicy.getMinLength()) {
            errors.rejectValue("password", "must be at least "+passwordPolicy.getMinLength()+" characters");
        }
        if (passwordPolicy.isNumericRequired() && !password.matches(".*\\d+.*")) {
            errors.rejectValue("password", "must contain at least one number (0-9)");
        }
        if (passwordPolicy.isSymbolRequired() && !password.matches(".*\\p{Punct}+.*")) {
            errors.rejectValue("password", "must contain at least one symbol ( !\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~ )");
        }
        if (passwordPolicy.isLowerCaseRequired() && !password.matches(".*[a-z]+.*")) {
            errors.rejectValue("password", "must contain at least one lowercase letter (a-z)");
        }
        if (passwordPolicy.isUpperCaseRequired() && !password.matches(".*[A-Z]+.*")) {
            errors.rejectValue("password", "must contain at least one uppercase letter (A-Z)");
        }
        new DataGroupsValidator(dataGroups).validate(new DataGroups(signUp.getDataGroups()), errors);
    }

}
