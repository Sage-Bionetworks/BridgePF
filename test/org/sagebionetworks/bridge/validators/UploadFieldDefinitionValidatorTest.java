package org.sagebionetworks.bridge.validators;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
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

    public void assertError(UploadFieldDefinition def, String error, String fieldName) {
        try {
            Validate.entityThrowingException(validator, def);
            fail("Should have thrown exception");
        } catch (InvalidEntityException e) {
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
    
    // The deserialization of upload field definitions throws a JsonMappingException, wrapping an 
    // an InvalidEntityException. So we can verify this happening, but to return a complete and correct
    // error to the user, we need to use a custom UploadSchema deserializer.
    
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
            // Verify as well that we have the right type name (doesn't start with "Dynamo")
            InvalidEntityException iee = (InvalidEntityException)e.getCause();
            assertTrue(iee.getMessage().startsWith("UploadFieldDefinition is invalid: "));
            assertEquals("type is required", errorFor(iee, "type"));
        }
    }

}
