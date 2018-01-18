package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;

import org.junit.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.SignIn;

public class SignInValidatorTest {
    
    private static final String TOKEN = "token";
    private static final String PASSWORD = "password";
    private static final String EMAIL = "email@email.com";
    private static final String REAUTH_TOKEN = "reauthToken";
    private static final SignIn EMPTY_SIGNIN = new SignIn.Builder().build();

    @Test
    public void emailSignInRequestOK() {
        SignIn signIn = new SignIn.Builder().withStudy(TEST_STUDY_IDENTIFIER).withEmail(EMAIL).build();
        Validate.entityThrowingException(SignInValidator.EMAIL_SIGNIN_REQUEST, signIn);
    }
    @Test
    public void emailSignInRequestInvalid() {
        assertValidatorMessage(SignInValidator.EMAIL_SIGNIN_REQUEST, EMPTY_SIGNIN, "study", "is required");
        assertValidatorMessage(SignInValidator.EMAIL_SIGNIN_REQUEST, EMPTY_SIGNIN, "email", "is required");
    }
    @Test
    public void emailSignInOK() {
        SignIn signIn = new SignIn.Builder().withStudy(TEST_STUDY_IDENTIFIER).withEmail(EMAIL).withToken(TOKEN).build();
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
        SignIn signIn = new SignIn.Builder().withStudy(TEST_STUDY_IDENTIFIER).withPhone(TestConstants.PHONE).build();
        Validate.entityThrowingException(SignInValidator.PHONE_SIGNIN_REQUEST, signIn);
    }
    @Test
    public void phoneSignInRequestInvalid() {
        assertValidatorMessage(SignInValidator.PHONE_SIGNIN_REQUEST, EMPTY_SIGNIN, "study", "is required");
        assertValidatorMessage(SignInValidator.PHONE_SIGNIN_REQUEST, EMPTY_SIGNIN, "phone", "is required");
    }
    @Test
    public void phoneSignInOK() {
        SignIn signIn = new SignIn.Builder().withStudy(TEST_STUDY_IDENTIFIER).withPhone(TestConstants.PHONE).withToken(TOKEN).build();
        Validate.entityThrowingException(SignInValidator.PHONE_SIGNIN, signIn);
    }
    @Test
    public void phoneSignInInvalid() {
        assertValidatorMessage(SignInValidator.PHONE_SIGNIN, EMPTY_SIGNIN, "study", "is required");
        assertValidatorMessage(SignInValidator.PHONE_SIGNIN, EMPTY_SIGNIN, "phone", "is required");
        assertValidatorMessage(SignInValidator.PHONE_SIGNIN, EMPTY_SIGNIN, "token", "is required");
    }
    @Test
    public void phoneSignInInvalidPhoneFields() {
        SignIn missingPhoneFields = new SignIn.Builder().withPhone(new Phone(null,null)).build();
        assertValidatorMessage(SignInValidator.PHONE_SIGNIN, missingPhoneFields, "study", "is required");
        assertValidatorMessage(SignInValidator.PHONE_SIGNIN, missingPhoneFields, "phone", "does not appear to be a phone number");
        assertValidatorMessage(SignInValidator.PHONE_SIGNIN, missingPhoneFields, "token", "is required");
    }
    @Test
    public void passwordSignInWithEmailOK() {
        SignIn signIn = new SignIn.Builder().withStudy(TEST_STUDY_IDENTIFIER).withEmail(EMAIL).withPassword(PASSWORD).build();
        Validate.entityThrowingException(SignInValidator.PASSWORD_SIGNIN, signIn);
    }
    @Test
    public void passwordSignInWithPhoneOK() {
        SignIn signIn = new SignIn.Builder().withStudy(TEST_STUDY_IDENTIFIER).withPhone(TestConstants.PHONE).withPassword(PASSWORD).build();
        Validate.entityThrowingException(SignInValidator.PASSWORD_SIGNIN, signIn);
    }
    @Test
    public void passwordSignInWithEmailAndPhoneOK() {
        SignIn signIn = new SignIn.Builder().withStudy(TEST_STUDY_IDENTIFIER).withEmail(EMAIL)
                .withPhone(TestConstants.PHONE).withPassword(PASSWORD).build();
        Validate.entityThrowingException(SignInValidator.PASSWORD_SIGNIN, signIn);
    }
    @Test
    public void passwordSignInWithInvalidPhoneInvalid() {
        SignIn signIn = new SignIn.Builder().withPhone(new Phone("xxxxxxxxxx", "US")).build();
        assertValidatorMessage(SignInValidator.PASSWORD_SIGNIN, signIn, "phone", "does not appear to be a phone number");
    }
    @Test
    public void passwordSignInInvalid() {
        assertValidatorMessage(SignInValidator.PASSWORD_SIGNIN, EMPTY_SIGNIN, "SignIn", "email or phone is required");
        assertValidatorMessage(SignInValidator.PASSWORD_SIGNIN, EMPTY_SIGNIN, "study", "is required");
        assertValidatorMessage(SignInValidator.PASSWORD_SIGNIN, EMPTY_SIGNIN, "password", "is required");
    }
    @Test
    public void reauthWithEmailOK() {
        SignIn signIn = new SignIn.Builder().withStudy(TEST_STUDY_IDENTIFIER).withEmail(EMAIL).withReauthToken(REAUTH_TOKEN).build();
        Validate.entityThrowingException(SignInValidator.REAUTH_SIGNIN, signIn);
    }
    @Test
    public void reauthWithPhoneOK() {
        SignIn signIn = new SignIn.Builder().withStudy(TEST_STUDY_IDENTIFIER).withPhone(TestConstants.PHONE).withReauthToken(REAUTH_TOKEN).build();
        Validate.entityThrowingException(SignInValidator.REAUTH_SIGNIN, signIn);
    }
    @Test
    public void reauthWithEmailAndPhoneOK() {
        SignIn signIn = new SignIn.Builder().withStudy(TEST_STUDY_IDENTIFIER).withReauthToken(REAUTH_TOKEN)
                .withEmail(EMAIL).withPhone(TestConstants.PHONE).build();
        Validate.entityThrowingException(SignInValidator.REAUTH_SIGNIN, signIn);
    }
    @Test
    public void reauthWithInvalidPhoneInvalid() {
        SignIn signIn = new SignIn.Builder().withPhone(new Phone("xxxxxxxxxx", "US")).build();
        assertValidatorMessage(SignInValidator.REAUTH_SIGNIN, signIn, "phone", "does not appear to be a phone number");
    }
    @Test
    public void reauthInvalid() {
        assertValidatorMessage(SignInValidator.REAUTH_SIGNIN, EMPTY_SIGNIN, "SignIn", "email or phone is required");
        assertValidatorMessage(SignInValidator.REAUTH_SIGNIN, EMPTY_SIGNIN, "study", "is required");
        assertValidatorMessage(SignInValidator.REAUTH_SIGNIN, EMPTY_SIGNIN, "reauthToken", "is required");
    }
    @Test
    public void requestResetPasswordOK() {
        SignIn signIn = new SignIn.Builder().withStudy(TEST_STUDY_IDENTIFIER).withEmail(EMAIL).build();
        Validate.entityThrowingException(SignInValidator.REQUEST_RESET_PASSWORD, signIn);

        signIn = new SignIn.Builder().withStudy(TEST_STUDY_IDENTIFIER).withPhone(TestConstants.PHONE).build();
        Validate.entityThrowingException(SignInValidator.REQUEST_RESET_PASSWORD, signIn);
    }
    @Test
    public void requestResetPassword() {
        assertValidatorMessage(SignInValidator.REQUEST_RESET_PASSWORD, EMPTY_SIGNIN, "SignIn", "email or phone is required");
        assertValidatorMessage(SignInValidator.REQUEST_RESET_PASSWORD, EMPTY_SIGNIN, "study", "is required");
    }
    @Test
    public void minimalOK() {
        SignIn signIn = new SignIn.Builder().withStudy(TEST_STUDY_IDENTIFIER).withEmail(EMAIL).build();
        Validate.entityThrowingException(SignInValidator.MINIMAL, signIn);

        signIn = new SignIn.Builder().withStudy(TEST_STUDY_IDENTIFIER).withPhone(TestConstants.PHONE).build();
        Validate.entityThrowingException(SignInValidator.MINIMAL, signIn);
    }
    @Test
    public void minimal() {
        assertValidatorMessage(SignInValidator.MINIMAL, EMPTY_SIGNIN, "SignIn", "email or phone is required");
        assertValidatorMessage(SignInValidator.MINIMAL, EMPTY_SIGNIN, "study", "is required");
    }
}
