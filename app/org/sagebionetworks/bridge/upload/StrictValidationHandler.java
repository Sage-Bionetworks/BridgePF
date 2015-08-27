package org.sagebionetworks.bridge.upload;

import javax.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Joiner;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordBuilder;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.services.UploadSchemaService;

/**
 * <p>
 * Upload validation handler that handles strict schema validation. Specifically, it checks to make sure the record
 * data contains all the fields marked as "required" are present. This guards against (a) apps sending "useless" data
 * that's missing key fields and (b) apps changing filenames and fieldnames causing Bridge to no longer see the
 * incoming data.
 * </p>
 * <p>
 * This handler won't make any changes to the UploadValidationContext, but it will throw an UploadValidationException
 * if the record data fails validation. Specifically, it will read data from
 * {@link org.sagebionetworks.bridge.upload.UploadValidationContext#getHealthDataRecordBuilder} and
 * {@link org.sagebionetworks.bridge.upload.UploadValidationContext#getAttachmentsByFieldName}.
 * </p>
 * <p>
 * Because legacy studies don't have the concept of "required fields", Upload Validation was made lenient, to be able
 * to still gather partial data. With strict validation, Bridge will still log warnings for all apps for reporting
 * purposes, and failing strict validation can be configured on a per-study basis. Note that all new studies should be
 * created with strict validation turned on.
 * </p>
 */
@Component
public class StrictValidationHandler implements UploadValidationHandler {
    private static final Logger logger = LoggerFactory.getLogger(StrictValidationHandler.class);

    private static final Joiner ERROR_MESSAGE_JOINER = Joiner.on("; ");

    private UploadSchemaService uploadSchemaService;

    /** Upload Schema Service, used to get the schema to validate against the upload. */
    @Autowired
    public final void setUploadSchemaService(UploadSchemaService uploadSchemaService) {
        this.uploadSchemaService = uploadSchemaService;
    }

    /** {@inheritDoc} */
    @Override
    public void handle(@Nonnull UploadValidationContext context) throws UploadValidationException {
        StudyIdentifier studyIdentifier = context.getStudy();

        // get fields from record builder
        HealthDataRecordBuilder recordBuilder = context.getHealthDataRecordBuilder();
        JsonNode recordDataNode = recordBuilder.getData();
        String schemaId = recordBuilder.getSchemaId();
        int schemaRev = recordBuilder.getSchemaRevision();

        // get attachment field names
        Set<String> attachmentFieldNameSet = context.getAttachmentsByFieldName().keySet();

        // get schema
        UploadSchema schema = uploadSchemaService.getUploadSchemaByIdAndRev(studyIdentifier, schemaId, schemaRev);
        List<UploadFieldDefinition> fieldDefList = schema.getFieldDefinitions();

        List<String> errorList = validateAllFields(fieldDefList, attachmentFieldNameSet, recordDataNode);

        handleErrors(context, errorList);
    }

    /**
     * Handles validation errors. Specifically, this logs a warning in the logs, writes the message to the upload
     * validation context, and (if shouldThrow is true) throws an UploadValidationException.
     *
     * @param context
     *         upload validation context
     * @param errorList
     *         list of errors, may be empty if there are no errors
     * @throws UploadValidationException
     *         representing the error with the specified message, if shouldThrow is true
     */
    private void handleErrors(@Nonnull UploadValidationContext context, @Nonnull List<String> errorList)
            throws UploadValidationException {
        if (errorList.isEmpty()) {
            // no errors, trivial return
            return;
        }

        // copy to upload validation context messages
        errorList.forEach(context::addMessage);

        // log warning
        String combinedErrorMessage = "Strict upload validation error in study " + context.getStudy().getIdentifier()
                + ", upload " + context.getUpload().getUploadId() + ": " + ERROR_MESSAGE_JOINER.join(errorList);
        logger.warn(combinedErrorMessage);

        // throw error, if configured to do so
        if (shouldThrow(context.getStudy())) {
            throw new UploadValidationException(combinedErrorMessage);
        }
    }

