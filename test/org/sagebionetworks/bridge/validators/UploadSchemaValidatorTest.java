package org.sagebionetworks.bridge.validators;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.junit.Test;
import org.sagebionetworks.bridge.dynamodb.DynamoUploadFieldDefinition;
import org.sagebionetworks.bridge.dynamodb.DynamoUploadSchema;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.models.upload.UploadSchemaType;
import org.springframework.validation.MapBindingResult;

public class UploadSchemaValidatorTest {

    private UploadSchemaValidator validator = UploadSchemaValidator.INSTANCE;
    
    private String errorFor(InvalidEntityException e, String field) {
        List<String> errors = e.getErrors().get(field);
        assertNotNull(errors);
        assertEquals(1, errors.size());
        return errors.get(0);
    }
    
    private void assertWillGenerateValidationError(UploadSchema schema, String error, String fieldName) {
        try {
            Validate.entityThrowingException(validator, schema);
            fail("Should have thrown an exception");
        } catch(InvalidEntityException e) {
            assertEquals(error, errorFor(e, fieldName));
        }
    }
    
    // branch coverage
    @Test
    public void validatorSupportsClass() {
        assertTrue(UploadSchemaValidator.INSTANCE.supports(UploadSchema.class));
    }

    // branch coverage
    @Test
    public void validatorSupportsSubclass() {
        assertTrue(UploadSchemaValidator.INSTANCE.supports(DynamoUploadSchema.class));
    }

    // branch coverage
    @Test
    public void validatorDoesntSupportClass() {
        assertFalse(UploadSchemaValidator.INSTANCE.supports(String.class));
    }

    // branch coverage
    // we call the validator directly, since Validate.validateThrowingException filters out nulls and wrong types
    @Test
    public void validateNull() {
        MapBindingResult errors = new MapBindingResult(new HashMap<>(), "UploadSchema");
        UploadSchemaValidator.INSTANCE.validate(null, errors);
        assertTrue(errors.hasErrors());
    }

    // branch coverage
    // we call the validator directly, since Validate.validateThrowingException filters out nulls and wrong types
    @Test
    public void validateWrongClass() {
        MapBindingResult errors = new MapBindingResult(new HashMap<>(), "UploadSchema");
        UploadSchemaValidator.INSTANCE.validate("this is the wrong class", errors);
        assertTrue(errors.hasErrors());
    }

