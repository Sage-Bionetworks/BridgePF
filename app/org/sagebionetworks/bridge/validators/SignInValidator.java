package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.EnumSet;

import static com.google.common.base.Preconditions.checkNotNull;

import org.sagebionetworks.bridge.models.accounts.SignIn;

import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

public class SignInValidator implements Validator {

    /** Sign in using email and password. */
    public static final SignInValidator EMAIL_PASSWORD_SIGNIN = new SignInValidator(Type.EMAIL_PASSWORD_SIGNIN);
    /** Request a token for sign in via a link in an email message. */
    public static final SignInValidator EMAIL_SIGNIN_REQUEST = new SignInValidator(Type.EMAIL_SIGNIN_REQUEST);
    /** Request a token for sign in via SMS. */
    public static final SignInValidator PHONE_SIGNIN_REQUEST = new SignInValidator(Type.PHONE_SIGNIN_REQUEST);
    /** Token-based reauthentication after an initial authentication attempt. */
    public static final SignInValidator REAUTH_SIGNIN = new SignInValidator(Type.REAUTH_SIGNIN);
    /** Token-based authentication, using a token sent through an authentication channel like emai or SMS. */
    public static final SignInValidator TOKEN_SIGNIN = new SignInValidator(Type.TOKEN_SIGNIN);
    
    private static enum Type {
        EMAIL_PASSWORD_SIGNIN,
        EMAIL_SIGNIN_REQUEST,
        PHONE_SIGNIN_REQUEST,
        REAUTH_SIGNIN,
        TOKEN_SIGNIN;
        
        private static EnumSet<Type> EMAIL_REQUIRED = EnumSet.of(EMAIL_SIGNIN_REQUEST, EMAIL_PASSWORD_SIGNIN);
        private static EnumSet<Type> STUDY_REQUIRED = EnumSet.of(EMAIL_SIGNIN_REQUEST, PHONE_SIGNIN_REQUEST,
                EMAIL_PASSWORD_SIGNIN);
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
        
        if (isBlank(signIn.getStudyId()) && Type.STUDY_REQUIRED.contains(type)) {
            errors.rejectValue("study", "is required");
        }
        if (isBlank(signIn.getEmail()) && Type.EMAIL_REQUIRED.contains(type)) {
            errors.rejectValue("email", "is required");
        }
        if (isBlank(signIn.getToken()) && type == Type.TOKEN_SIGNIN) {
            errors.rejectValue("token", "is required");
        }
        if (isBlank(signIn.getPassword()) && type == Type.EMAIL_PASSWORD_SIGNIN) {
            errors.rejectValue("password", "is required");
        }
        if (isBlank(signIn.getPhone()) && type == Type.PHONE_SIGNIN_REQUEST) {
            errors.rejectValue("phone", "is required");
        }
        if (isBlank(signIn.getReauthToken()) && type == Type.REAUTH_SIGNIN) {
            errors.rejectValue("reauthToken", "is required");
        }
    }

}
