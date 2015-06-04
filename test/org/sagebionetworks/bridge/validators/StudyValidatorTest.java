package org.sagebionetworks.bridge.validators;

import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.studies.EmailTemplate;
import org.sagebionetworks.bridge.models.studies.PasswordPolicy;

public class StudyValidatorTest {

    private DynamoStudy study;
    
    @Before
    public void createValidStudy() {
        study = new DynamoStudy();
        study.setPasswordPolicy(PasswordPolicy.DEFAULT_PASSWORD_POLICY);
        study.setVerifyEmailTemplate(new EmailTemplate("subject", "body"));
        study.setResetPasswordTemplate(new EmailTemplate("subject", "body"));
        study.setIdentifier("test");
        study.setName("Belgium Waffles [Test]");
        study.setSupportEmail("support@support.com");
        study.setConsentNotificationEmail("support@support.com");
    }

    @Test
    public void acceptsValidStudy() {
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
    public void requiresMissingSupportEmail() {
        study.setSupportEmail(null);
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
        study.setVerifyEmailTemplate(new EmailTemplate("  ", "body"));
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
    }

    @Test(expected = InvalidEntityException.class)
    public void requiresVerifyEmailTemplateWithBody() {
        study.setVerifyEmailTemplate(new EmailTemplate("subject", null));
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
    }

    @Test(expected = InvalidEntityException.class)
    public void requiresResetPasswordTemplate() {
        study.setResetPasswordTemplate(null);
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
    }

    @Test(expected = InvalidEntityException.class)
    public void requiresResetPasswordTemplateWithSubject() {
        study.setResetPasswordTemplate(new EmailTemplate("  ", "body"));
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
    }

    @Test(expected = InvalidEntityException.class)
    public void requiresResetPasswordTemplateWithBody() {
        study.setResetPasswordTemplate(new EmailTemplate("subject", null));
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
    }
}
