package org.sagebionetworks.bridge.validators;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.google.common.collect.ImmutableList;
import org.junit.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoUploadSchema;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.models.upload.UploadSchemaType;
import org.springframework.validation.MapBindingResult;

public class UploadSchemaValidatorTest {
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
        Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, makeValidSchema());
    }

    @Test
    public void validateHappyCase2() {
        // set up a different schema to validate
        UploadSchema schema = UploadSchema.create();
        schema.setName("happy schema 2");
        schema.setRevision(1);
        schema.setSchemaId("happy-schema-2");
        schema.setStudyId("test-study");
        schema.setSchemaType(UploadSchemaType.IOS_SURVEY);

        // test field def list
        List<UploadFieldDefinition> fieldDefList = new ArrayList<>();
        fieldDefList.add(new UploadFieldDefinition.Builder().withName("foo-field")
                .withType(UploadFieldType.INT).build());
        fieldDefList.add(new UploadFieldDefinition.Builder().withName("bar-field")
                .withType(UploadFieldType.STRING).build());
        fieldDefList.add(new UploadFieldDefinition.Builder().withName("baz-field")
                .withType(UploadFieldType.MULTI_CHOICE).withMultiChoiceAnswerList("asdf", "jkl").build());
        schema.setFieldDefinitions(fieldDefList);

        // validate
        Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, schema);
    }

    @Test
    public void validateMinMaxAppVersionsValid() {
        // test cases: { minAppVersion, maxAppVersion }
        Integer[][] testCaseArray = {
                { null, null },
                { null, 10 },
                { 10, null },
                { 10, 20 },
                { 15, 15 },
        };

        for (Integer[] oneTestCase : testCaseArray) {
            Integer minAppVersion = oneTestCase[0];
            Integer maxAppVersion = oneTestCase[1];

            // make valid schema
            UploadSchema schema = makeValidSchema();
            schema.setMaxAppVersion("unit-test", maxAppVersion);
            schema.setMinAppVersion("unit-test", minAppVersion);

            // validate, should succeed
            Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, schema);
        }
    }

    @Test
    public void validateMinMaxAppVersionsInvalid() {
        // make valid schema, except with invalid min/maxAppVersions
        UploadSchema schema = makeValidSchema();
        schema.setMaxAppVersion("unit-test", 10);
        schema.setMinAppVersion("unit-test", 20);

        TestUtils.assertValidatorMessage(UploadSchemaValidator.INSTANCE, schema, "minAppVersions{unit-test}",
                "can't be greater than maxAppVersion");
    }

    @Test(expected = InvalidEntityException.class)
    public void validateNullName() {
        UploadSchema schema = makeValidSchema();
        schema.setName(null);
        Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, schema);
    }

    @Test(expected = InvalidEntityException.class)
    public void validateEmptyName() {
        UploadSchema schema = makeValidSchema();
        schema.setName("");
        Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, schema);
    }

    @Test(expected = InvalidEntityException.class)
    public void validateBlankName() {
        UploadSchema schema = makeValidSchema();
        schema.setName("   ");
        Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, schema);
    }

    @Test(expected = InvalidEntityException.class)
    public void validateNegativeRev() {
        UploadSchema schema = makeValidSchema();
        schema.setRevision(-1);
        Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, schema);
    }

    @Test(expected = InvalidEntityException.class)
    public void validateZeroRev() {
        UploadSchema schema = makeValidSchema();
        schema.setRevision(0);
        Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, schema);
    }

    @Test(expected = InvalidEntityException.class)
    public void validateNullSchemaId() {
        UploadSchema schema = makeValidSchema();
        schema.setSchemaId(null);
        Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, schema);
    }

    @Test(expected = InvalidEntityException.class)
    public void validateEmptySchemaId() {
        UploadSchema schema = makeValidSchema();
        schema.setSchemaId("");
        Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, schema);
    }

    @Test(expected = InvalidEntityException.class)
    public void validateBlankSchemaId() {
        UploadSchema schema = makeValidSchema();
        schema.setSchemaId("   ");
        Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, schema);
    }

    @Test(expected = InvalidEntityException.class)
    public void validateNullSchemaType() {
        UploadSchema schema = makeValidSchema();
        schema.setSchemaType(null);
        Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, schema);
    }

    @Test(expected = InvalidEntityException.class)
    public void validateNullStudyId() {
        UploadSchema schema = makeValidSchema();
        schema.setStudyId(null);
        Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, schema);
    }

    @Test(expected = InvalidEntityException.class)
    public void validateEmptyStudyId() {
        UploadSchema schema = makeValidSchema();
        schema.setStudyId("");
        Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, schema);
    }

    @Test(expected = InvalidEntityException.class)
    public void validateBlankStudyId() {
        UploadSchema schema = makeValidSchema();
        schema.setStudyId("   ");
        Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, schema);
    }

    @Test
    public void nullFieldDefList() {
        UploadSchema schema = makeValidSchema();
        schema.setFieldDefinitions(null);
        Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, schema);
    }

    @Test
    public void emptyFieldDefList() {
        UploadSchema schema = makeValidSchema();
        schema.setFieldDefinitions(ImmutableList.of());
        Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, schema);
    }

    @Test
    public void invalidFieldDef() {
        // This is tested in-depth in UploadFieldDefinitionListValidatorTest. Just test that we catch a non-trivial
        // error here.
        UploadSchema schema = makeSchemaWithField(new UploadFieldDefinition.Builder().withName(null)
                .withType(UploadFieldType.INT).build());
        assertValidatorMessage(UploadSchemaValidator.INSTANCE, schema, "fieldDefinitions[0].name", "is required");
    }

    @Test
    public void fieldDefTooManyBytes() {
        // 17 LargeTextAttachment fields to exceed the bytes limit.
        List<UploadFieldDefinition> fieldDefList = new ArrayList<>();
        for (int i = 0; i < 17; i++) {
            UploadFieldDefinition fieldDef = new UploadFieldDefinition.Builder().withName("field-" + i)
                    .withType(UploadFieldType.LARGE_TEXT_ATTACHMENT).build();
            fieldDefList.add(fieldDef);
        }

        UploadSchema schema = makeSchemaWithFieldList(fieldDefList);
        assertValidatorMessage(UploadSchemaValidator.INSTANCE, schema, "fieldDefinitions",
                "cannot be greater than 50000 bytes combined");
    }

    @Test
    public void fieldDefTooManyColumns() {
        // 11 multi-choice columns with 11 answers each to exceed the column limit.
        List<String> answerList = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            answerList.add("answer-" + i);
        }

        List<UploadFieldDefinition> fieldDefList = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            UploadFieldDefinition fieldDef = new UploadFieldDefinition.Builder().withName("field-" + i)
                    .withType(UploadFieldType.MULTI_CHOICE).withMultiChoiceAnswerList(answerList).build();
            fieldDefList.add(fieldDef);
        }

        UploadSchema schema = makeSchemaWithFieldList(fieldDefList);
        assertValidatorMessage(UploadSchemaValidator.INSTANCE, schema, "fieldDefinitions",
                "cannot be greater than 100 columns combined");
    }

    // Helper to make a valid schema
    private static UploadSchema makeValidSchema() {
        UploadFieldDefinition fieldDef = new UploadFieldDefinition.Builder().withName("test-field")
                .withType(UploadFieldType.ATTACHMENT_BLOB).build();
        return makeSchemaWithField(fieldDef);
    }

    // Make a schema with the given field that's otherwise valid.
    private static UploadSchema makeSchemaWithField(UploadFieldDefinition fieldDef) {
        return makeSchemaWithFieldList(ImmutableList.of(fieldDef));
    }

    private static UploadSchema makeSchemaWithFieldList(List<UploadFieldDefinition> fieldDefList) {
        UploadSchema schema = UploadSchema.create();
        schema.setFieldDefinitions(fieldDefList);
        schema.setName("valid schema");
        schema.setRevision(1);
        schema.setSchemaId("valid-schema");
        schema.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        schema.setSchemaType(UploadSchemaType.IOS_DATA);
        return schema;
    }
}
