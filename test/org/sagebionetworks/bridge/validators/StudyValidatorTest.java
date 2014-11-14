package org.sagebionetworks.bridge.validators;

import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.springframework.validation.Validator;

public class StudyValidatorTest {

    private Validator validator;
    private DynamoStudy study;

    @Before
    public void before() {
        validator = new StudyValidator();
        study = new DynamoStudy();
    }
    
    @Test(expected=InvalidEntityException.class)
    public void cannotCreateIdentifierWithUppercase() {
        study.setIdentifier("Test");
        study.setName("Belgium Waffles [Test]");
        Validate.entityThrowingException(validator, study);
    }
    
    @Test(expected=InvalidEntityException.class)
    public void cannotCreateInvalidWithSpaces() {
        study.setIdentifier("test test");
        study.setName("Belgium Waffles [Test]");
        Validate.entityThrowingException(validator, study);
    }
    
    @Test(expected=InvalidEntityException.class)
    public void cannotCreateInvalidWithNumbers() {
        study.setIdentifier("test3");
        study.setName("Belgium Waffles [Test]");
        Validate.entityThrowingException(validator, study);
    }
    
    @Test
    public void identifierCanContainDashes() {
        study.setIdentifier("sage-pd");
        study.setName("Belgium Waffles [Test]");
        Validate.entityThrowingException(validator, study);
    }
    
}
