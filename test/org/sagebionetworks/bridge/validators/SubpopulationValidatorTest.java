package org.sagebionetworks.bridge.validators;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;

import com.google.common.collect.ImmutableSet;

public class SubpopulationValidatorTest {

    SubpopulationValidator validator;
    
    @Before
    public void before() {
        validator = new SubpopulationValidator(TestConstants.USER_DATA_GROUPS, TestConstants.USER_SUBSTUDY_IDS);
    }
    
    @Test
    public void testEntirelyValid() {
        Subpopulation subpop = Subpopulation.create();
        subpop.setName("Name");
        subpop.setDescription("Description");
        subpop.setDefaultGroup(true);
        subpop.setRequired(true);
        subpop.setStudyIdentifier("test-study");
        subpop.setVersion(3L);
        subpop.setGuidString("AAA");
        
        Criteria criteria = TestUtils.createCriteria(2, 4, ImmutableSet.of("group1"), ImmutableSet.of("group2"));
        criteria.setAllOfSubstudyIds(ImmutableSet.of("substudyA"));
        criteria.setNoneOfSubstudyIds(ImmutableSet.of("substudyB"));
        subpop.setCriteria(criteria);
        
        Validate.entityThrowingException(validator, subpop);
    }
    
    @Test
    public void testValidation() {
        Subpopulation subpop = Subpopulation.create();
        
        Criteria criteria = TestUtils.createCriteria(-10, -2, null, ImmutableSet.of("wrongGroup"));
        criteria.setAllOfSubstudyIds(ImmutableSet.of("substudyC"));
        criteria.setNoneOfSubstudyIds(ImmutableSet.of("substudyD"));
        subpop.setCriteria(criteria);
        try {
            Validate.entityThrowingException(validator, subpop);
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertMessage(e, "minAppVersions.iphone_os", " cannot be negative");
            assertMessage(e, "maxAppVersions.iphone_os", " cannot be negative");
            assertMessage(e, "studyIdentifier", " is required");
            assertMessage(e, "name", " is required");
            assertMessage(e, "guid", " is required");
            assertMessage(e, "noneOfGroups", " 'wrongGroup' is not in enumeration");
            assertMessage(e, "allOfSubstudyIds", " 'substudyC' is not in enumeration");
            assertMessage(e, "noneOfSubstudyIds", " 'substudyD' is not in enumeration");
        }
    }
    
    private void assertMessage(InvalidEntityException e, String propName, String error) {
        Map<String,List<String>> errors = e.getErrors();
        List<String> messages = errors.get(propName);
        assertTrue(messages.get(0).contains(propName + error));
    }
}
