package org.sagebionetworks.bridge.upload;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Joiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordBuilder;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.services.UploadSchemaService;

/**
 * <p>
 * Upload validation handler that handles strict schema validation. Specifically, it checks to make sure the record
 * data contains all the fields marked as "required" are present. This guards against (a) apps sending "useless" data
 * that's missing key fields and (b) apps changing filenames and fieldnames causing Bridge to no longer see the
 * incoming data.
 * </p>
 * <p>
 * This handler also canonicalizes the values in {@link UploadValidationContext#getHealthDataRecordBuilder} in
 * {@link HealthDataRecordBuilder#getData()} and writes them back to the data map.
 * </p>
 * <p>
 * This handler won't make any other changes to the UploadValidationContext, but it will throw an UploadValidationException
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

    private StudyService studyService;
    private UploadSchemaService uploadSchemaService;

    /** Study service, used to fetch configuration for if strict validation is enabled for the given study. */
    @Autowired
    public final void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }

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

        List<String> errorList = validateAllFields(context.getAppVersion(), fieldDefList, attachmentFieldNameSet,
                recordDataNode);

        handleErrors(context, schemaId, schemaRev, errorList);
    }

    /**
     * Handles validation errors. Specifically, this logs a warning in the logs, writes the message to the upload
     * validation context, and (if shouldThrow is true) throws an UploadValidationException.
     *
     * @param context
     *         upload validation context
     * @param schemaId
     *         schema ID, used for logging
     * @param schemaRev
     *         schema revision, used for logging
     * @param errorList
     *         list of errors, may be empty if there are no errors
     * @throws UploadValidationException
     *         representing the error with the specified message, if shouldThrow is true
     */
    private void handleErrors(@Nonnull UploadValidationContext context, @Nonnull String schemaId, int schemaRev,
            @Nonnull List<String> errorList)
            throws UploadValidationException {
        if (errorList.isEmpty()) {
            // no errors, trivial return
            return;
        }

        // copy to upload validation context messages
        errorList.forEach(context::addMessage);

        // log warning
        String combinedErrorMessage = "Strict upload validation error in study " + context.getStudy().getIdentifier()
                + ", schema " + schemaId + "-v" + schemaRev + ", upload " + context.getUpload().getUploadId() + ": " +
                ERROR_MESSAGE_JOINER.join(errorList);
        logger.warn(combinedErrorMessage);

        // throw error, if configured to do so
        if (shouldThrow(context.getStudy())) {
            throw new UploadValidationException(combinedErrorMessage);
        }
    }

    /**
     * Returns whether strict validation should throw exceptions, based on study configs.
     *
     * @param studyIdentifier
     *         study ID of the current data record we're validating
     * @return true if we should throw an exception, false otherwise
     */
    private boolean shouldThrow(StudyIdentifier studyIdentifier) {
        Study study = studyService.getStudy(studyIdentifier);
        return study.isStrictUploadValidationEnabled();
    }

    /**
     * Given the schema, the attachments (all we need are names), and the JSON data nodes, we validate the data against
     * the schema.
     *
     * @param appVersion
     *         app version of the upload, null if the app version is not present
     * @param fieldDefList
     *         list of field definitions from the schema
     * @param attachmentFieldNameSet
     *         set of attachment field names that we have attachments for
     * @param recordDataNode
     *         JSON node of the parsed data to validate
     * @return list of error messages, empty if there are no errors
     */
    private static List<String> validateAllFields(@Nullable Integer appVersion,
            @Nonnull List<UploadFieldDefinition> fieldDefList, @Nonnull Set<String> attachmentFieldNameSet,
            @Nonnull JsonNode recordDataNode) {
        // walk the field definitions and validate fields
        List<String> errorList = new ArrayList<>();
        for (UploadFieldDefinition oneFieldDef : fieldDefList) {
            String fieldName = oneFieldDef.getName();
            UploadFieldType fieldType = oneFieldDef.getType();
            boolean isRequired = computeIsRequired(appVersion, oneFieldDef);

            if (UploadFieldType.ATTACHMENT_TYPE_SET.contains(fieldType)) {
                // For attachment types, since they just get exported as raw files, we only need to check if it's
                // required and present. Specifically, if it's required and it's not present, then that's an error.
                if (isRequired && !attachmentFieldNameSet.contains(fieldName)) {
                    errorList.add("Required attachment field " + fieldName + " missing");
                }
            } else {
                JsonNode fieldValueNode = recordDataNode.get(fieldName);

                if (fieldValueNode != null && !fieldValueNode.isNull()) {
                    // Canonicalize the field.
                    CanonicalizationResult canonicalizationResult = UploadUtil.canonicalize(fieldValueNode, fieldType);
                    if (canonicalizationResult.isValid()) {
                        JsonNode canonicalizedValueNode = canonicalizationResult.getCanonicalizedValueNode();

                        // Special case: MULTI_CHOICE value validation
                        if (fieldType == UploadFieldType.MULTI_CHOICE) {
                            //noinspection ConstantConditions
                            Set<String> validAnswerSet = new HashSet<>(oneFieldDef.getMultiChoiceAnswerList());
                            int numAnswers = canonicalizedValueNode.size();
                            for (int i = 0; i < numAnswers; i++) {
                                String answer = canonicalizedValueNode.get(i).textValue();
                                if (!validAnswerSet.contains(answer)) {
                                    errorList.add("Multi-Choice field " + fieldName + " contains invalid answer " +
                                            answer);
                                }
                            }
                        }

                        // Write the canonicalization back into the field data map.
                        ((ObjectNode)recordDataNode).set(fieldName, canonicalizedValueNode);
                    } else {
                        errorList.add("Canonicalization failed for field " + fieldName + ": " +
                                canonicalizationResult.getErrorMessage());
                    }
                } else if (isRequired) {
                    errorList.add("Required field " + fieldName + " missing");
                }
            }
        }

        return errorList;
    }

    /**
     * <p>
     * Given the upload's app version and the parameters in the field definition, compute whether we should treat the
     * field value as required. In particular, we look at the field's required, minAppVersion, and maxAppVersion
     * attributes.
     * </p>
     * <p>
     * Package-scoped to facilitate unit tests.
     * </p>
     *
     * @param appVersion
     *         app version from the upload
     * @param fieldDef
     *         field definition to validate
     * @return true if the field is required, false otherwise
     */
    static boolean computeIsRequired(@Nullable Integer appVersion, @Nonnull UploadFieldDefinition fieldDef) {
        if (!fieldDef.isRequired()) {
            // If the field definition says it's not required, then it's always not required.
            return false;
        }

        if (appVersion == null) {
            // If the app version isn't specified, we can't apply min/maxAppVersion logic. Assume it is required.
            return true;
        }

        // If the field has min- and/or maxAppVersions set, we check the appVersion against these.
        Integer minAppVersion = fieldDef.getMinAppVersion();
        Integer maxAppVersion = fieldDef.getMaxAppVersion();
        return (minAppVersion == null || minAppVersion <= appVersion) &&
                (maxAppVersion == null || appVersion <= maxAppVersion);
    }
}
