package org.sagebionetworks.bridge.validators;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;

import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.validators.ExternalIdsValidator.ExternalIdList;

import com.google.common.collect.Lists;

public class ExternalIdValidatorTest {

    private ExternalIdsValidator validator;
    
    @Before
    public void before() {
        validator = new ExternalIdsValidator(5);
    }
    
    @Test
    public void rejectsOverLimit() {
        ExternalIdList list = new ExternalIdList(Lists.newArrayList("A","B","C","D","E","F"));
        
        try {
            Validate.entityThrowingException(validator, list);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertTrue(e.getMessage().contains("ExternalIdList contains too many elements; size=6, limit=5"));
        }
    }
    
    @Test
    public void rejectsEmpty() {
        ExternalIdList list = new ExternalIdList(Lists.newArrayList());
        
        try {
            Validate.entityThrowingException(validator, list);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertTrue(e.getMessage().contains("ExternalIdList contains no elements"));
        }
    }
    
    @Test
    public void rejectsEmptyElement() {
        ExternalIdList list = new ExternalIdList(Lists.newArrayList("AAA","","CCC"));

        try {
            Validate.entityThrowingException(validator, list);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertEquals("ids[1] cannot be null or blank", e.getErrors().get("ids[1]").get(0));
            assertTrue(e.getMessage().contains("ids[1] cannot be null or blank"));
        }
    }
    
    @Test
    public void rejectsNullElement() {
        ExternalIdList list = new ExternalIdList(Lists.newArrayList("AAA",null,"CCC"));

        try {
            Validate.entityThrowingException(validator, list);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertEquals("ids[1] cannot be null or blank", e.getErrors().get("ids[1]").get(0));
            assertTrue(e.getMessage().contains("ids[1] cannot be null or blank"));
        }
    }
    
    @Test
    public void rejectsInvalidElement() {
        ExternalIdList list = new ExternalIdList(Lists.newArrayList("Two Words","<Funky>Markup","And a \ttab character"));

        try {
            Validate.entityThrowingException(validator, list);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            assertEquals("ids[0] 'Two Words' must contain only digits, letters, underscores and dashes", e.getErrors().get("ids[0]").get(0));
            assertEquals("ids[1] '<Funky>Markup' must contain only digits, letters, underscores and dashes", e.getErrors().get("ids[1]").get(0));
            assertEquals("ids[2] 'And a \ttab character' must contain only digits, letters, underscores and dashes", e.getErrors().get("ids[2]").get(0));
        }        
    }
    
}
