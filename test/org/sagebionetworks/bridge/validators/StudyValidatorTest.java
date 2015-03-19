package org.sagebionetworks.bridge.validators;

import org.junit.Test;

import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;

public class StudyValidatorTest {
    
    @Test(expected = InvalidEntityException.class)
    public void cannotCreateIdentifierWithUppercase() {
        DynamoStudy study = new DynamoStudy();
        study.setIdentifier("Test");
        study.setName("Belgium Waffles [Test]");
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
    }

    @Test(expected = InvalidEntityException.class)
    public void cannotCreateInvalidWithSpaces() {
        DynamoStudy study = new DynamoStudy();
        study.setIdentifier("test test");
        study.setName("Belgium Waffles [Test]");
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
    }

    /* Why did I want to prevent this? It makes a valid hostname, it's okay to have numbers
    @Test(expected = InvalidEntityException.class)
    public void cannotCreateInvalidWithNumbers() {
        DynamoStudy study = new DynamoStudy();
        study.setIdentifier("test3");
        study.setName("Belgium Waffles [Test]");
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
    }*/

    @Test
    public void identifierCanContainDashes() {
        DynamoStudy study = new DynamoStudy();
        study.setIdentifier("sage-pd");
        study.setName("Belgium Waffles [Test]");
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
    }
    
    @Test
    public void acceptsValidSupportEmailAddresses() {
        DynamoStudy study = new DynamoStudy();
        study.setIdentifier("test");
        study.setName("Belgium Waffles [Test]");
        study.setSupportEmail("test@test.com,test2@test.com");
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void rejectsInvalidSupportEmailAddresses() {
        DynamoStudy study = new DynamoStudy();
        study.setIdentifier("test3");
        study.setName("Belgium Waffles [Test]");
        study.setSupportEmail("test@test.com,asdf,test2@test.com");
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void rejectsInvalidSupportEmailAddresses2() {
        DynamoStudy study = new DynamoStudy();
        study.setIdentifier("test3");
        study.setName("Belgium Waffles [Test]");
        study.setSupportEmail("test@test.com,,,test2@test.com");
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
    }
    
    @Test
    public void acceptsValidConsentEmailAddresses() {
        DynamoStudy study = new DynamoStudy();
        study.setIdentifier("test");
        study.setName("Belgium Waffles [Test]");
        study.setConsentNotificationEmail("test@test.com");
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void rejectsInvalidConsentEmailAddresses() {
        DynamoStudy study = new DynamoStudy();
        study.setIdentifier("test3");
        study.setName("Belgium Waffles [Test]");
        study.setConsentNotificationEmail("test@test.com,asdf,test2@test.com");
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
    }
    
    @Test(expected = InvalidEntityException.class)
    public void cannotAddConflictingUserProfileAttribute() {
        DynamoStudy study = new DynamoStudy();
        study.getUserProfileAttributes().add("username");
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
    }
    
}
