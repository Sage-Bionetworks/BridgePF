package org.sagebionetworks.bridge.validators;

import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.EmailTemplate.MimeType;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;

public class StudyValidatorTest {

    private DynamoStudy study;
    
    @Before
    public void createValidStudy() {
        study = TestUtils.getValidStudy();
    }
    
    @Test
    public void acceptsValidStudy() {
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
    }
    
    // While 2 is not a good length, we must allow it for legacy reasons.
    @Test(expected = InvalidEntityException.class)
    public void minLengthCannotBeLessThan2() {
        study.setPasswordPolicy(new PasswordPolicy(1, false, false, false, false));
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void minLengthCannotBeMoreThan999() {
        study.setPasswordPolicy(new PasswordPolicy(1000, false, false, false, false));
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void templatesMustHaveUrlVariable() {
        study.setVerifyEmailTemplate(new EmailTemplate("subject", "no url variable", MimeType.TEXT));
        study.setResetPasswordTemplate(new EmailTemplate("subject", "no url variable", MimeType.TEXT));
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
    }

    @Test(expected = InvalidEntityException.class)
    public void cannotCreateIdentifierWithUppercase() {
        study.setIdentifier("Test");
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
    }

    @Test(expected = InvalidEntityException.class)
    public void cannotCreateInvalidIdentifierWithSpaces() {
        study.setIdentifier("test test");
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
    }

    @Test
    public void identifierCanContainDashes() {
        study.setIdentifier("sage-pd");
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
    }
    
    @Test
    public void acceptsMultipleValidSupportEmailAddresses() {
        study.setSupportEmail("test@test.com,test2@test.com");
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void rejectsInvalidSupportEmailAddresses() {
        study.setSupportEmail("test@test.com,asdf,test2@test.com");
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void rejectsInvalidSupportEmailAddresses2() {
        study.setSupportEmail("test@test.com,,,test2@test.com");
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void requiresMissingSupportEmail() {
        study.setSupportEmail(null);
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
    }
    
    @Test
    public void acceptsMultipleValidTechnicalEmailAddresses() {
        study.setTechnicalEmail("test@test.com,test2@test.com");
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void rejectsInvalidTechnicalEmailAddresses() {
        study.setTechnicalEmail("test@test.com,asdf,test2@test.com");
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void rejectsInvalidTechnicalEmailAddresses2() {
        study.setTechnicalEmail("test@test.com,,,test2@test.com");
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void requiresMissingTechnicalEmail() {
        study.setTechnicalEmail(null);
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void rejectsInvalidConsentEmailAddresses() {
        study.setConsentNotificationEmail("test@test.com,asdf,test2@test.com");
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void cannotAddConflictingUserProfileAttribute() {
        study.getUserProfileAttributes().add("username");
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void requiresMissingConsentNotificationEmail() {
        study.setConsentNotificationEmail(null);
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void requiresValidSupportEmail() {
        study.setSupportEmail("asdf");
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void requiresValidConsentNotificationEmail() {
        study.setConsentNotificationEmail("aaa@aaa");
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void requiresPasswordPolicy() {
        study.setPasswordPolicy(null);
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void requiresVerifyEmailTemplate() {
        study.setVerifyEmailTemplate(null);
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
    }

    @Test(expected = InvalidEntityException.class)
    public void requiresVerifyEmailTemplateWithSubject() {
        study.setVerifyEmailTemplate(new EmailTemplate("  ", "body", MimeType.HTML));
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
    }

    @Test(expected = InvalidEntityException.class)
    public void requiresVerifyEmailTemplateWithBody() {
        study.setVerifyEmailTemplate(new EmailTemplate("subject", null, MimeType.HTML));
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
    }

    @Test(expected = InvalidEntityException.class)
    public void requiresResetPasswordTemplate() {
        study.setResetPasswordTemplate(null);
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
    }

    @Test(expected = InvalidEntityException.class)
    public void requiresResetPasswordTemplateWithSubject() {
        study.setResetPasswordTemplate(new EmailTemplate("  ", "body", MimeType.TEXT));
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
    }

    @Test(expected = InvalidEntityException.class)
    public void requiresResetPasswordTemplateWithBody() {
        study.setResetPasswordTemplate(new EmailTemplate("subject", null, MimeType.TEXT));
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void cannotSetMinAgeOfConsentLessThanZero() {
        study.setMinAgeOfConsent(-100);
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void cannotSetMaxNumOfParticipantsLessThanZero() {
        study.setMaxNumOfParticipants(-100);
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
    }
    
    /*
    if (study.getMinAgeOfConsent() < 0) {
        errors.rejectValue("minAgeOfConsent", "must be zero (no minimum age of consent) or higher");
    }
    if (study.getMaxNumOfParticipants() < 0) {
        errors.rejectValue("maxNumOfParticipants", "must be zero (no limit on enrollees) or higher");
    }
    */

}
