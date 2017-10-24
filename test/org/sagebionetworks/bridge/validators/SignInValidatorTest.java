package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;

import org.junit.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.models.accounts.SignIn;

public class SignInValidatorTest {
    
    private static final String STUDY_ID = TestConstants.TEST_STUDY_IDENTIFIER;
    private static final String TOKEN = "token";
    private static final String PASSWORD = "password";
    private static final String EMAIL = "email@email.com";
    private static final String PHONE = "+1234567890";
    private static final String REAUTH_TOKEN = "reauthToken";
    private static final SignIn EMPTY_SIGNIN = new SignIn.Builder().build();

    @Test
    public void emailSignInRequestOK() {
        SignIn signIn = new SignIn.Builder().withStudy(STUDY_ID).withEmail(EMAIL).build();
        Validate.entityThrowingException(SignInValidator.EMAIL_SIGNIN_REQUEST, signIn);
    }
    @Test
    public void emailSignInRequestInvalid() {
        assertValidatorMessage(SignInValidator.EMAIL_SIGNIN_REQUEST, EMPTY_SIGNIN, "study", "is required");
        assertValidatorMessage(SignInValidator.EMAIL_SIGNIN_REQUEST, EMPTY_SIGNIN, "email", "is required");
    }
    @Test
    public void emailSignInOK() {
        SignIn signIn = new SignIn.Builder().withStudy(STUDY_ID).withEmail(EMAIL).withToken(TOKEN).build();
        Validate.entityThrowingException(SignInValidator.EMAIL_SIGNIN, signIn);
    }
    @Test
    public void emailSignInInvalid() {
        assertValidatorMessage(SignInValidator.EMAIL_SIGNIN, EMPTY_SIGNIN, "study", "is required");
        assertValidatorMessage(SignInValidator.EMAIL_SIGNIN, EMPTY_SIGNIN, "email", "is required");
        assertValidatorMessage(SignInValidator.EMAIL_SIGNIN, EMPTY_SIGNIN, "token", "is required");
    }
    @Test
    public void phoneSignInRequestOK() {
        SignIn signIn = new SignIn.Builder().withStudy(STUDY_ID).withPhone(PHONE).build();
        Validate.entityThrowingException(SignInValidator.PHONE_SIGNIN_REQUEST, signIn);
    }
    @Test
    public void phoneSignInRequestInvalid() {
        assertValidatorMessage(SignInValidator.PHONE_SIGNIN_REQUEST, EMPTY_SIGNIN, "study", "is required");
        assertValidatorMessage(SignInValidator.PHONE_SIGNIN_REQUEST, EMPTY_SIGNIN, "phone", "is required");
    }
    @Test
    public void phoneSignInOK() {
        SignIn signIn = new SignIn.Builder().withStudy(STUDY_ID).withPhone(PHONE).withToken(TOKEN).build();
        Validate.entityThrowingException(SignInValidator.PHONE_SIGNIN, signIn);
    }
    @Test
    public void phoneSignInNoStudy() {
        assertValidatorMessage(SignInValidator.PHONE_SIGNIN, EMPTY_SIGNIN, "study", "is required");
        assertValidatorMessage(SignInValidator.PHONE_SIGNIN, EMPTY_SIGNIN, "phone", "is required");
        assertValidatorMessage(SignInValidator.PHONE_SIGNIN, EMPTY_SIGNIN, "token", "is required");
    }
    @Test
    public void passwordSignInOK() {
        SignIn signIn = new SignIn.Builder().withStudy(STUDY_ID).withEmail(EMAIL).withPassword(PASSWORD).build();
        Validate.entityThrowingException(SignInValidator.PASSWORD_SIGNIN, signIn);
    }
    @Test
    public void passwordSignInInvalid() {
        assertValidatorMessage(SignInValidator.PASSWORD_SIGNIN, EMPTY_SIGNIN, "study", "is required");
        assertValidatorMessage(SignInValidator.PASSWORD_SIGNIN, EMPTY_SIGNIN, "email", "is required");
        assertValidatorMessage(SignInValidator.PASSWORD_SIGNIN, EMPTY_SIGNIN, "password", "is required");
    }
    @Test
    public void reauthOK() {
        SignIn signIn = new SignIn.Builder().withStudy(STUDY_ID).withEmail(EMAIL).withReauthToken(REAUTH_TOKEN).build();
        Validate.entityThrowingException(SignInValidator.REAUTH_SIGNIN, signIn);
    }
    @Test
    public void reauthInvalid() {
        assertValidatorMessage(SignInValidator.REAUTH_SIGNIN, EMPTY_SIGNIN, "study", "is required");
        assertValidatorMessage(SignInValidator.REAUTH_SIGNIN, EMPTY_SIGNIN, "email", "is required");
        assertValidatorMessage(SignInValidator.REAUTH_SIGNIN, EMPTY_SIGNIN, "reauthToken", "is required");
    }
}
