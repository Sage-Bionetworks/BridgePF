package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.validators.SignInValidator.RequiredFields.STUDY;
import static org.sagebionetworks.bridge.validators.SignInValidator.RequiredFields.EMAIL;
import static org.sagebionetworks.bridge.validators.SignInValidator.RequiredFields.EMAIL_OR_PHONE;
import static org.sagebionetworks.bridge.validators.SignInValidator.RequiredFields.PASSWORD;
import static org.sagebionetworks.bridge.validators.SignInValidator.RequiredFields.PHONE;
import static org.sagebionetworks.bridge.validators.SignInValidator.RequiredFields.TOKEN;
import static org.sagebionetworks.bridge.validators.SignInValidator.RequiredFields.REAUTH;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.EnumSet;

import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.SignIn;

import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class SignInValidator implements Validator {
    
    /** Request to sign in via email. */
    public static final SignInValidator EMAIL_SIGNIN_REQUEST = new SignInValidator(EnumSet.of(STUDY, EMAIL));
    /** Sign in using token sent through email. */    
    public static final SignInValidator EMAIL_SIGNIN = new SignInValidator(EnumSet.of(STUDY, EMAIL, TOKEN));

    /** Request to sign in via phone. */
    public static final SignInValidator PHONE_SIGNIN_REQUEST = new SignInValidator(EnumSet.of(STUDY, PHONE));
    /** Sign in using token sent through SMS. */
    public static final SignInValidator PHONE_SIGNIN = new SignInValidator(EnumSet.of(STUDY, PHONE, TOKEN));

    /** Request a reset password link via email or SMS. */
    public static final SignInValidator REQUEST_RESET_PASSWORD = new SignInValidator(EnumSet.of(STUDY, EMAIL_OR_PHONE));
    
    /** The basics of a sign in that must be present for the admin create user API. */
    public static final SignInValidator MINIMAL = new SignInValidator(EnumSet.of(STUDY, EMAIL_OR_PHONE));
    
    /** Sign in using an email and password. */
    public static final SignInValidator PASSWORD_SIGNIN = new SignInValidator(EnumSet.of(STUDY, EMAIL_OR_PHONE, PASSWORD));
    /** Reauthentication. */
    public static final SignInValidator REAUTH_SIGNIN = new SignInValidator(EnumSet.of(STUDY, EMAIL_OR_PHONE, REAUTH));
    
    static enum RequiredFields {
        STUDY,
        EMAIL,
        EMAIL_OR_PHONE,
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
        if (requiredFields.contains(EMAIL_OR_PHONE)) {
            if (isBlank(signIn.getEmail()) && signIn.getPhone() == null) {
                errors.reject("email or phone is required");
            }
            if (signIn.getPhone() != null && !Phone.isValid(signIn.getPhone())) {
                errors.rejectValue("phone", "does not appear to be a phone number");
            }
        }
        if (requiredFields.contains(TOKEN) && isBlank(signIn.getToken())) {
            errors.rejectValue("token", "is required");
        }
        if (requiredFields.contains(REAUTH) && isBlank(signIn.getReauthToken())) {
            errors.rejectValue("reauthToken", "is required");
        }
        if (requiredFields.contains(PHONE)) {
            if (signIn.getPhone() == null) {
                errors.rejectValue("phone", "is required");
            } else if (!Phone.isValid(signIn.getPhone())) {
                errors.rejectValue("phone", "does not appear to be a phone number");
            }
        }
    }

}
