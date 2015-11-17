package org.sagebionetworks.bridge.validators;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.accounts.DataGroups;

import com.google.common.collect.Sets;

public class DataGroupsValidatorTest {

    private Set<String> studyDataGroups;
    private DataGroupsValidator validator;
    
    @Before
    public void validatesDataGroupValues() {
        studyDataGroups = Sets.newHashSet("group_1", "group_2");
        validator = new DataGroupsValidator(studyDataGroups);
    }
    
    @Test
    public void validateDataGroupValuesValid() {
        DataGroups dataGroups = new DataGroups(Sets.newHashSet("A", "B", "group_2", null, " "));
        
        try {
            Validate.entityThrowingException(validator, dataGroups);
            fail("Should have thrown exception.");
        } catch(InvalidEntityException e) {
            List<String> errors = e.getErrors().get("dataGroups");
            assertEquals(4, errors.size());
            assertTrue(errors.contains("dataGroups 'null' is not one of these valid values: group_1, group_2."));
            assertTrue(errors.contains("dataGroups ' ' is not one of these valid values: group_1, group_2."));
            assertTrue(errors.contains("dataGroups 'A' is not one of these valid values: group_1, group_2."));
            assertTrue(errors.contains("dataGroups 'B' is not one of these valid values: group_1, group_2."));
        }
    }
    
}
