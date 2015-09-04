package org.sagebionetworks.bridge.validators;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.EmailValidator;
import org.sagebionetworks.bridge.models.accounts.SignUp;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class SignUpValidator implements Validator {

    private final EmailValidator emailValidator = EmailValidator.getInstance();

    private final PasswordPolicy passwordPolicy;

    public SignUpValidator(PasswordPolicy passwordPolicy) {
        this.passwordPolicy = passwordPolicy;
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
        } else if (!emailValidator.isValid(signUp.getEmail())) {
            errors.rejectValue("email", "not a valid email address");
        }
        if (StringUtils.isBlank(signUp.getUsername())) {
            errors.rejectValue("username", "null or blank");
        }
        if (StringUtils.isBlank(signUp.getPassword())) {
            errors.rejectValue("password", "null or blank");
            return;
        }
        // Password validation
        String password = signUp.getPassword();
        if (password.length() < passwordPolicy.getMinLength()) {
            errors.rejectValue("password", "must be at least " + passwordPolicy.getMinLength() + " characters");
        }
        if (passwordPolicy.isNumericRequired() && !password.matches(".*\\d+.*")) {
            errors.rejectValue("password", "must have at least one number");
        }
        if (passwordPolicy.isSymbolRequired() && !password.matches(".*\\p{Punct}+.*")) {
            errors.rejectValue("password", "must have at least one symbol (! \" # $ % & ' ( ) * + , - . / : ; < = > ? @ [ \\ ] ^ _ ` { ¦ } ~)");
        }
        if (passwordPolicy.isLowerCaseRequired() && !password.matches(".*[a-z]+.*")) {
            errors.rejectValue("password", "must have at least one lowercase letter (a-z)");
        }
        if (passwordPolicy.isUpperCaseRequired() && !password.matches(".*[A-Z]+.*")) {
            errors.rejectValue("password", "must have at least one uppercase letter (A-Z)");
        }
    }

}
