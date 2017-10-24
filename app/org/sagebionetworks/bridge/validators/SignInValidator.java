package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.validators.SignInValidator.RequiredFields.STUDY;
import static org.sagebionetworks.bridge.validators.SignInValidator.RequiredFields.EMAIL;
import static org.sagebionetworks.bridge.validators.SignInValidator.RequiredFields.PASSWORD;
import static org.sagebionetworks.bridge.validators.SignInValidator.RequiredFields.PHONE;
import static org.sagebionetworks.bridge.validators.SignInValidator.RequiredFields.TOKEN;
import static org.sagebionetworks.bridge.validators.SignInValidator.RequiredFields.REAUTH;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

import org.sagebionetworks.bridge.models.accounts.SignIn;

import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import com.google.common.collect.Sets;

public class SignInValidator implements Validator {
    
    /** Request to sign in via email. */
    public static final SignInValidator EMAIL_SIGNIN_REQUEST = new SignInValidator(EnumSet.of(STUDY, EMAIL));
    /** Sign in using token sent through email. */    
    public static final SignInValidator EMAIL_SIGNIN = new SignInValidator(EnumSet.of(STUDY, EMAIL, TOKEN));

    /** Request to sign in via phone. */
    public static final SignInValidator PHONE_SIGNIN_REQUEST = new SignInValidator(EnumSet.of(STUDY, PHONE));
    /** Sign in using token sent through SMS. */
    public static final SignInValidator PHONE_SIGNIN = new SignInValidator(EnumSet.of(STUDY, PHONE, TOKEN));

    /** Sign in using an email and password. */
    public static final SignInValidator PASSWORD_SIGNIN = new SignInValidator(EnumSet.of(STUDY, EMAIL, PASSWORD));
    /** Reauthentication. */
    public static final SignInValidator REAUTH_SIGNIN = new SignInValidator(EnumSet.of(STUDY, EMAIL, REAUTH));
    
    static enum RequiredFields {
        STUDY,
        EMAIL,
        PASSWORD,
        PHONE,
        TOKEN,
        REAUTH
    }
    
    private final EnumSet<RequiredFields> requiredFields;

    private SignInValidator(EnumSet<RequiredFields> fields) {
        requiredFields = fields;
    }
    
    @Override
    public boolean supports(Class<?> clazz) {
        return SignIn.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        SignIn signIn = (SignIn)object;
        
        if (requiredFields.contains(STUDY) && isBlank(signIn.getStudyId())) {
            errors.rejectValue("study", "is required");
        }
        if (requiredFields.contains(EMAIL) && isBlank(signIn.getEmail())) {
            errors.rejectValue("email", "is required");
        }
        if (requiredFields.contains(PASSWORD) && isBlank(signIn.getPassword())) {
            errors.rejectValue("password", "is required");
        }
        if (requiredFields.contains(PHONE) && isBlank(signIn.getPhone())) {
            errors.rejectValue("phone", "is required");
        }
        if (requiredFields.contains(TOKEN) && isBlank(signIn.getToken())) {
            errors.rejectValue("token", "is required");
        }
        if (requiredFields.contains(REAUTH) && isBlank(signIn.getReauthToken())) {
            errors.rejectValue("reauthToken", "is required");
        }
    }

}
