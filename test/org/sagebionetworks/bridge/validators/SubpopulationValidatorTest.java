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
        subpop.setDataGroupsAssignedWhileConsented(TestConstants.USER_DATA_GROUPS);
        subpop.setSubstudyIdsAssignedOnConsent(TestConstants.USER_SUBSTUDY_IDS);
        
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
        
        subpop.setDataGroupsAssignedWhileConsented(ImmutableSet.of("group1", "dataGroup3"));
        subpop.setSubstudyIdsAssignedOnConsent(ImmutableSet.of("substudyA", "substudyC"));
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
            assertMessage(e, "dataGroupsAssignedWhileConsented", " 'dataGroup3' is not in enumeration: group1, group2");
            assertMessage(e, "substudyIdsAssignedOnConsent", " 'substudyC' is not in enumeration: substudyA, substudyB");
        }
    }
    
    @Test
    public void emptyListsOK() {
        Subpopulation subpop = Subpopulation.create();
        subpop.setStudyIdentifier("test-study");
        subpop.setGuidString("AAA");
        subpop.setName("Name");
        subpop.setDataGroupsAssignedWhileConsented(ImmutableSet.of());
        subpop.setSubstudyIdsAssignedOnConsent(ImmutableSet.of());
        
        Validate.entityThrowingException(validator, subpop);
        assertTrue(subpop.getDataGroupsAssignedWhileConsented().isEmpty());
        assertTrue(subpop.getSubstudyIdsAssignedOnConsent().isEmpty());
    }
    
    @Test
    public void nullListsOK() {
        Subpopulation subpop = Subpopulation.create();
        subpop.setStudyIdentifier("test-study");
        subpop.setGuidString("AAA");
        subpop.setName("Name");
        subpop.setDataGroupsAssignedWhileConsented(null);
        subpop.setSubstudyIdsAssignedOnConsent(null);
        
        Validate.entityThrowingException(validator, subpop);
        assertTrue(subpop.getDataGroupsAssignedWhileConsented().isEmpty());
        assertTrue(subpop.getSubstudyIdsAssignedOnConsent().isEmpty());
    }
    
    private void assertMessage(InvalidEntityException e, String propName, String error) {
        Map<String,List<String>> errors = e.getErrors();
        List<String> messages = errors.get(propName);
        assertTrue(messages.get(0).contains(propName + error));
    }
}
