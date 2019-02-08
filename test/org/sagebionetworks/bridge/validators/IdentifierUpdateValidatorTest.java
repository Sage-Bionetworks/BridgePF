package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;

import java.util.Optional;

import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.models.accounts.ExternalIdentifier;
import org.sagebionetworks.bridge.models.accounts.IdentifierUpdate;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.ExternalIdService;

@RunWith(MockitoJUnitRunner.class)
public class IdentifierUpdateValidatorTest {

    private static final String UPDATED_EMAIL = "updated@email.com";
    private static final String UPDATED_EXTERNAL_ID = "updatedExternalId";
    private static final ExternalIdentifier EXT_ID = ExternalIdentifier.create(TestConstants.TEST_STUDY, UPDATED_EXTERNAL_ID);
    
    @Mock
    private ExternalIdService externalIdService;

    private Study study; 
    
    private IdentifierUpdateValidator validator;
    
    @Before
    public void before() {
        study = Study.create();
        study.setIdentifier(TestConstants.TEST_STUDY_IDENTIFIER);
        validator = new IdentifierUpdateValidator(study, externalIdService);
        
    }
    
    @Test
    public void signInRequired() {
        IdentifierUpdate update = new IdentifierUpdate(null, UPDATED_EMAIL, null, UPDATED_EXTERNAL_ID);
        
        assertValidatorMessage(validator, update, "IdentifierUpdate", "requires a signIn object");
    }
    
    @Test
    public void signInErrorsNestedSignIn() {
        // Sign in with no password
        SignIn signIn = new SignIn.Builder().withStudy(TestConstants.TEST_STUDY_IDENTIFIER)
                .withEmail(TestConstants.EMAIL).build();
        
        IdentifierUpdate update = new IdentifierUpdate(signIn, UPDATED_EMAIL, null, UPDATED_EXTERNAL_ID);
        assertValidatorMessage(validator, update, "signIn.password", "is required");
    }
    
    @Test
    public void signInErrorsNestedReauthentication() {
        // Reauthentication with no study
        SignIn reauth = new SignIn.Builder().withEmail(TestConstants.EMAIL)
                .withReauthToken("ABDC").build();
        
        IdentifierUpdate update = new IdentifierUpdate(reauth, null, TestConstants.PHONE, UPDATED_EXTERNAL_ID);
        assertValidatorMessage(validator, update, "signIn.study", "is required");
    }
    
    @Test
    public void validEmailPasswordUpdate() {
        SignIn signIn = new SignIn.Builder().withStudy(TestConstants.TEST_STUDY_IDENTIFIER)
                .withEmail(TestConstants.EMAIL).withPassword(TestConstants.PASSWORD).build();
        
        IdentifierUpdate update = new IdentifierUpdate(signIn, UPDATED_EMAIL, null, null);
        Validate.entityThrowingException(validator, update);
    }
    
    @Test
    public void validPhonePasswordUpdate() {
        SignIn signIn = new SignIn.Builder().withStudy(TestConstants.TEST_STUDY_IDENTIFIER)
                .withPhone(TestConstants.PHONE).withPassword(TestConstants.PASSWORD).build();
        
        IdentifierUpdate update = new IdentifierUpdate(signIn, UPDATED_EMAIL, null, null);
        Validate.entityThrowingException(validator, update);
    }
    
    @Test
    public void validReauthUpdate() {
        SignIn signIn = new SignIn.Builder().withStudy(TestConstants.TEST_STUDY_IDENTIFIER)
                .withEmail(TestConstants.EMAIL).withReauthToken("asdf").build();
        
        IdentifierUpdate update = new IdentifierUpdate(signIn, UPDATED_EMAIL, null, null);
        Validate.entityThrowingException(validator, update);
    }
    
    @Test
    public void noUpdatesInvalid() {
        SignIn signIn = new SignIn.Builder().withStudy(TestConstants.TEST_STUDY_IDENTIFIER)
                .withEmail(TestConstants.EMAIL).withReauthToken("asdf").build();
        
        IdentifierUpdate update = new IdentifierUpdate(signIn, null, null, null);
        assertValidatorMessage(validator, update, "IdentifierUpdate",
                "requires at least one updated identifier (email, phone, externalId)");
    }
    