    @Test
    public void validateHappyCase() {
        // set up schema to validate
        DynamoUploadSchema schema = new DynamoUploadSchema();
        schema.setName("happy schema");
        schema.setSchemaId("happy-schema");
        schema.setStudyId("test-study");
        schema.setSchemaType(UploadSchemaType.IOS_DATA);

        // test field def list
        List<UploadFieldDefinition> fieldDefList = new ArrayList<>();
        fieldDefList.add(new DynamoUploadFieldDefinition.Builder().withName("test-field")
                .withType(UploadFieldType.ATTACHMENT_BLOB).build());
        schema.setFieldDefinitions(fieldDefList);

        // validate
        Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, schema);
    }

    @Test
    public void validateHappyCase2() {
        // set up schema to validate
        DynamoUploadSchema schema = new DynamoUploadSchema();
        schema.setName("happy schema 2");
        schema.setRevision(1);
        schema.setSchemaId("happy-schema-2");
        schema.setStudyId("test-study");
        schema.setSchemaType(UploadSchemaType.IOS_SURVEY);

        // test field def list
        List<UploadFieldDefinition> fieldDefList = new ArrayList<>();
        fieldDefList.add(new DynamoUploadFieldDefinition.Builder().withName("foo-field")
                .withType(UploadFieldType.INT).build());
        fieldDefList.add(new DynamoUploadFieldDefinition.Builder().withName("bar-field")
                .withType(UploadFieldType.STRING).build());
        fieldDefList.add(new DynamoUploadFieldDefinition.Builder().withName("baz-field")
                .withType(UploadFieldType.MULTI_CHOICE).withMultiChoiceAnswerList("asdf", "jkl").build());
        schema.setFieldDefinitions(fieldDefList);

        // validate
        Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, schema);
    }

    @Test(expected = InvalidEntityException.class)
    public void validateNullFieldDefList() {
        // set up schema to validate
        DynamoUploadSchema schema = new DynamoUploadSchema();
        schema.setName("test schema");
        schema.setSchemaId("test-schema");
        schema.setStudyId("test-study");
        schema.setSchemaType(UploadSchemaType.IOS_DATA);

        // validate
        Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, schema);
    }

    @Test(expected = InvalidEntityException.class)
    public void validateEmptyFieldDefList() {
        // set up schema to validate
        DynamoUploadSchema schema = new DynamoUploadSchema();
        schema.setName("test schema");
        schema.setSchemaId("test-schema");
        schema.setStudyId("test-study");
        schema.setFieldDefinitions(Collections.<UploadFieldDefinition>emptyList());
        schema.setSchemaType(UploadSchemaType.IOS_DATA);

        // validate
        Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, schema);
    }

    @Test(expected = InvalidEntityException.class)
    public void validateNullName() {
        // set up schema to validate
        DynamoUploadSchema schema = new DynamoUploadSchema();
        schema.setSchemaId("test-schema");
        schema.setStudyId("test-study");
        schema.setSchemaType(UploadSchemaType.IOS_DATA);

        // test field def list
        List<UploadFieldDefinition> fieldDefList = new ArrayList<>();
        fieldDefList.add(new DynamoUploadFieldDefinition.Builder().withName("good-field")
                .withType(UploadFieldType.ATTACHMENT_BLOB).build());
        schema.setFieldDefinitions(fieldDefList);

        // validate
        Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, schema);
    }

    @Test(expected = InvalidEntityException.class)
    public void validateEmptyName() {
        // set up schema to validate
        DynamoUploadSchema schema = new DynamoUploadSchema();
        schema.setName("");
        schema.setSchemaId("test-schema");
        schema.setStudyId("test-study");
        schema.setSchemaType(UploadSchemaType.IOS_DATA);

        // test field def list
        List<UploadFieldDefinition> fieldDefList = new ArrayList<>();
        fieldDefList.add(new DynamoUploadFieldDefinition.Builder().withName("good-field")
                .withType(UploadFieldType.ATTACHMENT_BLOB).build());
        schema.setFieldDefinitions(fieldDefList);

        // validate
        Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, schema);
    }

    @Test(expected = InvalidEntityException.class)
    public void validateNegativeRev() {
        // set up schema to validate
        DynamoUploadSchema schema = new DynamoUploadSchema();
        schema.setName("test schema");
        schema.setRevision(-1);
        schema.setSchemaId("test-schema");
        schema.setStudyId("test-study");
        schema.setSchemaType(UploadSchemaType.IOS_DATA);

        // test field def list
        List<UploadFieldDefinition> fieldDefList = new ArrayList<>();
        fieldDefList.add(new DynamoUploadFieldDefinition.Builder().withName("good-field")
                .withType(UploadFieldType.ATTACHMENT_BLOB).build());
        schema.setFieldDefinitions(fieldDefList);

        // validate
        Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, schema);
    }

    @Test(expected = InvalidEntityException.class)
    public void validateNullSchemaId() {
        // set up schema to validate
        DynamoUploadSchema schema = new DynamoUploadSchema();
        schema.setName("test schema");
        schema.setStudyId("test-study");
        schema.setSchemaType(UploadSchemaType.IOS_DATA);

        // test field def list
        List<UploadFieldDefinition> fieldDefList = new ArrayList<>();
        fieldDefList.add(new DynamoUploadFieldDefinition.Builder().withName("good-field")
                .withType(UploadFieldType.ATTACHMENT_BLOB).build());
        schema.setFieldDefinitions(fieldDefList);

        // validate
        Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, schema);
    }

    @Test(expected = InvalidEntityException.class)
    public void validateEmptySchemaId() {
        // set up schema to validate
        DynamoUploadSchema schema = new DynamoUploadSchema();
        schema.setName("test schema");
        schema.setSchemaId("");
        schema.setStudyId("test-study");
        schema.setSchemaType(UploadSchemaType.IOS_DATA);

        // test field def list
        List<UploadFieldDefinition> fieldDefList = new ArrayList<>();
        fieldDefList.add(new DynamoUploadFieldDefinition.Builder().withName("good-field")
                .withType(UploadFieldType.ATTACHMENT_BLOB).build());
        schema.setFieldDefinitions(fieldDefList);

        // validate
        Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, schema);
    }

    @Test(expected = InvalidEntityException.class)
    public void validateNullSchemaType() {
        // set up schema to validate
        DynamoUploadSchema schema = new DynamoUploadSchema();
        schema.setName("test schema");
        schema.setSchemaId("test-schema");
        schema.setStudyId("test-study");

        // test field def list
        List<UploadFieldDefinition> fieldDefList = new ArrayList<>();
        fieldDefList.add(new DynamoUploadFieldDefinition.Builder().withName("test-field")
                .withType(UploadFieldType.ATTACHMENT_BLOB).build());
        schema.setFieldDefinitions(fieldDefList);

        // validate
        Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, schema);
    }

    // These tests are redundant, but I wrote them specifically to test the messages that are sent
    // back, and some use JSON to test what happens when properties are missing
    
    @Test
    public void requiresAtLeastOneFieldDefinition() throws Exception {
        String json = "{\"name\":\"Upload Test iOS Survey\",\"schemaId\":\"upload-test-ios-survey\",\"schemaType\":\"ios_survey\",\"revision\":1}";
        UploadSchema schema = BridgeObjectMapper.get().readValue(json, DynamoUploadSchema.class);
        assertWillGenerateValidationError(schema, "fieldDefinitions requires at least one definition", "fieldDefinitions");
    }
    
    @Test
    public void requiresName() throws Exception {
        String json = "{\"schemaId\":\"upload-test-ios-survey\",\"schemaType\":\"ios_survey\",\"revision\":1,\"fieldDefinitions\":[{\"name\":\"foo\",\"required\":true,\"type\":\"string\"},{\"name\":\"bar\",\"required\":true,\"type\":\"int\"}]}";
        UploadSchema schema = BridgeObjectMapper.get().readValue(json, DynamoUploadSchema.class);
        assertWillGenerateValidationError(schema, "name is required", "name");
    }
    
    @Test
    public void requiresNonNegativeRevision() throws Exception {
        String json = "{\"name\":\"Upload Test iOS Survey\",\"schemaId\":\"upload-test-ios-survey\",\"schemaType\":\"ios_survey\",\"revision\":-1,\"fieldDefinitions\":[{\"name\":\"foo\",\"required\":true,\"type\":\"string\"},{\"name\":\"bar\",\"required\":true,\"type\":\"int\"}]}";
        UploadSchema schema = BridgeObjectMapper.get().readValue(json, DynamoUploadSchema.class);
        assertWillGenerateValidationError(schema, "revision must be equal to or greater than zero", "revision");
    }
    
    @Test
    public void requiresSchemaId() throws Exception {
        String json = "{\"name\":\"Upload Test iOS Survey\",\"schemaType\":\"ios_survey\",\"revision\":-1,\"fieldDefinitions\":[{\"name\":\"foo\",\"required\":true,\"type\":\"string\"},{\"name\":\"bar\",\"required\":true,\"type\":\"int\"}]}";
        UploadSchema schema = BridgeObjectMapper.get().readValue(json, DynamoUploadSchema.class);
        assertWillGenerateValidationError(schema, "schemaId is required", "schemaId");
    }
    
    @Test
    public void requiresSchemaType() throws Exception {
        String json = "{\"name\":\"Upload Test iOS Survey\",\"revision\":1,\"fieldDefinitions\":[{\"name\":\"foo\",\"required\":true,\"type\":\"string\"},{\"name\":\"bar\",\"required\":true,\"type\":\"int\"}]}";
        UploadSchema schema = BridgeObjectMapper.get().readValue(json, DynamoUploadSchema.class);
        assertWillGenerateValidationError(schema, "schemaType is required", "schemaType");
    }
    
    @Test
    public void requiresFieldDefinitionHasName() throws Exception {
        // missing property
        String json = "{\"name\":\"Upload Test iOS Survey\",\"schemaId\":\"upload-test-ios-survey\",\"schemaType\":\"ios_survey\",\"revision\":1,\"fieldDefinitions\":[{\"name\":\"foo\",\"required\":true}]}";
        UploadSchema schema = BridgeObjectMapper.get().readValue(json, UploadSchema.class);
        assertWillGenerateValidationError(schema, "fieldDefinitions[0].type is required", "fieldDefinitions[0].type");
        
        // null property
        json = "{\"name\":\"Upload Test iOS Survey\",\"schemaId\":\"upload-test-ios-survey\",\"schemaType\":\"ios_survey\",\"revision\":1,\"fieldDefinitions\":[{\"name\":\"foo\",\"required\":true,\"type\":null}]}";
        schema = BridgeObjectMapper.get().readValue(json, UploadSchema.class);
        assertWillGenerateValidationError(schema, "fieldDefinitions[0].type is required", "fieldDefinitions[0].type");
    }
    
    @Test
    public void requiresFieldDefinitionHasType() throws Exception {
        // missing property
        String json = "{\"name\":\"Upload Test iOS Survey\",\"schemaId\":\"upload-test-ios-survey\",\"schemaType\":\"ios_survey\",\"revision\":1,\"fieldDefinitions\":[{\"required\":true,\"type\":\"string\"}]}";
        UploadSchema schema = BridgeObjectMapper.get().readValue(json, UploadSchema.class);
        assertWillGenerateValidationError(schema, "fieldDefinitions[0].name is required", "fieldDefinitions[0].name");
        
        // empty property
        json = "{\"name\":\"Upload Test iOS Survey\",\"schemaId\":\"upload-test-ios-survey\",\"schemaType\":\"ios_survey\",\"revision\":1,\"fieldDefinitions\":[{\"name\":\"\",\"required\":true,\"type\":\"string\"}]}";
        schema = BridgeObjectMapper.get().readValue(json, UploadSchema.class);
        assertWillGenerateValidationError(schema, "fieldDefinitions[0].name is required", "fieldDefinitions[0].name");
        
        // null property
        json = "{\"name\":\"Upload Test iOS Survey\",\"schemaId\":\"upload-test-ios-survey\",\"schemaType\":\"ios_survey\",\"revision\":1,\"fieldDefinitions\":[{\"name\":null,\"required\":true,\"type\":\"string\"}]}";
        schema = BridgeObjectMapper.get().readValue(json, UploadSchema.class);
        assertWillGenerateValidationError(schema, "fieldDefinitions[0].name is required", "fieldDefinitions[0].name");
    }

    @Test
    public void duplicateFieldName() {
        // set up schema to validate
        DynamoUploadSchema schema = new DynamoUploadSchema();
        schema.setName("Dupe Fields");
        schema.setSchemaId("dupe-field-schema");
        schema.setStudyId("test-study");
        schema.setSchemaType(UploadSchemaType.IOS_SURVEY);

        // test field def list
        List<UploadFieldDefinition> fieldDefList = new ArrayList<>();
        fieldDefList.add(new DynamoUploadFieldDefinition.Builder().withName("foo-field")
                .withType(UploadFieldType.STRING).build());
        fieldDefList.add(new DynamoUploadFieldDefinition.Builder().withName("foo-field")
                .withType(UploadFieldType.INT).build());
        fieldDefList.add(new DynamoUploadFieldDefinition.Builder().withName("bar")
                .withType(UploadFieldType.MULTI_CHOICE).withMultiChoiceAnswerList("bar", "other")
                .withAllowOtherChoices(true).build());
        fieldDefList.add(new DynamoUploadFieldDefinition.Builder().withName("bar.bar").withType(UploadFieldType.STRING)
                .build());
        fieldDefList.add(new DynamoUploadFieldDefinition.Builder().withName("baz").withType(UploadFieldType.TIMESTAMP)
                .build());
        fieldDefList.add(new DynamoUploadFieldDefinition.Builder().withName("baz.timezone")
                .withType(UploadFieldType.STRING).build());
        schema.setFieldDefinitions(fieldDefList);

        // validate
        Exception thrownEx = null;
        try {
            Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, schema);
            fail("expected exception");
        } catch (InvalidEntityException ex) {
            thrownEx = ex;
        }
        assertTrue(thrownEx.getMessage().contains("conflict in field names or sub-field names: bar.bar, bar.other, " +
                "baz.timezone, foo-field"));
    }

    @Test
    public void multiChoiceWithNoAnswerList() {
        // set up schema to validate
        DynamoUploadSchema schema = new DynamoUploadSchema();
        schema.setName("Multi-Choice Schema");
        schema.setSchemaId("multi-choice-schema");
        schema.setStudyId("test-study");
        schema.setSchemaType(UploadSchemaType.IOS_SURVEY);

        // test field def list
        List<UploadFieldDefinition> fieldDefList = new ArrayList<>();
        fieldDefList.add(new DynamoUploadFieldDefinition.Builder().withName("multi-choice-null")
                .withType(UploadFieldType.MULTI_CHOICE).build());
        fieldDefList.add(new DynamoUploadFieldDefinition.Builder().withName("multi-choice-empty")
                .withType(UploadFieldType.MULTI_CHOICE).withMultiChoiceAnswerList().build());
        schema.setFieldDefinitions(fieldDefList);

        // validate
        Exception thrownEx = null;
        try {
            Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, schema);
            fail("expected exception");
        } catch (InvalidEntityException ex) {
            thrownEx = ex;
        }
        assertTrue(thrownEx.getMessage().contains("must be specified for MULTI_CHOICE field multi-choice-null"));
        assertTrue(thrownEx.getMessage().contains("must be specified for MULTI_CHOICE field multi-choice-empty"));
    }
}
