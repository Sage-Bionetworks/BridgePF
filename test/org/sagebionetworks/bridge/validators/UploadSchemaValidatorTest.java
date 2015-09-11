package org.sagebionetworks.bridge.validators;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.List;

import org.junit.Test;
import org.sagebionetworks.bridge.dynamodb.DynamoUploadSchema;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.springframework.validation.MapBindingResult;

import com.google.common.collect.Maps;

public class UploadSchemaValidatorTest {

    private UploadSchemaValidator validator = UploadSchemaValidator.INSTANCE;
    
    private String errorFor(InvalidEntityException e, String field) {
        List<String> errors = e.getErrors().get(field);
        assertNotNull(errors);
        assertEquals(1, errors.size());
        return errors.get(0);
    }
    
    private void assertErrorExists(UploadSchema schema, String error, String fieldName) {
        try {
            Validate.entityThrowingException(validator, schema);
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertEquals(error, errorFor(e, fieldName));
        }
    }
    
    @Test
    public void requiresObject() {
        MapBindingResult errors = new MapBindingResult(Maps.newHashMap(), "UploadSchema");
        validator.validate(null, errors);
        String error = Validate.convertBindingResultToMessage(errors);
        assertEquals("UploadSchema is invalid: uploadSchema cannot be null", error);
    }
    
    @Test
    public void requiresUploadSchema() {
        MapBindingResult errors = new MapBindingResult(Maps.newHashMap(), "UploadSchema");
        validator.validate(new String("not the right object type"), errors);
        String error = Validate.convertBindingResultToMessage(errors);
        assertEquals("UploadSchema is invalid: uploadSchema is the wrong type", error);
    }
    
    @Test
    public void requiresAtLeastOneFieldDefinition() throws Exception {
        String json = "{\"name\":\"Upload Test iOS Survey\",\"schemaId\":\"upload-test-ios-survey\",\"schemaType\":\"ios_survey\",\"revision\":1}";
        UploadSchema schema = BridgeObjectMapper.get().readValue(json, DynamoUploadSchema.class);
        assertErrorExists(schema, "fieldDefinitions requires at least one definition", "fieldDefinitions");
    }
    
    @Test
    public void requiresName() throws Exception {
        String json = "{\"schemaId\":\"upload-test-ios-survey\",\"schemaType\":\"ios_survey\",\"revision\":1,\"fieldDefinitions\":[{\"name\":\"foo\",\"required\":true,\"type\":\"string\"},{\"name\":\"bar\",\"required\":true,\"type\":\"int\"}]}";
        UploadSchema schema = BridgeObjectMapper.get().readValue(json, DynamoUploadSchema.class);
        assertErrorExists(schema, "name is required", "name");
    }
    
    @Test
    public void requiresNonNegativeRevision() throws Exception {
        String json = "{\"name\":\"Upload Test iOS Survey\",\"schemaId\":\"upload-test-ios-survey\",\"schemaType\":\"ios_survey\",\"revision\":-1,\"fieldDefinitions\":[{\"name\":\"foo\",\"required\":true,\"type\":\"string\"},{\"name\":\"bar\",\"required\":true,\"type\":\"int\"}]}";
        UploadSchema schema = BridgeObjectMapper.get().readValue(json, DynamoUploadSchema.class);
        assertErrorExists(schema, "revision must be a positive integer", "revision");
    }
    
    @Test
    public void requiresSchemaId() throws Exception {
        String json = "{\"name\":\"Upload Test iOS Survey\",\"schemaType\":\"ios_survey\",\"revision\":-1,\"fieldDefinitions\":[{\"name\":\"foo\",\"required\":true,\"type\":\"string\"},{\"name\":\"bar\",\"required\":true,\"type\":\"int\"}]}";
        UploadSchema schema = BridgeObjectMapper.get().readValue(json, DynamoUploadSchema.class);
        assertErrorExists(schema, "schemaId is required", "schemaId");
    }
    
    @Test
    public void requiresSchemaType() throws Exception {
        String json = "{\"name\":\"Upload Test iOS Survey\",\"revision\":1,\"fieldDefinitions\":[{\"name\":\"foo\",\"required\":true,\"type\":\"string\"},{\"name\":\"bar\",\"required\":true,\"type\":\"int\"}]}";
        UploadSchema schema = BridgeObjectMapper.get().readValue(json, DynamoUploadSchema.class);
        assertErrorExists(schema, "schemaType is required", "schemaType");
    }
    
}
