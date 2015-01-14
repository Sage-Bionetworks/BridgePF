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

    @Test(expected = InvalidEntityException.class)
    public void cannotCreateInvalidWithNumbers() {
        DynamoStudy study = new DynamoStudy();
        study.setIdentifier("test3");
        study.setName("Belgium Waffles [Test]");
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
    }

    @Test
    public void identifierCanContainDashes() {
        DynamoStudy study = new DynamoStudy();
        study.setIdentifier("sage-pd");
        study.setName("Belgium Waffles [Test]");
        Validate.entityThrowingException(StudyValidator.INSTANCE, study);
    }
}
