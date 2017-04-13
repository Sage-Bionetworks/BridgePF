package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;

import org.junit.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.models.accounts.SignIn;

public class SignInValidatorTest {
    
    private static final String TOKEN = "token";
    private static final String PASSWORD = "password";
    private static final String EMAIL = "email@email.com";
    
    @Test
    public void emailSignInRequestOK() {
        SignIn signIn = new SignIn(TestConstants.TEST_STUDY_IDENTIFIER, EMAIL, null, null);
        Validate.entityThrowingException(SignInValidator.EMAIL_SIGNIN_REQUEST, signIn);
    }
    
    @Test
    public void emailSignInRequestValidates() {
        SignIn signIn = new SignIn(null, null, null, null);
        assertValidatorMessage(SignInValidator.EMAIL_SIGNIN_REQUEST, signIn, "study", "is required");
        assertValidatorMessage(SignInValidator.EMAIL_SIGNIN_REQUEST, signIn, "email", "is required");
    }
    
    @Test
    public void emailSignInOK() {
        SignIn signIn = new SignIn(TestConstants.TEST_STUDY_IDENTIFIER, EMAIL, null, TOKEN);
        Validate.entityThrowingException(SignInValidator.EMAIL_SIGNIN, signIn);
    }
    
    @Test
    public void emailSignInValidates() {
        SignIn signIn = new SignIn(null, null, null, null);
        assertValidatorMessage(SignInValidator.EMAIL_SIGNIN, signIn, "study", "is required");
        assertValidatorMessage(SignInValidator.EMAIL_SIGNIN, signIn, "email", "is required");
        assertValidatorMessage(SignInValidator.EMAIL_SIGNIN, signIn, "token", "is required");
    }
    
    @Test
    public void passwordSignInOK() {
        SignIn signIn = new SignIn(TestConstants.TEST_STUDY_IDENTIFIER, EMAIL, PASSWORD, null);
        Validate.entityThrowingException(SignInValidator.PASSWORD_SIGNIN, signIn);
    }
    
    @Test
    public void passwordSignInValidates() {
        SignIn signIn = new SignIn(null, null, null, null);
        assertValidatorMessage(SignInValidator.PASSWORD_SIGNIN, signIn, "study", "is required");
        assertValidatorMessage(SignInValidator.PASSWORD_SIGNIN, signIn, "email", "is required");
        assertValidatorMessage(SignInValidator.PASSWORD_SIGNIN, signIn, "password", "is required");
    }
}
