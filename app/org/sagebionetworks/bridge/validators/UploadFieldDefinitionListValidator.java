package org.sagebionetworks.bridge.validators;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.Errors;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;
import org.sagebionetworks.bridge.upload.UploadUtil;

/**
 * Validates a List of UploadFieldDefinitions. This doesn't use the Validator class, because (1) the typing for a List
 * of UploadFieldDefinitions is a little wonky and (2) we needed to pass in a little more context to get this to work.
 */
public class UploadFieldDefinitionListValidator {
    private static final char MULTI_CHOICE_FIELD_SEPARATOR = '.';
    private static final String OTHER_CHOICE_FIELD_SUFFIX = ".other";
    private static final String TIME_ZONE_FIELD_SUFFIX = ".timezone";

    /** Singleton instance of this validator. */
    public static final UploadFieldDefinitionListValidator INSTANCE = new UploadFieldDefinitionListValidator();

    /**
     * Validates a List of UploadFieldDefinitions.
     *
     * @param fieldDefList
     *         field definition list to validate
     * @param errors
     *         validation errors are written to this
     * @param attributeName
     *         the parent class's attribute name for the field definition list, as would be reported in errors
     */
    public void validate(List<UploadFieldDefinition> fieldDefList, Errors errors, String attributeName) {
        if (fieldDefList == null || fieldDefList.isEmpty()) {
            // Skip. Sometimes, the fieldDefList doesn't need to be present, like in
            // Study.uploadMetadataFieldDefinitions.
            return;
        }

        // Keep track of field names seen. This list may include duplicates, which we validate in a later step.
        List<String> fieldNameList = new ArrayList<>();

        for (int i = 0; i < fieldDefList.size(); i++) {
            UploadFieldDefinition fieldDef = fieldDefList.get(i);
            String fieldDefinitionKey = attributeName + "[" + i + "]";
            if (fieldDef == null) {
                errors.rejectValue(fieldDefinitionKey, "is required");
            } else {
                errors.pushNestedPath(fieldDefinitionKey);

                String fieldName = fieldDef.getName();
                if (StringUtils.isBlank(fieldName)) {
                    errors.rejectValue("name", "is required");
                } else {
                    fieldNameList.add(fieldName);

                    // Validate field name.
                    if (!UploadUtil.isValidSchemaFieldName(fieldName)) {
                        errors.rejectValue("name", String.format(UploadUtil.INVALID_FIELD_NAME_ERROR_MESSAGE,
                                fieldName));
                    }
                }

                UploadFieldType fieldType = fieldDef.getType();
                if (fieldType == null) {
                    errors.rejectValue("type", "is required");
                }

                if (fieldType == UploadFieldType.MULTI_CHOICE) {
                    List<String> multiChoiceAnswerList = fieldDef.getMultiChoiceAnswerList();
                    if (multiChoiceAnswerList == null || multiChoiceAnswerList.isEmpty()) {
                        errors.rejectValue("multiChoiceAnswerList", "must be specified for MULTI_CHOICE field "
                                + fieldName);
                    } else {
                        // Multi-Choice fields create extra "sub-field" columns, and we need to check for
                        // potential name collisions.

                        int numAnswers = multiChoiceAnswerList.size();
                        for (int j = 0; j < numAnswers; j++) {
                            String oneAnswer = multiChoiceAnswerList.get(j);
                            fieldNameList.add(fieldName + MULTI_CHOICE_FIELD_SEPARATOR + oneAnswer);

                            // Validate choice answer name.
                            if (!UploadUtil.isValidAnswerChoice(oneAnswer)) {
                                errors.rejectValue("multiChoice[" + j + "]", String.format(
                                        UploadUtil.INVALID_ANSWER_CHOICE_ERROR_MESSAGE, oneAnswer));
                            }
                        }
                    }

                    if (Boolean.TRUE.equals(fieldDef.getAllowOtherChoices())) {
                        // Similarly, there's an "other" field.
                        fieldNameList.add(fieldName + OTHER_CHOICE_FIELD_SUFFIX);
                    }
                } else if (fieldType == UploadFieldType.TIMESTAMP) {
                    // Timestamp fields also generate a second subfield for timezone. Need to check for name
                    // collisions here too.
                    fieldNameList.add(fieldName + TIME_ZONE_FIELD_SUFFIX);
                }

                if (fieldDef.isUnboundedText() != null && fieldDef.isUnboundedText() &&
                        fieldDef.getMaxLength() != null) {
                    errors.rejectValue("unboundedText", "cannot specify unboundedText=true with a maxLength");
                }

                errors.popNestedPath();
            }
        }

        // Check for duplicate field names. Dupe set is a tree set so our error messages are in a predictable
        // alphabetical order.
        Set<String> seenFieldNameSet = new HashSet<>();
        Set<String> dupeFieldNameSet = new TreeSet<>();
        for (String oneFieldName : fieldNameList) {
            if (seenFieldNameSet.contains(oneFieldName)) {
                dupeFieldNameSet.add(oneFieldName);
            } else {
                seenFieldNameSet.add(oneFieldName);
            }
        }

        if (!dupeFieldNameSet.isEmpty()) {
            errors.rejectValue(attributeName, "conflict in field names or sub-field names: " +
                    BridgeUtils.COMMA_SPACE_JOINER.join(dupeFieldNameSet));
        }
    }
}
