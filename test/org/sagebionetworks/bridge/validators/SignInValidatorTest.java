package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;

import org.junit.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.models.accounts.SignIn;

public class SignInValidatorTest {
    
    private static final String TOKEN = "token";
    private static final String PASSWORD = "password";
    private static final String EMAIL = "email@email.com";
    private static final String REAUTH_TOKEN = "reauthToken";
    
    @Test
    public void emailSignInRequestOK() {
        SignIn signIn = new SignIn.Builder().withStudy(TestConstants.TEST_STUDY_IDENTIFIER).withEmail(EMAIL).build();
        Validate.entityThrowingException(SignInValidator.EMAIL_SIGNIN_REQUEST, signIn);
    }
    
    @Test
    public void emailSignInRequestValidates() {
        SignIn signIn = new SignIn.Builder().build();
        assertValidatorMessage(SignInValidator.EMAIL_SIGNIN_REQUEST, signIn, "study", "is required");
        assertValidatorMessage(SignInValidator.EMAIL_SIGNIN_REQUEST, signIn, "email", "is required");
    }
    
    @Test
    public void emailSignInOK() {
        SignIn signIn = new SignIn.Builder().withStudy(TestConstants.TEST_STUDY_IDENTIFIER).withEmail(EMAIL)
                .withToken(TOKEN).build();
        Validate.entityThrowingException(SignInValidator.EMAIL_SIGNIN, signIn);
    }
    
    @Test
    public void emailSignInValidates() {
        SignIn signIn = new SignIn.Builder().build();
        assertValidatorMessage(SignInValidator.EMAIL_SIGNIN, signIn, "study", "is required");
        assertValidatorMessage(SignInValidator.EMAIL_SIGNIN, signIn, "email", "is required");
        assertValidatorMessage(SignInValidator.EMAIL_SIGNIN, signIn, "token", "is required");
    }
    
    @Test
    public void passwordSignInOK() {
        SignIn signIn = new SignIn.Builder().withStudy(TestConstants.TEST_STUDY_IDENTIFIER).withEmail(EMAIL)
                .withPassword(PASSWORD).build();
        Validate.entityThrowingException(SignInValidator.PASSWORD_SIGNIN, signIn);
    }
    
    @Test
    public void passwordSignInValidates() {
        SignIn signIn = new SignIn.Builder().build();
        assertValidatorMessage(SignInValidator.PASSWORD_SIGNIN, signIn, "study", "is required");
        assertValidatorMessage(SignInValidator.PASSWORD_SIGNIN, signIn, "email", "is required");
        assertValidatorMessage(SignInValidator.PASSWORD_SIGNIN, signIn, "password", "is required");
    }
    
    @Test
    public void reauthenticationValidates() {
        SignIn signIn = new SignIn.Builder().withStudy(TestConstants.TEST_STUDY_IDENTIFIER).withEmail(EMAIL)
                .withReauthToken(REAUTH_TOKEN).build();
        Validate.entityThrowingException(SignInValidator.REAUTHENTICATION_REQUEST, signIn);
    }
    
    @Test
    public void reauthenticationTokenValidated() {
        SignIn signIn = new SignIn.Builder().build();
        assertValidatorMessage(SignInValidator.REAUTHENTICATION_REQUEST, signIn, "study", "is required");
        assertValidatorMessage(SignInValidator.REAUTHENTICATION_REQUEST, signIn, "email", "is required");
        assertValidatorMessage(SignInValidator.REAUTHENTICATION_REQUEST, signIn, "reauthToken", "is required");
    }
}
