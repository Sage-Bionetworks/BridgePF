package org.sagebionetworks.bridge.validators;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.List;

import org.junit.Test;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.springframework.validation.MapBindingResult;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.google.common.collect.Maps;

public class UploadFieldDefinitionValidatorTest {
    
    private UploadFieldDefinitionValidator validator = UploadFieldDefinitionValidator.INSTANCE;
    
    
    private String errorFor(InvalidEntityException e, String field) {
        List<String> errors = e.getErrors().get(field);
        assertNotNull(errors);
        assertEquals(1, errors.size());
        return errors.get(0);
    }
    
    private void assertErrorExists(UploadFieldDefinition def, String error, String fieldName) {
        try {
            Validate.entityThrowingException(validator, def);
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertEquals(error, errorFor(e, fieldName));
        }
    }

    @Test
    public void requiresObject() {
        MapBindingResult errors = new MapBindingResult(Maps.newHashMap(), "UploadFieldDefinition");
        validator.validate(null, errors);
        String error = Validate.convertBindingResultToMessage(errors);
        assertEquals("UploadFieldDefinition is invalid: uploadFieldDefinition cannot be null", error);
    }
    
    @Test
    public void requiresUploadSchema() {
        MapBindingResult errors = new MapBindingResult(Maps.newHashMap(), "UploadFieldDefinition");
        validator.validate(new String("not the right object type"), errors);
        String error = Validate.convertBindingResultToMessage(errors);
        assertEquals("UploadFieldDefinition is invalid: uploadFieldDefinition is the wrong type", error);
    }
    
    // The problem illustrated in the next two tests is that the deserialization of upload field definitions
    // throws a JsonMappingException, not an InvalidEntityException. This translates into the impression 
    // that the JSON was malformed, when it wasn't, it was just that a required field is missing.
    
    @Test
    public void requiresName() throws Exception {
        try {
            String json = "{\"required\":true,\"type\":\"string\"}";
            BridgeObjectMapper.get().readValue(json, UploadFieldDefinition.class);
        } catch(JsonMappingException e) {
            assertEquals("name is required", errorFor((InvalidEntityException)e.getCause(), "name"));
        }
    }
    
    @Test
    public void requiresType() throws Exception {
        try {
            String json = "{\"name\":\"foo\",\"required\":true}";
            BridgeObjectMapper.get().readValue(json, UploadFieldDefinition.class);
        } catch(JsonMappingException e) {
            assertEquals("type is required", errorFor((InvalidEntityException)e.getCause(), "type"));
        }
    }

}
