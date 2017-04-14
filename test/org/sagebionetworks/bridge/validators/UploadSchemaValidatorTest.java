package org.sagebionetworks.bridge.validators;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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

    @Test(expected = InvalidEntityException.class)
    public void validateNullFieldDefList() {
        UploadSchema schema = makeValidSchema();
        schema.setFieldDefinitions(null);
        Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, schema);
    }

    @Test(expected = InvalidEntityException.class)
    public void validateEmptyFieldDefList() {
        UploadSchema schema = makeValidSchema();
        schema.setFieldDefinitions(ImmutableList.of());
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

    @Test(expected = InvalidEntityException.class)
    public void nullFieldName() {
        UploadFieldDefinition fieldDef = new UploadFieldDefinition.Builder().withName(null)
                .withType(UploadFieldType.BOOLEAN).build();
        UploadSchema schema = makeSchemaWithField(fieldDef);
        Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, schema);
    }

    @Test(expected = InvalidEntityException.class)
    public void emptyFieldName() {
        UploadFieldDefinition fieldDef = new UploadFieldDefinition.Builder().withName("")
                .withType(UploadFieldType.BOOLEAN).build();
        UploadSchema schema = makeSchemaWithField(fieldDef);
        Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, schema);
    }

    @Test(expected = InvalidEntityException.class)
    public void blankFieldName() {
        UploadFieldDefinition fieldDef = new UploadFieldDefinition.Builder().withName("   ")
                .withType(UploadFieldType.BOOLEAN).build();
        UploadSchema schema = makeSchemaWithField(fieldDef);
        Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, schema);
    }

    @Test(expected = InvalidEntityException.class)
    public void invalidFieldName() {
        // The specifics of what is an invalid field name is covered in UploadUtilTest. This tests that the validator
        // validates invalid field names.
        UploadFieldDefinition fieldDef = new UploadFieldDefinition.Builder().withName("**invalid$field^name##")
                .withType(UploadFieldType.BOOLEAN).build();
        UploadSchema schema = makeSchemaWithField(fieldDef);
        Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, schema);
    }

    @Test
    public void keywordsAreValidChoiceValues() {
        UploadFieldDefinition fieldDef = new UploadFieldDefinition.Builder().withName("multi-choice-q")
                .withType(UploadFieldType.MULTI_CHOICE).withMultiChoiceAnswerList("true", "false", "select", "where")
                .build();
        UploadSchema schema = makeSchemaWithField(fieldDef);
        Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, schema);
    }

    @Test(expected = InvalidEntityException.class)
    public void invalidMultiChoiceAnswer() {
        UploadFieldDefinition fieldDef = new UploadFieldDefinition.Builder().withName("multi-choice-q")
                .withType(UploadFieldType.MULTI_CHOICE).withMultiChoiceAnswerList("!invalid@choice%").build();
        UploadSchema schema = makeSchemaWithField(fieldDef);
        Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, schema);
    }

    @Test
    public void validateUnboundedMaxLength() {
        // valid test cases
        // { unboundedText, maxLength }
        Object[][] testCases = {
                { null, null },
                { null, 24 },
                { false, null },
                { false, 24 },
                { true, null },
        };

        for (Object[] oneTestCase : testCases) {
            UploadFieldDefinition fieldDef = new UploadFieldDefinition.Builder().withName("field")
                    .withType(UploadFieldType.STRING).withUnboundedText((Boolean) oneTestCase[0])
                    .withMaxLength((Integer) oneTestCase[1]).build();
            UploadSchema schema = makeSchemaWithField(fieldDef);
            Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, schema);
        }

        // The only invalid test case is when unboundedText=true and maxLength is not null
        {
            UploadFieldDefinition fieldDef = new UploadFieldDefinition.Builder().withName("field")
                    .withType(UploadFieldType.STRING).withUnboundedText(true).withMaxLength(24).build();
            UploadSchema schema = makeSchemaWithField(fieldDef);

            try {
                Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, schema);
                fail("expected exception");
            } catch (InvalidEntityException ex) {
                assertTrue(ex.getMessage().contains("cannot specify unboundedText=true with a maxLength"));
            }
        }
    }

    @Test
    public void duplicateFieldName() {
        // set up schema to validate
        UploadSchema schema = makeValidSchema();

        // test field def list
        List<UploadFieldDefinition> fieldDefList = new ArrayList<>();
        fieldDefList.add(new UploadFieldDefinition.Builder().withName("foo-field")
                .withType(UploadFieldType.STRING).build());
        fieldDefList.add(new UploadFieldDefinition.Builder().withName("foo-field")
                .withType(UploadFieldType.INT).build());
        fieldDefList.add(new UploadFieldDefinition.Builder().withName("bar")
                .withType(UploadFieldType.MULTI_CHOICE).withMultiChoiceAnswerList("bar", "other")
                .withAllowOtherChoices(true).build());
        fieldDefList.add(new UploadFieldDefinition.Builder().withName("bar.bar").withType(UploadFieldType.STRING)
                .build());
        fieldDefList.add(new UploadFieldDefinition.Builder().withName("baz").withType(UploadFieldType.TIMESTAMP)
                .build());
        fieldDefList.add(new UploadFieldDefinition.Builder().withName("baz.timezone")
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
        assertTrue(thrownEx.getMessage().contains("conflict in field names or sub-field names: bar.bar, "
                + "bar.other, baz.timezone, foo-field"));
    }

    @Test
    public void multiChoiceWithNoAnswerList() {
        // set up schema to validate
        UploadSchema schema = makeValidSchema();

        // test field def list
        List<UploadFieldDefinition> fieldDefList = new ArrayList<>();
        fieldDefList.add(new UploadFieldDefinition.Builder().withName("multi-choice-null")
                .withType(UploadFieldType.MULTI_CHOICE).build());
        fieldDefList.add(new UploadFieldDefinition.Builder().withName("multi-choice-empty")
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

    // Helper to make a valid schema
    private static UploadSchema makeValidSchema() {
        UploadFieldDefinition fieldDef = new UploadFieldDefinition.Builder().withName("test-field")
                .withType(UploadFieldType.ATTACHMENT_BLOB).build();
        return makeSchemaWithField(fieldDef);
    }

    // Make a schema with the given field that's otherwise valid.
    private static UploadSchema makeSchemaWithField(UploadFieldDefinition fieldDef) {
        UploadSchema schema = UploadSchema.create();
        schema.setFieldDefinitions(ImmutableList.of(fieldDef));
        schema.setName("valid schema");
        schema.setRevision(1);
        schema.setSchemaId("valid-schema");
        schema.setStudyId(TestConstants.TEST_STUDY_IDENTIFIER);
        schema.setSchemaType(UploadSchemaType.IOS_DATA);
        return schema;
    }
}