    /**
     * <p>
     * Returns whether strict validation should throw exceptions, based on study configs.
     * </p>
     * <p>
     * This is protected so that unit tests can mock this out. (Until we implement the actual study config lookup.)
     * </p>
     *
     * @param studyIdentifier
     *         study ID of the current data record we're validating
     * @return true if we should throw an exception, false otherwise
     */
    // TODO: implement this
    protected boolean shouldThrow(StudyIdentifier studyIdentifier) {
        return false;
    }

    /**
     * Given the schema, the attachments (all we need are names), and the JSON data nodes, we validate the data against
     * the schema.
     *
     * @param fieldDefList
     *         list of field definitions from the schema
     * @param attachmentFieldNameSet
     *         set of attachment field names that we have attachments for
     * @param recordDataNode
     *         JSON node of the parsed data to validate
     * @return list of error messages, empty if there are no errors
     */
    private static List<String> validateAllFields(@Nonnull List<UploadFieldDefinition> fieldDefList,
            @Nonnull Set<String> attachmentFieldNameSet, @Nonnull JsonNode recordDataNode) {
        // walk the field definitions and validate fields
        List<String> errorList = new ArrayList<>();
        for (UploadFieldDefinition oneFieldDef : fieldDefList) {
            String fieldName = oneFieldDef.getName();
            UploadFieldType fieldType = oneFieldDef.getType();
            boolean isRequired = oneFieldDef.isRequired();

            if (UploadFieldType.ATTACHMENT_TYPE_SET.contains(fieldType)) {
                // For attachment types, since they just get exported as raw files, we only need to check if it's
                // required and present. Specifically, if it's required and it's not present, then that's an error.
                if (isRequired && !attachmentFieldNameSet.contains(fieldName)) {
                    errorList.add("Required attachment field " + fieldName + " missing");
                }
            } else {
                JsonNode fieldValueNode = recordDataNode.get(fieldName);

                if (fieldValueNode != null && !fieldValueNode.isNull()) {
                    boolean isValid = validateJsonField(fieldType, fieldValueNode);
                    if (!isValid) {
                        errorList.add("Expected field " + fieldName + " to be " + fieldType.name() +
                                ", but was instead JSON type " + fieldValueNode.getNodeType().name());
                    }
                } else if (isRequired) {
                    errorList.add("Required field " + fieldName + " missing");
                }
            }
        }

        return errorList;
    }

    /**
     * Given the expected field type and the field value JSON node, this validates the we have the correct type,
     * returning true of the value is valid and false otherwise. Note that because attachment fields live in the
     * attachments map and not in JSON, this will always return false for attachment types.
     *
     * @param fieldType
     *         expected field type
     * @param fieldValueNode
     *         field value JSON node to validate
     * @return true if valid, false otherwise
     */
    private static boolean validateJsonField(@Nonnull UploadFieldType fieldType, @Nonnull JsonNode fieldValueNode) {
        switch (fieldType) {
            case BOOLEAN:
                return fieldValueNode.isBoolean();
            case CALENDAR_DATE:
                // We expect a string. Also, the string should be parseable by Joda LocalDate.
                if (!fieldValueNode.isTextual()) {
                    return false;
                }

                try {
                    // DateUtils calls through to Joda parseLocalDate(), which is documented as
                    // never returning null. So we don't need to null check here.
                    DateUtils.parseCalendarDate(fieldValueNode.textValue());
                    return true;
                } catch (RuntimeException ex) {
                    return false;
                }
            case FLOAT:
            case INT:
                // Research Kit doesn't distinguish between floats and ints, so neither should we. Just
                // accept any number type.
                return fieldValueNode.isNumber();
            case INLINE_JSON_BLOB:
                // JSON blobs are always JSON blobs. We don't need to do any special validation.
                return true;
            case STRING:
                return fieldValueNode.isTextual();
            case TIMESTAMP:
                // either it's a string in ISO format, or it's a long in epoch milliseconds
                if (fieldValueNode.isTextual()) {
                    DateTime dateTime = UploadUtil.parseIosTimestamp(fieldValueNode.textValue());
                    return dateTime != null;
                } else if (fieldValueNode.isIntegralNumber()) {
                    // any integral value can be converted to an epoch milliseconds, so this is good no
                    // matter what
                    return true;
                } else {
                    return false;
                }
            default:
                // This should never happen, but just in case we add a new field to UploadFieldType
                // but forget to upload this switch.
                return false;
        }
    }
}
