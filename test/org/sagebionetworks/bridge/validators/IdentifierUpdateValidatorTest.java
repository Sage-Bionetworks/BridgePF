package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;

import org.junit.Test;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.models.accounts.IdentifierUpdate;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.SignIn;

public class IdentifierUpdateValidatorTest {

    // validates sign by phone or email
    // validates sign in by reauth
    
    @Test
    public void signInRequired() {
        IdentifierUpdate update = new IdentifierUpdate(null, "updated@email.com", null);
        
        assertValidatorMessage(IdentifierUpdateValidator.INSTANCE, update, "IdentifierUpdate",
                "requires a signIn object");
    }
    
    @Test
    public void signInErrorsNested() {
        // Sign in with no password
        SignIn signIn = new SignIn.Builder().withStudy(TestConstants.TEST_STUDY_IDENTIFIER)
                .withEmail(TestConstants.EMAIL).build();
        
        IdentifierUpdate update = new IdentifierUpdate(signIn, "updated@email.com", null);
        assertValidatorMessage(IdentifierUpdateValidator.INSTANCE, update, "signIn.password", "is required");
    }
    
    @Test
    public void validEmailPasswordUpdate() {
        SignIn signIn = new SignIn.Builder().withStudy(TestConstants.TEST_STUDY_IDENTIFIER)
                .withEmail(TestConstants.EMAIL).withPassword(TestConstants.PASSWORD).build();
        
        IdentifierUpdate update = new IdentifierUpdate(signIn, "updated@email.com", null);
        Validate.entityThrowingException(IdentifierUpdateValidator.INSTANCE, update);
    }
    
    @Test
    public void validPhonePasswordUpdate() {
        SignIn signIn = new SignIn.Builder().withStudy(TestConstants.TEST_STUDY_IDENTIFIER)
                .withPhone(TestConstants.PHONE).withPassword(TestConstants.PASSWORD).build();
        
        IdentifierUpdate update = new IdentifierUpdate(signIn, "updated@email.com", null);
        Validate.entityThrowingException(IdentifierUpdateValidator.INSTANCE, update);
    }
    
    @Test
    public void validReauthUpdate() {
        SignIn signIn = new SignIn.Builder().withStudy(TestConstants.TEST_STUDY_IDENTIFIER)
                .withEmail(TestConstants.EMAIL).withReauthToken("asdf").build();
        
        IdentifierUpdate update = new IdentifierUpdate(signIn, "updated@email.com", null);
        Validate.entityThrowingException(IdentifierUpdateValidator.INSTANCE, update);
    }
    
    @Test
    public void noUpdatesInvalid() {
        SignIn signIn = new SignIn.Builder().withStudy(TestConstants.TEST_STUDY_IDENTIFIER)
                .withEmail(TestConstants.EMAIL).withReauthToken("asdf").build();
        
        IdentifierUpdate update = new IdentifierUpdate(signIn, null, null);
        assertValidatorMessage(IdentifierUpdateValidator.INSTANCE, update, "IdentifierUpdate",
                "requires at least one updated identifier (email, phone)");
    }
    
    @Test
    public void phoneInvalid() {
        SignIn signIn = new SignIn.Builder().withStudy(TestConstants.TEST_STUDY_IDENTIFIER)
                .withEmail(TestConstants.EMAIL).withReauthToken("asdf").build();
        
        IdentifierUpdate update = new IdentifierUpdate(signIn, null, new Phone("12334578990", "US"));
        assertValidatorMessage(IdentifierUpdateValidator.INSTANCE, update, "phoneUpdate",
                "does not appear to be a phone number");
    }
}
