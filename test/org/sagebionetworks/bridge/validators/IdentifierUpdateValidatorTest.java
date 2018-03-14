package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;

import org.junit.Test;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.models.accounts.IdentifierUpdate;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.SignIn;

public class IdentifierUpdateValidatorTest {

    private static final String UPDATED_EMAIL = "updated@email.com";
    private static final String UPDATED_EXTERNAL_ID = "updatedExternalId";

    // validates sign by phone or email
    // validates sign in by reauth
    
    @Test
    public void signInRequired() {
        IdentifierUpdate update = new IdentifierUpdate(null, UPDATED_EMAIL, null, UPDATED_EXTERNAL_ID);
        
        assertValidatorMessage(IdentifierUpdateValidator.INSTANCE, update, "IdentifierUpdate",
                "requires a signIn object");
    }
    
    @Test
    public void signInErrorsNestedSignIn() {
        // Sign in with no password
        SignIn signIn = new SignIn.Builder().withStudy(TestConstants.TEST_STUDY_IDENTIFIER)
                .withEmail(TestConstants.EMAIL).build();
        
        IdentifierUpdate update = new IdentifierUpdate(signIn, UPDATED_EMAIL, null, UPDATED_EXTERNAL_ID);
        assertValidatorMessage(IdentifierUpdateValidator.INSTANCE, update, "signIn.password", "is required");
    }
    
    @Test
    public void signInErrorsNestedReauthentication() {
        // Reauthentication with no study
        SignIn reauth = new SignIn.Builder().withEmail(TestConstants.EMAIL)
                .withReauthToken("ABDC").build();
        
        IdentifierUpdate update = new IdentifierUpdate(reauth, null, TestConstants.PHONE, UPDATED_EXTERNAL_ID);
        assertValidatorMessage(IdentifierUpdateValidator.INSTANCE, update, "signIn.study", "is required");
    }
    
    @Test
    public void validEmailPasswordUpdate() {
        SignIn signIn = new SignIn.Builder().withStudy(TestConstants.TEST_STUDY_IDENTIFIER)
                .withEmail(TestConstants.EMAIL).withPassword(TestConstants.PASSWORD).build();
        
        IdentifierUpdate update = new IdentifierUpdate(signIn, UPDATED_EMAIL, null, null);
        Validate.entityThrowingException(IdentifierUpdateValidator.INSTANCE, update);
    }
    
    @Test
    public void validPhonePasswordUpdate() {
        SignIn signIn = new SignIn.Builder().withStudy(TestConstants.TEST_STUDY_IDENTIFIER)
                .withPhone(TestConstants.PHONE).withPassword(TestConstants.PASSWORD).build();
        
        IdentifierUpdate update = new IdentifierUpdate(signIn, UPDATED_EMAIL, null, null);
        Validate.entityThrowingException(IdentifierUpdateValidator.INSTANCE, update);
    }
    
    @Test
    public void validReauthUpdate() {
        SignIn signIn = new SignIn.Builder().withStudy(TestConstants.TEST_STUDY_IDENTIFIER)
                .withEmail(TestConstants.EMAIL).withReauthToken("asdf").build();
        
        IdentifierUpdate update = new IdentifierUpdate(signIn, UPDATED_EMAIL, null, null);
        Validate.entityThrowingException(IdentifierUpdateValidator.INSTANCE, update);
    }
    
    @Test
    public void noUpdatesInvalid() {
        SignIn signIn = new SignIn.Builder().withStudy(TestConstants.TEST_STUDY_IDENTIFIER)
                .withEmail(TestConstants.EMAIL).withReauthToken("asdf").build();
        
        IdentifierUpdate update = new IdentifierUpdate(signIn, null, null, null);
        assertValidatorMessage(IdentifierUpdateValidator.INSTANCE, update, "IdentifierUpdate",
                "requires at least one updated identifier (email, phone, externalId)");
    }
    
    @Test
    public void phoneInvalid() {
        SignIn signIn = new SignIn.Builder().withStudy(TestConstants.TEST_STUDY_IDENTIFIER)
                .withEmail(TestConstants.EMAIL).withReauthToken("asdf").build();
        
        IdentifierUpdate update = new IdentifierUpdate(signIn, null, new Phone("12334578990", "US"), null);
        assertValidatorMessage(IdentifierUpdateValidator.INSTANCE, update, "phoneUpdate",
                "does not appear to be a phone number");
    }
}
