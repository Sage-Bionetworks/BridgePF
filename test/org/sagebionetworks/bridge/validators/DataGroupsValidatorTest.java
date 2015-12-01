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
            assertEquals(3, errors.size());
            assertTrue(errors.contains("dataGroups ' ' is not one of these valid values: group_1, group_2."));
            assertTrue(errors.contains("dataGroups 'A' is not one of these valid values: group_1, group_2."));
            assertTrue(errors.contains("dataGroups 'B' is not one of these valid values: group_1, group_2."));
        }
    }
    
    @Test
    public void invalidGroupsWhenStudyHasNoDataGroups() {
        try {
            DataGroups dataGroups = new DataGroups(Sets.newHashSet("group_4"));
            Validate.entityThrowingException(new DataGroupsValidator(Sets.newHashSet()), dataGroups);    
        } catch(InvalidEntityException e) {
            List<String> errors = e.getErrors().get("dataGroups");
            assertEquals("dataGroups 'group_4' is not one of these valid values: <none>", errors.get(0));
        }
    }
    
    @Test
    public void validDataGroup() {
        DataGroups dataGroups = new DataGroups(Sets.newHashSet("group_1", "group_2"));
        Validate.entityThrowingException(validator, dataGroups);
    }
    
    @Test
    public void emptyDataGroupsValid() {
        DataGroups dataGroups = new DataGroups(Sets.newHashSet());
        Validate.entityThrowingException(validator, dataGroups);
    }
    
}
