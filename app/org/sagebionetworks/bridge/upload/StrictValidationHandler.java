package org.sagebionetworks.bridge.upload;

import javax.annotation.Nonnull;

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

import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.models.upload.UploadValidationStrictness;
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
 * This handler also canonicalizes the values in {@link UploadValidationContext#getHealthDataRecord} in
 * {@link HealthDataRecord#getData()} and writes them back to the data map.
 * </p>
 * <p>
 * This handler won't make any other changes to the UploadValidationContext, but it will throw an UploadValidationException
 * if the record data fails validation. Specifically, it will read data from
 * {@link org.sagebionetworks.bridge.upload.UploadValidationContext#getHealthDataRecord}.
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
        HealthDataRecord record = context.getHealthDataRecord();
        JsonNode recordDataNode = record.getData();
        String schemaId = record.getSchemaId();
        int schemaRev = record.getSchemaRevision();

        // get schema
        UploadSchema schema = uploadSchemaService.getUploadSchemaByIdAndRev(studyIdentifier, schemaId, schemaRev);
        List<UploadFieldDefinition> fieldDefList = schema.getFieldDefinitions();

        List<String> errorList = validateAllFields(fieldDefList, recordDataNode);

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
        String combinedErrorMessage = ERROR_MESSAGE_JOINER.join(errorList);
        String loggedErrorMessage = "Strict upload validation error in study " + context.getStudy().getIdentifier()
                + ", schema " + schemaId + "-v" + schemaRev + ", upload " + context.getUploadId() + ": " +
                combinedErrorMessage;
        logger.warn(loggedErrorMessage);

        // Further action depends on validation strictness.
        UploadValidationStrictness uploadValidationStrictness = getUploadValidationStrictnessForStudy(context
                .getStudy());
        switch (uploadValidationStrictness) {
            case WARNING:
                // We already warned. Do nothing.
                break;
            case REPORT:
                // Write to record.validationErrors.
                context.getHealthDataRecord().setValidationErrors(combinedErrorMessage);
                break;
            case STRICT:
                // Strict means throw.
                throw new UploadValidationException(loggedErrorMessage);
        }
    }

    /**
     * Returns what level of validation strictness we should use, based on study configs. Package-scoped to facilitate
     * unit tests.
     */
    UploadValidationStrictness getUploadValidationStrictnessForStudy(StudyIdentifier studyId) {
        Study study = studyService.getStudy(studyId);

        // First check UploadValidationStrictness.
        UploadValidationStrictness uploadValidationStrictness = study.getUploadValidationStrictness();
        if (uploadValidationStrictness != null) {
            return uploadValidationStrictness;
        }

        // Next, try isStrictValidationEnabled. True means Strict. False means Warning.
        boolean strictValidationEnabled = study.isStrictUploadValidationEnabled();
        return strictValidationEnabled ? UploadValidationStrictness.STRICT : UploadValidationStrictness.WARNING;
    }

    /**
     * Given the schema, the attachments (all we need are names), and the JSON data nodes, we validate the data against
     * the schema.
     */
    private static List<String> validateAllFields(List<UploadFieldDefinition> fieldDefList, JsonNode recordDataNode) {
        // walk the field definitions and validate fields
        List<String> errorList = new ArrayList<>();
        for (UploadFieldDefinition oneFieldDef : fieldDefList) {
            String fieldName = oneFieldDef.getName();
            UploadFieldType fieldType = oneFieldDef.getType();
            boolean isRequired = oneFieldDef.isRequired();

            if (UploadFieldType.ATTACHMENT_TYPE_SET.contains(fieldType)) {
                // For attachment types, since they just get exported as raw files, we only need to check if it's
                // required and present. Specifically, if it's required and it's not present, then that's an error.
                if (isRequired && !recordDataNode.hasNonNull(fieldName)) {
                    errorList.add("Required attachment field " + fieldName + " missing");
                }
            } else {
                JsonNode fieldValueNode = recordDataNode.get(fieldName);

                if (fieldValueNode != null && !fieldValueNode.isNull()) {
                    // Canonicalize the field.
                    CanonicalizationResult canonicalizationResult = UploadUtil.canonicalize(fieldValueNode, fieldType);
                    if (canonicalizationResult.isValid()) {
                        JsonNode canonicalizedValueNode = canonicalizationResult.getCanonicalizedValueNode();

                        // Special case: MULTI_CHOICE value validation (unless it allows other choices)
                        if (fieldType == UploadFieldType.MULTI_CHOICE &&
                                !Boolean.TRUE.equals(oneFieldDef.getAllowOtherChoices())) {
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
}
