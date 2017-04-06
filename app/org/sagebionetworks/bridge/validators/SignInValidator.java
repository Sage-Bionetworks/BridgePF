package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static com.google.common.base.Preconditions.checkNotNull;

import org.sagebionetworks.bridge.models.accounts.SignIn;

import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class SignInValidator implements Validator {
    
    public static enum Type {
        PASSWORD,
        EMAIL_REQUEST,
        EMAIL
    }
    
    private Type type;

    public SignInValidator(Type type) {
        checkNotNull(type);
        this.type = type;
    }
    
    @Override
    public boolean supports(Class<?> clazz) {
        return SignIn.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        SignIn signIn = (SignIn)object;
        
        if (isBlank(signIn.getStudyId())) {
            errors.rejectValue("study", "is required");
        }
        if (isBlank(signIn.getEmail())) {
            errors.rejectValue("email", "is required");
        }            
        if (type == Type.PASSWORD) {
            if (isBlank(signIn.getPassword())) {
                errors.rejectValue("password", "required");
            }
        } else if (type == Type.EMAIL) {
            if (isBlank(signIn.getToken())) {
                errors.rejectValue("token", "is required");
            }
        }
    }

}
