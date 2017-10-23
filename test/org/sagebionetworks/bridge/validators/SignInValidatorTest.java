package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;

import org.junit.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.models.accounts.SignIn;

public class SignInValidatorTest {
    
    private static final String STUDY_ID = TestConstants.TEST_STUDY_IDENTIFIER;
    private static final String PHONE = "phone";
    private static final String EMAIL = "email@email.com";
    private static final String TOKEN = "token";
    private static final String PASSWORD = "password";
    private static final String REAUTH_TOKEN = "reauthToken";
    
    @Test
    public void emailSignInRequestOK() {
        SignIn signIn = new SignIn.Builder().withStudy(STUDY_ID).withEmail(EMAIL).build();
        Validate.entityThrowingException(SignInValidator.EMAIL_SIGNIN_REQUEST, signIn);
    }
    @Test
    public void emailSignInRequestNoStudy() {
        SignIn signIn = new SignIn.Builder().withEmail(EMAIL).build();
        assertValidatorMessage(SignInValidator.EMAIL_SIGNIN_REQUEST, signIn, "study", "is required");
    }
    @Test
    public void emailSignInRequestNoEmail() {
        SignIn signIn = new SignIn.Builder().withStudy(STUDY_ID).build();
        assertValidatorMessage(SignInValidator.EMAIL_SIGNIN_REQUEST, signIn, "email", "is required");
    }
    @Test
    public void phoneSignInRequestOK() {
        SignIn signIn = new SignIn.Builder().withStudy(STUDY_ID).withPhone(PHONE).build();
        Validate.entityThrowingException(SignInValidator.PHONE_SIGNIN_REQUEST, signIn);
    }
    @Test
    public void phoneSignInRequestNoStudy() {
        SignIn signIn = new SignIn.Builder().withPhone(PHONE).build();
        assertValidatorMessage(SignInValidator.PHONE_SIGNIN_REQUEST, signIn, "study", "is required");
    }
    @Test
    public void phoneSignInRequestNoPhone() {
        SignIn signIn = new SignIn.Builder().withStudy(STUDY_ID).build();
        assertValidatorMessage(SignInValidator.PHONE_SIGNIN_REQUEST, signIn, "phone", "is required");
    }
    @Test
    public void tokenSignInOK() {
        SignIn signIn = new SignIn.Builder().withToken(TOKEN).build();
        Validate.entityThrowingException(SignInValidator.TOKEN_SIGNIN, signIn);
    }
    @Test
    public void tokenSignInNoToken() {
        SignIn signIn = new SignIn.Builder().build();
        assertValidatorMessage(SignInValidator.TOKEN_SIGNIN, signIn, "token", "is required");
    }
    @Test
    public void emailPasswordSignInOK() {
        SignIn signIn = new SignIn.Builder().withStudy(STUDY_ID).withEmail(EMAIL).withPassword(PASSWORD).build();
        Validate.entityThrowingException(SignInValidator.EMAIL_PASSWORD_SIGNIN, signIn);
    }
    @Test
    public void emailPasswordSignInNoStudy() {
        SignIn signIn = new SignIn.Builder().withEmail(EMAIL).withPassword(PASSWORD).build();
        assertValidatorMessage(SignInValidator.EMAIL_PASSWORD_SIGNIN, signIn, "study", "is required");
    }
    @Test
    public void emailPasswordSignInNoEmail() {
        SignIn signIn = new SignIn.Builder().withStudy(STUDY_ID).withPassword(PASSWORD).build();
        assertValidatorMessage(SignInValidator.EMAIL_PASSWORD_SIGNIN, signIn, "email", "is required");
    }
    @Test
    public void emailPasswordSignInNoPassword() {
        SignIn signIn = new SignIn.Builder().withStudy(STUDY_ID).withEmail(EMAIL).build();
        assertValidatorMessage(SignInValidator.EMAIL_PASSWORD_SIGNIN, signIn, "password", "is required");
    }
    @Test
    public void reauthSignInOK() {
        SignIn signIn = new SignIn.Builder().withReauthToken(REAUTH_TOKEN).build();
        Validate.entityThrowingException(SignInValidator.REAUTH_SIGNIN, signIn);
    }
    @Test
    public void reauthSignInOKNoReauthToken() {
        SignIn signIn = new SignIn.Builder().build();
        assertValidatorMessage(SignInValidator.REAUTH_SIGNIN, signIn, "reauthToken", "is required");
    }    
}