    @Test
    public void validPhoneUpdate() {
        SignIn signIn = new SignIn.Builder().withStudy(TestConstants.TEST_STUDY_IDENTIFIER)
                .withEmail(TestConstants.EMAIL).withReauthToken("asdf").build();
        
        IdentifierUpdate update = new IdentifierUpdate(signIn, null, new Phone("4082588569", "US"), null);
        Validate.entityThrowingException(validator, update);
    }
    
    @Test
    public void validExternalIdUpate() {
        SignIn signIn = new SignIn.Builder().withStudy(TestConstants.TEST_STUDY_IDENTIFIER)
                .withEmail(TestConstants.EMAIL).withReauthToken("asdf").build();
        
        IdentifierUpdate update = new IdentifierUpdate(signIn, null, null, "newExternalId");
        Validate.entityThrowingException(validator, update);
    }
    
    @Test
    public void phoneInvalid() {
        SignIn signIn = new SignIn.Builder().withStudy(TestConstants.TEST_STUDY_IDENTIFIER)
                .withEmail(TestConstants.EMAIL).withReauthToken("asdf").build();
        
        IdentifierUpdate update = new IdentifierUpdate(signIn, null, new Phone("12334578990", "US"), null);
        assertValidatorMessage(validator, update, "phoneUpdate", "does not appear to be a phone number");
    }
    
    @Test
    public void emailInvalidValue() {
        SignIn signIn = new SignIn.Builder().withStudy(TestConstants.TEST_STUDY_IDENTIFIER)
                .withEmail(TestConstants.EMAIL).withReauthToken("asdf").build();
        
        IdentifierUpdate update = new IdentifierUpdate(signIn, "junk", null, null);
        assertValidatorMessage(validator, update, "emailUpdate", "does not appear to be an email address");
    }
    
    @Test
    public void emailEmptyValue() {
        SignIn signIn = new SignIn.Builder().withStudy(TestConstants.TEST_STUDY_IDENTIFIER)
                .withEmail(TestConstants.EMAIL).withReauthToken("asdf").build();
        
        IdentifierUpdate update = new IdentifierUpdate(signIn, "", null, null);
        assertValidatorMessage(validator, update, "emailUpdate", "does not appear to be an email address");
    }
    
    @Test
    public void externalIdValidWithManagement() {
        when(externalIdService.getExternalId(study.getStudyIdentifier(), UPDATED_EXTERNAL_ID)).thenReturn(Optional.of(EXT_ID));
        study.setExternalIdValidationEnabled(true);
        
        SignIn signIn = new SignIn.Builder().withStudy(TestConstants.TEST_STUDY_IDENTIFIER)
                .withEmail(TestConstants.EMAIL).withReauthToken("asdf").build();
        
        IdentifierUpdate update = new IdentifierUpdate(signIn, null, null, UPDATED_EXTERNAL_ID);
        Validate.entityThrowingException(validator, update);
    }
    
    @Test
    public void externalIdInvalidWithManagement() {
        when(externalIdService.getExternalId(study.getStudyIdentifier(), UPDATED_EXTERNAL_ID)).thenReturn(Optional.empty());
        study.setExternalIdValidationEnabled(true);
        
        SignIn signIn = new SignIn.Builder().withStudy(TestConstants.TEST_STUDY_IDENTIFIER)
                .withEmail(TestConstants.EMAIL).withReauthToken("asdf").build();
        
        IdentifierUpdate update = new IdentifierUpdate(signIn, null, null, UPDATED_EXTERNAL_ID);
        assertValidatorMessage(validator, update, "externalIdUpdate", "is not a valid external ID");
    }
    
    @Test
    public void externalIdCannotBeBlank() {
        SignIn signIn = new SignIn.Builder().withStudy(TestConstants.TEST_STUDY_IDENTIFIER)
                .withEmail(TestConstants.EMAIL).withReauthToken("asdf").build();
        
        IdentifierUpdate update = new IdentifierUpdate(signIn, null, null, "");
        assertValidatorMessage(validator, update, "externalIdUpdate", "cannot be blank");
    }
}
