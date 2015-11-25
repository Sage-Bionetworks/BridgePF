package org.sagebionetworks.bridge.validators;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.studies.Subpopulation;

import com.google.common.collect.Sets;

public class SubpopulationValidatorTest {

    SubpopulationValidator validator;
    
    @Before
    public void beofre() {
        validator = new SubpopulationValidator(Sets.newHashSet("group1","group2"));
    }
    
    @Test
    public void testValidation() {
        Subpopulation subpop = Subpopulation.create();
        subpop.setMinAppVersion(-10);
        subpop.setMaxAppVersion(-2);
        subpop.getNoneOfGroups().add("wrongGroup");
        try {
            Validate.entityThrowingException(validator, subpop);
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertMessage(e, "minAppVersion", " cannot be negative");
            assertMessage(e, "maxAppVersion", " cannot be negative");
            assertMessage(e, "studyIdentifier", " is required");
            assertMessage(e, "name", " is required");
            assertMessage(e, "guid", " is required");
            assertMessage(e, "noneOfGroups", " 'wrongGroup' is not in enumeration");
        }
    }
    
    private void assertMessage(InvalidEntityException e, String propName, String error) {
        Map<String,List<String>> errors = e.getErrors();
        List<String> messages = errors.get(propName);
        assertTrue(messages.get(0).contains(propName + error));
    }
}

