package org.sagebionetworks.bridge.validators;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import com.google.common.collect.ImmutableList;
import org.junit.Test;

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
        // set up schema to validate
        UploadSchema schema = UploadSchema.create();
        schema.setName("happy schema");
        schema.setSchemaId("happy-schema");
        schema.setStudyId("test-study");
        schema.setSchemaType(UploadSchemaType.IOS_DATA);

        // test field def list
        List<UploadFieldDefinition> fieldDefList = new ArrayList<>();
        fieldDefList.add(new UploadFieldDefinition.Builder().withName("test-field")
                .withType(UploadFieldType.ATTACHMENT_BLOB).build());
        schema.setFieldDefinitions(fieldDefList);

        // validate
        Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, schema);
    }

    @Test
    public void validateHappyCase2() {
        // set up schema to validate
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
        // set up schema to validate
        UploadSchema schema = UploadSchema.create();
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
        UploadSchema schema = UploadSchema.create();
        schema.setName("test schema");
        schema.setSchemaId("test-schema");
        schema.setStudyId("test-study");
        schema.setFieldDefinitions(Collections.<UploadFieldDefinition>emptyList());
        schema.setSchemaType(UploadSchemaType.IOS_DATA);

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
            UploadSchema schema = UploadSchema.create();
            schema.setFieldDefinitions(ImmutableList.of(new UploadFieldDefinition.Builder()
                    .withName("test-field").withType(UploadFieldType.INT).build()));
            schema.setMaxAppVersion("unit-test", maxAppVersion);
            schema.setMinAppVersion("unit-test", minAppVersion);
            schema.setName("Test Schema");
            schema.setSchemaId("test-schema");
            schema.setSchemaType(UploadSchemaType.IOS_DATA);

            // validate, should succeed
            Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, schema);
        }
    }

    @Test
    public void validateMinMaxAppVersionsInvalid() {
        // make valid schema, except with invalid min/maxAppVersions
        UploadSchema schema = UploadSchema.create();
        schema.setFieldDefinitions(ImmutableList.of(new UploadFieldDefinition.Builder()
                .withName("test-field").withType(UploadFieldType.INT).build()));
        schema.setMaxAppVersion("unit-test", 10);
        schema.setMinAppVersion("unit-test", 20);
        schema.setName("Test Schema");
        schema.setSchemaId("test-schema");
        schema.setSchemaType(UploadSchemaType.IOS_DATA);

        TestUtils.assertValidatorMessage(UploadSchemaValidator.INSTANCE, schema, "minAppVersions{unit-test}",
                "can't be greater than maxAppVersion");
    }

    @Test(expected = InvalidEntityException.class)
    public void validateNullName() {
        // set up schema to validate
        UploadSchema schema = UploadSchema.create();
        schema.setSchemaId("test-schema");
        schema.setStudyId("test-study");
        schema.setSchemaType(UploadSchemaType.IOS_DATA);

        // test field def list
        List<UploadFieldDefinition> fieldDefList = new ArrayList<>();
        fieldDefList.add(new UploadFieldDefinition.Builder().withName("good-field")
                .withType(UploadFieldType.ATTACHMENT_BLOB).build());
        schema.setFieldDefinitions(fieldDefList);

        // validate
        Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, schema);
    }

    @Test(expected = InvalidEntityException.class)
    public void validateEmptyName() {
        // set up schema to validate
        UploadSchema schema = UploadSchema.create();
        schema.setName("");
        schema.setSchemaId("test-schema");
        schema.setStudyId("test-study");
        schema.setSchemaType(UploadSchemaType.IOS_DATA);

        // test field def list
        List<UploadFieldDefinition> fieldDefList = new ArrayList<>();
        fieldDefList.add(new UploadFieldDefinition.Builder().withName("good-field")
                .withType(UploadFieldType.ATTACHMENT_BLOB).build());
        schema.setFieldDefinitions(fieldDefList);

        // validate
        Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, schema);
    }

    @Test(expected = InvalidEntityException.class)
    public void validateNegativeRev() {
        // set up schema to validate
        UploadSchema schema = UploadSchema.create();
        schema.setName("test schema");
        schema.setRevision(-1);
        schema.setSchemaId("test-schema");
        schema.setStudyId("test-study");
        schema.setSchemaType(UploadSchemaType.IOS_DATA);

        // test field def list
        List<UploadFieldDefinition> fieldDefList = new ArrayList<>();
        fieldDefList.add(new UploadFieldDefinition.Builder().withName("good-field")
                .withType(UploadFieldType.ATTACHMENT_BLOB).build());
        schema.setFieldDefinitions(fieldDefList);

        // validate
        Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, schema);
    }

    @Test(expected = InvalidEntityException.class)
    public void validateNullSchemaId() {
        // set up schema to validate
        UploadSchema schema = UploadSchema.create();
        schema.setName("test schema");
        schema.setStudyId("test-study");
        schema.setSchemaType(UploadSchemaType.IOS_DATA);

        // test field def list
        List<UploadFieldDefinition> fieldDefList = new ArrayList<>();
        fieldDefList.add(new UploadFieldDefinition.Builder().withName("good-field")
                .withType(UploadFieldType.ATTACHMENT_BLOB).build());
        schema.setFieldDefinitions(fieldDefList);

        // validate
        Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, schema);
    }

    @Test(expected = InvalidEntityException.class)
    public void validateEmptySchemaId() {
        // set up schema to validate
        UploadSchema schema = UploadSchema.create();
        schema.setName("test schema");
        schema.setSchemaId("");
        schema.setStudyId("test-study");
        schema.setSchemaType(UploadSchemaType.IOS_DATA);

        // test field def list
        List<UploadFieldDefinition> fieldDefList = new ArrayList<>();
        fieldDefList.add(new UploadFieldDefinition.Builder().withName("good-field")
                .withType(UploadFieldType.ATTACHMENT_BLOB).build());
        schema.setFieldDefinitions(fieldDefList);

        // validate
        Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, schema);
    }

    @Test(expected = InvalidEntityException.class)
    public void validateNullSchemaType() {
        // set up schema to validate
        UploadSchema schema = UploadSchema.create();
        schema.setName("test schema");
        schema.setSchemaId("test-schema");
        schema.setStudyId("test-study");

        // test field def list
        List<UploadFieldDefinition> fieldDefList = new ArrayList<>();
        fieldDefList.add(new UploadFieldDefinition.Builder().withName("test-field")
                .withType(UploadFieldType.ATTACHMENT_BLOB).build());
        schema.setFieldDefinitions(fieldDefList);

        // validate
        Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, schema);
    }

    @Test(expected = InvalidEntityException.class)
    public void invalidFieldName() {
        // The specifics of what is an invalid field name is covered in UploadUtilTest. This tests that the validator
        // validates invalid field names.
        UploadSchema schema = UploadSchema.create();
        schema.setName("Test Schema");
        schema.setSchemaId("test-schema");
        schema.setStudyId("test-study");
        schema.setSchemaType(UploadSchemaType.IOS_DATA);

        // test field def list
        List<UploadFieldDefinition> fieldDefList = new ArrayList<>();
        fieldDefList.add(new UploadFieldDefinition.Builder().withName("**invalid$field^name##")
                .withType(UploadFieldType.BOOLEAN).build());
        schema.setFieldDefinitions(fieldDefList);

        // validate
        Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, schema);
    }

    @Test
    public void keywordsAreValidChoiceValues() {
        // Similarly
        UploadSchema schema = UploadSchema.create();
        schema.setName("Test Schema");
        schema.setSchemaId("test-schema");
        schema.setStudyId("test-study");
        schema.setSchemaType(UploadSchemaType.IOS_DATA);

        // test field def list
        List<UploadFieldDefinition> fieldDefList = new ArrayList<>();
        fieldDefList.add(new UploadFieldDefinition.Builder().withName("multi-choice-q")
                .withType(UploadFieldType.MULTI_CHOICE)
                .withMultiChoiceAnswerList("true", "false", "select", "where").build());
        schema.setFieldDefinitions(fieldDefList);

        // validate
        Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, schema);
    }

    @Test(expected = InvalidEntityException.class)
    public void invalidMultiChoiceAnswer() {
        // Similarly
        UploadSchema schema = UploadSchema.create();
        schema.setName("Test Schema");
        schema.setSchemaId("test-schema");
        schema.setStudyId("test-study");
        schema.setSchemaType(UploadSchemaType.IOS_DATA);

        // test field def list
        List<UploadFieldDefinition> fieldDefList = new ArrayList<>();
        fieldDefList.add(new UploadFieldDefinition.Builder().withName("multi-choice-q")
                .withType(UploadFieldType.MULTI_CHOICE).withMultiChoiceAnswerList("!invalid@choice%").build());
        schema.setFieldDefinitions(fieldDefList);

        // validate
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

        // Since schemas are mutable, we can share a schema for all test cases.
        UploadSchema schema = UploadSchema.create();
        schema.setName("happy schema");
        schema.setStudyId("test-study");
        schema.setSchemaId("happy-schema");
        schema.setRevision(4);
        schema.setSchemaType(UploadSchemaType.IOS_DATA);

        for (Object[] oneTestCase : testCases) {
            // We need to create a new field def list for every test case though.
            UploadFieldDefinition fieldDef = new UploadFieldDefinition.Builder().withName("field")
                    .withType(UploadFieldType.STRING).withUnboundedText((Boolean) oneTestCase[0])
                    .withMaxLength((Integer) oneTestCase[1]).build();
            schema.setFieldDefinitions(ImmutableList.of(fieldDef));
            Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, schema);
        }

        // The only invalid test case is when unboundedText=true and maxLength is not null
        {
            UploadFieldDefinition fieldDef = new UploadFieldDefinition.Builder().withName("field")
                    .withType(UploadFieldType.STRING).withUnboundedText(true).withMaxLength(24).build();
            schema.setFieldDefinitions(ImmutableList.of(fieldDef));

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
        UploadSchema schema = UploadSchema.create();
        schema.setName("Dupe Fields");
        schema.setSchemaId("dupe-field-schema");
        schema.setStudyId("test-study");
        schema.setSchemaType(UploadSchemaType.IOS_SURVEY);

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
        UploadSchema schema = UploadSchema.create();
        schema.setName("Multi-Choice Schema");
        schema.setSchemaId("multi-choice-schema");
        schema.setStudyId("test-study");
        schema.setSchemaType(UploadSchemaType.IOS_SURVEY);

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
}
