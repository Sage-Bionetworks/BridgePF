package org.sagebionetworks.bridge.validators;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.springframework.validation.MapBindingResult;

import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;
import org.sagebionetworks.bridge.upload.UploadUtil;

public class UploadFieldDefinitionListValidatorTest {
    private static final String FIELD_LIST_ATTR_NAME = "fieldDefList";
    private static final String FIELD_NAME = "test-field";

    @Test
    public void success() {
        assertValidatorMessage(ImmutableList.of(makeValidFieldDefBuilder().build()), false, null, null);
    }

    @Test
    public void nullField() {
        // ImmutableList can't contain nulls, so use an ArrayList.
        List<UploadFieldDefinition> fieldDefList = new ArrayList<>();
        fieldDefList.add(null);
        assertValidatorMessage(fieldDefList, true, FIELD_LIST_ATTR_NAME + "[0]", "is required");
    }

    @Test
    public void nullFieldName() {
        assertValidatorMessage(ImmutableList.of(makeValidFieldDefBuilder().withName(null).build()), true,
                FIELD_LIST_ATTR_NAME + "[0].name", "is required");
    }

    @Test
    public void emptyFieldName() {
        assertValidatorMessage(ImmutableList.of(makeValidFieldDefBuilder().withName("").build()), true,
                FIELD_LIST_ATTR_NAME + "[0].name", "is required");
    }

    @Test
    public void blankFieldName() {
        assertValidatorMessage(ImmutableList.of(makeValidFieldDefBuilder().withName("   ").build()), true,
                FIELD_LIST_ATTR_NAME + "[0].name", "is required");
    }

    @Test
    public void invalidFieldName() {
        // The specifics of what is an invalid field name is covered in UploadUtilTest. This tests that the validator
        // validates invalid field names.
        String fieldName = "**invalid$field^name##";
        assertValidatorMessage(ImmutableList.of(makeValidFieldDefBuilder().withName(fieldName).build()), true,
                FIELD_LIST_ATTR_NAME + "[0].name", String.format(UploadUtil.INVALID_FIELD_NAME_ERROR_MESSAGE, fieldName));
    }

    @Test
    public void nullFieldType() {
        assertValidatorMessage(ImmutableList.of(makeValidFieldDefBuilder().withType(null).build()), true,
                FIELD_LIST_ATTR_NAME + "[0].type", "is required");
    }

    @Test
    public void multiChoiceNullAnswerList() {
        assertValidatorMessage(ImmutableList.of(makeValidFieldDefBuilder()
                .withMultiChoiceAnswerList((List<String>)null).build()), true,
                FIELD_LIST_ATTR_NAME + "[0].multiChoiceAnswerList",
                "must be specified for MULTI_CHOICE field " + FIELD_NAME);
    }

    @Test
    public void multiChoiceEmptyAnswerList() {
        assertValidatorMessage(ImmutableList.of(makeValidFieldDefBuilder()
                        .withMultiChoiceAnswerList(ImmutableList.of()).build()), true,
                FIELD_LIST_ATTR_NAME + "[0].multiChoiceAnswerList",
                "must be specified for MULTI_CHOICE field " + FIELD_NAME);
    }

    @Test
    public void multiChoiceInvalidAnswerList() {
        String answerChoice = "!invalid@choice%";
        assertValidatorMessage(ImmutableList.of(makeValidFieldDefBuilder().withMultiChoiceAnswerList(answerChoice)
                .build()), true, FIELD_LIST_ATTR_NAME + "[0].multiChoice[0]",
                String.format(UploadUtil.INVALID_ANSWER_CHOICE_ERROR_MESSAGE, answerChoice));
    }

    @Test
    public void stringWithUnboundedTextAndMaxLength() {
        assertValidatorMessage(ImmutableList.of(new UploadFieldDefinition.Builder().withName(FIELD_NAME)
                .withType(UploadFieldType.STRING).withMaxLength(42).withUnboundedText(true).build()), true,
                FIELD_LIST_ATTR_NAME + "[0].unboundedText", "cannot specify unboundedText=true with a maxLength");
    }

    @Test
    public void duplicateFieldName() {
        // make field defs
        List<UploadFieldDefinition> fieldDefList = new ArrayList<>();
        fieldDefList.add(new UploadFieldDefinition.Builder().withName("foo-field").withType(UploadFieldType.STRING)
                .build());
        fieldDefList.add(new UploadFieldDefinition.Builder().withName("foo-field").withType(UploadFieldType.INT)
                .build());
        fieldDefList.add(new UploadFieldDefinition.Builder().withName("bar").withType(UploadFieldType.MULTI_CHOICE)
                .withMultiChoiceAnswerList("bar", "other").withAllowOtherChoices(true).build());
        fieldDefList.add(new UploadFieldDefinition.Builder().withName("bar.bar").withType(UploadFieldType.STRING)
                .build());
        fieldDefList.add(new UploadFieldDefinition.Builder().withName("baz").withType(UploadFieldType.TIMESTAMP)
                .build());
        fieldDefList.add(new UploadFieldDefinition.Builder().withName("baz.timezone").withType(UploadFieldType.STRING)
                .build());

        // validate
        assertValidatorMessage(fieldDefList, true, FIELD_LIST_ATTR_NAME, "conflict in field names or sub-field names: "
                + "bar.bar, bar.other, baz.timezone, foo-field");
    }

    // Can't use TestUtils.assertValidatorMessage() because this is a special case validator.
    private static void assertValidatorMessage(List<UploadFieldDefinition> fieldDefList, boolean hasError,
            String fieldName, String errorMessage) {
        MapBindingResult errors = new MapBindingResult(new HashMap<>(), "UploadFieldDefinitionList");
        UploadFieldDefinitionListValidator.INSTANCE.validate(fieldDefList, errors, FIELD_LIST_ATTR_NAME);

        assertEquals(hasError, errors.hasErrors());
        if (hasError) {
            Map<String, List<String>> errorMap = Validate.convertErrorsToSimpleMap(errors);
            assertEquals(fieldName + " " + errorMessage, errorMap.get(fieldName).get(0));
        }
    }

    private static UploadFieldDefinition.Builder makeValidFieldDefBuilder() {
        return new UploadFieldDefinition.Builder().withName(FIELD_NAME).withType(UploadFieldType.MULTI_CHOICE)
                .withMultiChoiceAnswerList("foo", "bar", "baz");
    }
}
