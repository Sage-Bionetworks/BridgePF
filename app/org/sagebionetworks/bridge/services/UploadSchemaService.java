package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.UploadSchemaDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.surveys.Constraints;
import org.sagebionetworks.bridge.models.surveys.DataType;
import org.sagebionetworks.bridge.models.surveys.MultiValueConstraints;
import org.sagebionetworks.bridge.models.surveys.NumericalConstraints;
import org.sagebionetworks.bridge.models.surveys.StringConstraints;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestionOption;
import org.sagebionetworks.bridge.models.surveys.Unit;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.models.upload.UploadSchemaType;
import org.sagebionetworks.bridge.upload.UploadUtil;
import org.sagebionetworks.bridge.validators.UploadSchemaValidator;
import org.sagebionetworks.bridge.validators.Validate;

/**
 * Service handler for upload schema APIs. This is called by researchers to create, read, and update upload schemas.
 */
@Component
public class UploadSchemaService {
    // Mapping from Survey types to Schema types. We have two separate typing systems because the Survey typing system
    // is slightly incompatible with Schemas, most notably around multi-choice and single-choice questions.
    private static final Map<DataType, UploadFieldType> SURVEY_TO_SCHEMA_TYPE =
            ImmutableMap.<DataType, UploadFieldType>builder()
                    .put(DataType.DURATION, UploadFieldType.INT)
                    .put(DataType.STRING, UploadFieldType.STRING)
                    .put(DataType.INTEGER, UploadFieldType.INT)
                    .put(DataType.DECIMAL, UploadFieldType.FLOAT)
                    .put(DataType.BOOLEAN, UploadFieldType.BOOLEAN)
                    .put(DataType.DATE, UploadFieldType.CALENDAR_DATE)
                    .put(DataType.TIME, UploadFieldType.TIME_V2)
                    .put(DataType.DATETIME, UploadFieldType.TIMESTAMP)
                    .build();

    // Default string length for single_choice field. This is needed to prevent single_choice questions from changing
    // in length a lot. Package-scoped to facilitate unit tests.
    static final int SINGLE_CHOICE_DEFAULT_LENGTH = 100;
    private static int singleChoiceDefaultLength = SINGLE_CHOICE_DEFAULT_LENGTH;

    /** Overrides the single_choice default length. Used for unit tests. */
    static void setSingleChoiceDefaultLength(int singleChoiceDefaultLength) {
        UploadSchemaService.singleChoiceDefaultLength = singleChoiceDefaultLength;
    }

    /** Resets the singleChoiceDefaultLength and removes the override. Used for unit tests. */
    static void resetSingleChoiceDefaultLength() {
        UploadSchemaService.singleChoiceDefaultLength = SINGLE_CHOICE_DEFAULT_LENGTH;
    }

    private UploadSchemaDao uploadSchemaDao;

    /** DAO for upload schemas. This is configured by Spring. */
    @Autowired
    public final void setUploadSchemaDao(UploadSchemaDao uploadSchemaDao) {
        this.uploadSchemaDao = uploadSchemaDao;
    }

    /**
     * Creates a schema revision using the new V4 semantics. The schema ID and revision will be taken from the
     * UploadSchema object. If the revision isn't specified, we'll get the latest schema rev for the schema ID and use
     * that rev + 1.
     */
    public UploadSchema createSchemaRevisionV4(StudyIdentifier studyId, UploadSchema schema) {
        // Controller guarantees valid studyId and non-null uploadSchema
        checkNotNull(studyId, "studyId must be non-null");
        checkNotNull(schema, "uploadSchema must be non-null");

        // Schema ID is validated by getCurrentSchemaRevision()

        // Set revision if needed. 0 represents an unset schema rev.
        int oldRev = getCurrentSchemaRevision(studyId, schema.getSchemaId());
        if (schema.getRevision() == 0) {
            schema.setRevision(oldRev + 1);
        }

        // Set study. This enforces that you can't create schemas outside of your study.
        schema.setStudyId(studyId.getIdentifier());

        // validate schema
        Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, schema);

        // call through to DAO
        return uploadSchemaDao.createSchemaRevision(schema);
    }

    /**
     * <p>
     * Service handler for creating and updating upload schemas. This method creates an upload schema, using the study
     * ID and schema ID of the specified schema, or updates an existing one if it already exists.
     * </p>
     * <p>
     * This uses the old V3 semantics for creating and updating schemas. To create a schema, do not set the revision
     * (or set it to the default value of 0). To update a schema, set revision equal to the latest revision of the
     * schema. Creating a schema that already exists, or updating a schema that's not the latest revision will result
     * in a ConcurrentModificationException.
     * </p>
     */
    public UploadSchema createOrUpdateUploadSchema(StudyIdentifier studyId, UploadSchema schema) {
        // Controller guarantees valid studyId and non-null uploadSchema
        checkNotNull(studyId, "studyId must be non-null");
        checkNotNull(schema, "uploadSchema must be non-null");

        // Schema ID is validated by getCurrentSchemaRevision()

        // Request should match old rev. If it does, auto-increment and write. Otherwise, throw.
        int oldRev = getCurrentSchemaRevision(studyId, schema.getSchemaId());
        if (oldRev != schema.getRevision()) {
            throw new ConcurrentModificationException(schema);
        }
        schema.setRevision(oldRev + 1);

        // Set study. This enforces that you can't create schemas outside of your study.
        schema.setStudyId(studyId.getIdentifier());

        // validate schema
        Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, schema);

        // call through to DAO
        return uploadSchemaDao.createSchemaRevision(schema);
    }

    /**
     * Private helper function to get the current schema revision of the specified schema ID. Returns 0 if the schema
     * doesn't exist. This is generally useful for validating the proper revision number for making updates.
     */
    private int getCurrentSchemaRevision(StudyIdentifier studyId, String schemaId) {
        UploadSchema oldSchema = getUploadSchemaNoThrow(studyId, schemaId);
        if (oldSchema != null) {
            return oldSchema.getRevision();
        } else {
            return 0;
        }
    }

    /**
     * <p>
     * Creates an upload schema from a survey. This is generally called when a survey is published, to
     * create the corresponding upload schema, so that health data records can be created from survey responses.
     * This method will also persist the schema to the backing store.
     * <p>
     * If newSchemaRev is true, this method will always create a new schema revision. If false, it will attempt to
     * modify the existing schema revision. However, if the schema revisions are not compatible, it will fall back to
     * creating a new schema revision.
     * </p>
     */
    public UploadSchema createUploadSchemaFromSurvey(StudyIdentifier studyId, Survey survey, boolean newSchemaRev) {
        // https://sagebionetworks.jira.com/browse/BRIDGE-1698 - If the existing Schema ID points to a different survey
        // or a non-survey, this is an error. Having multiple surveys point to the same schema ID causes really bad
        // things to happen, and we need to prevent it.
        String schemaId = survey.getIdentifier();
        UploadSchema oldSchema = getUploadSchemaNoThrow(studyId, schemaId);
        if (oldSchema != null) {
            if (oldSchema.getSchemaType() != UploadSchemaType.IOS_SURVEY ||
                    !Objects.equals(oldSchema.getSurveyGuid(), survey.getGuid())) {
                throw new BadRequestException("Survey with identifier " + schemaId +
                        " conflicts with schema with the same ID. Please use a different survey identifier.");
            }
        }

        // Get survey questions.
        List<SurveyQuestion> surveyQuestionList = survey.getUnmodifiableQuestionList();
        if (surveyQuestionList.isEmpty()) {
            throw new BadRequestException("Can't create a schema from a survey with no questions");
        }

        // create upload field definitions from survey questions
        List<UploadFieldDefinition> newFieldDefList = new ArrayList<>();
        for (SurveyQuestion oneQuestion : surveyQuestionList) {
            addFieldDefsForSurveyQuestion(newFieldDefList, oneQuestion);
        }

        // If we want to use the existing schema rev, and one exists. Note that we've already validated that it is for
        // the same survey.
        if (!newSchemaRev && oldSchema != null) {
            List<UploadFieldDefinition> oldFieldDefList = oldSchema.getFieldDefinitions();

            // Optimization: If the new schema and the old schema have the same fields, return the old schema
            // instead of creating a new one.
            //
            // Dump the fieldDefLists into a set, because if we have the same fields in a different order, the
            // schemas are compatible, and we should use the old schema too.
            Set<UploadFieldDefinition> oldFieldDefSet = ImmutableSet.copyOf(oldFieldDefList);
            Set<UploadFieldDefinition> newFieldDefSet = ImmutableSet.copyOf(newFieldDefList);
            if (oldFieldDefSet.equals(newFieldDefSet)) {
                return oldSchema;
            }

            // Otherwise, merge the old and new field defs to create a new schema.
            MergeSurveySchemaResult mergeResult = mergeSurveySchemaFields(oldFieldDefList, newFieldDefList);
            if (mergeResult.isSuccess()) {
                // We successfully merged the field def lists. We can successfully update the existing schema
                // in-place.
                addSurveySchemaMetadata(oldSchema, survey, mergeResult.getFieldDefinitionList());
                return updateSchemaRevisionV4(studyId, schemaId, oldSchema.getRevision(), oldSchema);
            }
        }

        // We were unable to reconcile this with the existing schema. Create a new schema. (Create API will
        // automatically bump the rev number if an old schema revision exists.)
        UploadSchema schemaToCreate = UploadSchema.create();
        addSurveySchemaMetadata(schemaToCreate, survey, newFieldDefList);
        return createSchemaRevisionV4(studyId, schemaToCreate);
    }

    // Helper method to convert a survey question into one or more schema field defs. As some survey questions can
    // generate more than one field (such as unit fields), callers should pass in a field def list, and this method
    // will append created field defs to that list.
    private static void addFieldDefsForSurveyQuestion(List<UploadFieldDefinition> fieldDefList,
            SurveyQuestion question) {
        // These preconditions should never happen, but we have a Preconditions check here just in case.
        checkNotNull(question);

        Constraints constraints = question.getConstraints();
        checkNotNull(constraints);

        DataType surveyQuestionType = constraints.getDataType();
        checkNotNull(surveyQuestionType);

        // Init field def builder with basic fields. Note that all survey questions are skippable, so mark the field as
        // optional (not required).
        String fieldName = question.getIdentifier();
        UploadFieldDefinition.Builder fieldDefBuilder = new UploadFieldDefinition.Builder().withName(fieldName)
                .withRequired(false);

        UploadFieldType uploadFieldType;
        if (constraints instanceof MultiValueConstraints) {
            MultiValueConstraints multiValueConstraints = (MultiValueConstraints) constraints;
            if (multiValueConstraints.getAllowMultiple()) {
                uploadFieldType = UploadFieldType.MULTI_CHOICE;
                fieldDefBuilder.withAllowOtherChoices(multiValueConstraints.getAllowOther());

                // convert the survey answer option list to a list of possible multi-choice answers
                List<String> fieldAnswerList = new ArrayList<>();
                //noinspection Convert2streamapi
                for (SurveyQuestionOption oneSurveyOption : multiValueConstraints.getEnumeration()) {
                    fieldAnswerList.add(oneSurveyOption.getValue());
                }
                fieldDefBuilder.withMultiChoiceAnswerList(fieldAnswerList);
            } else {
                uploadFieldType = UploadFieldType.SINGLE_CHOICE;

                // Unfortunately, survey questions don't know their own length. Fortunately, we can determine this
                // by iterating over all answers.
                int maxLength = 0;
                //noinspection Convert2streamapi
                for (SurveyQuestionOption oneSurveyOption : multiValueConstraints.getEnumeration()) {
                    maxLength = Math.max(maxLength, oneSurveyOption.getValue().length());
                }

                if (maxLength <= singleChoiceDefaultLength) {
                    // If you update the single_choice field with longer answers, this changes the max length and
                    // breaks the schema. As such, we'll need to "pad" the single_choice field to the default length
                    // (100), so that Synapse tables don't need to be recreated.
                    fieldDefBuilder.withMaxLength(singleChoiceDefaultLength);
                } else {
                    // If the choices are very long, we should just use an unbounded string. This unfortunately is not
                    // searchable in Synapse, but it allows for stable survey schema revisions.
                    fieldDefBuilder.withUnboundedText(true);
                }
            }
        } else {
            // Get upload field type from the map.
            uploadFieldType = SURVEY_TO_SCHEMA_TYPE.get(surveyQuestionType);
            if (uploadFieldType == null) {
                throw new BridgeServiceException("Unexpected survey question type: " + surveyQuestionType);
            }

            // Type-specific parameters.
            if (constraints instanceof StringConstraints) {
                Integer maxLength = ((StringConstraints) constraints).getMaxLength();
                if (maxLength != null) {
                    fieldDefBuilder.withMaxLength(maxLength);
                } else {
                    // No max length specified. Assume this can be unbounded.
                    fieldDefBuilder.withUnboundedText(true);
                }
            }
        }

        fieldDefBuilder.withType(uploadFieldType);
        fieldDefList.add(fieldDefBuilder.build());

        // NumericalConstraints (integer, decimal, duration) have units. We want to write the unit into Synapse in case
        // (a) the survey question changes the units or (b) we add support for app-specified units.
        if (constraints instanceof NumericalConstraints) {
            UploadFieldDefinition unitFieldDef = new UploadFieldDefinition.Builder()
                    .withName(fieldName + UploadUtil.UNIT_FIELD_SUFFIX).withType(UploadFieldType.STRING)
                    .withRequired(false).withMaxLength(Unit.MAX_STRING_LENGTH).build();
            fieldDefList.add(unitFieldDef);
        }
    }

    // Helper method for merging the field def lists for survey schemas. The return value is a struct that contains the
    // merged list (if successful) and a flag indicating if the merge was successful.
    // Package-scoped for unit tests.
    static MergeSurveySchemaResult mergeSurveySchemaFields(List<UploadFieldDefinition> oldFieldDefList,
            List<UploadFieldDefinition> newFieldDefList) {
        // This method takes in lists because order matters (for creating Synapse columns). However, the field defs
        // might have been re-ordered, so we need to use a map to look up the old field defs.
        Map<String, UploadFieldDefinition> oldFieldDefMap = Maps.uniqueIndex(oldFieldDefList,
                UploadFieldDefinition::getName);

        List<UploadFieldDefinition> mergedFieldDefList = new ArrayList<>();
        Set<String> newFieldNameSet = new HashSet<>();
        boolean success = true;
        for (UploadFieldDefinition oneNewFieldDef : newFieldDefList) {
            // Keep track of all the field names in the new field def list, so we can determine which fields from the
            // old list need to get merged back in.
            String oneNewFieldName = oneNewFieldDef.getName();
            newFieldNameSet.add(oneNewFieldName);

            UploadFieldDefinition oneOldFieldDef = oldFieldDefMap.get(oneNewFieldName);
            if (oneOldFieldDef == null || UploadUtil.isCompatibleFieldDef(oneOldFieldDef, oneNewFieldDef)) {
                // Either the field is new, or it's compatible with the old field. Either way, we can add it straight
                // accross to the mergedFieldDefList.
                mergedFieldDefList.add(oneNewFieldDef);
            } else if (oneOldFieldDef.getType() != oneNewFieldDef.getType()) {
                // Field types are different. They aren't compatible, and we can't make them compatible. Mark the merge
                // as failed and short-cut all the way back out.
                success = false;
                break;
            } else {
                // There are some common use cases where can "massage" the new field def to be compatible with the old.
                UploadFieldDefinition.Builder modifiedFieldDefBuilder = new UploadFieldDefinition.Builder()
                        .copyOf(oneNewFieldDef);

                // If the old field allowed other choices, make the new field also allow other choices.
                Boolean oldAllowOther = oneOldFieldDef.getAllowOtherChoices();
                if (oldAllowOther != null && oldAllowOther) {
                    modifiedFieldDefBuilder.withAllowOtherChoices(true);
                }

                // If the old max length is longer, use the old max length.
                Integer oldMaxLength = oneOldFieldDef.getMaxLength();
                Integer newMaxLength = oneNewFieldDef.getMaxLength();
                if (oldMaxLength != null && newMaxLength != null && oldMaxLength > newMaxLength) {
                    modifiedFieldDefBuilder.withMaxLength(oldMaxLength);
                }

                // Similarly, if we deleted answers from the multi-choice answer list, we want to merge those answers
                // back in, so we can retain the column(s) in the Synapse tables.
                List<String> oldAnswerList = oneOldFieldDef.getMultiChoiceAnswerList();
                List<String> newAnswerList = oneNewFieldDef.getMultiChoiceAnswerList();
                if (!Objects.equals(oldAnswerList, newAnswerList)) {
                    List<String> mergedAnswerList = mergeMultiChoiceAnswerLists(oldAnswerList, newAnswerList);
                    modifiedFieldDefBuilder.withMultiChoiceAnswerList(mergedAnswerList);
                }

                // If old field is unbounded, then new field is unbounded.
                Boolean oldIsUnbounded = oneOldFieldDef.isUnboundedText();
                if (oldIsUnbounded != null && oldIsUnbounded) {
                    modifiedFieldDefBuilder.withUnboundedText(true);

                    // Clear maxLength, because you can't have both unboundedText and maxLength.
                    modifiedFieldDefBuilder.withMaxLength(null);
                }

                // One last check for compatibility.
                UploadFieldDefinition modifiedFieldDef = modifiedFieldDefBuilder.build();
                boolean isCompatible = UploadUtil.isCompatibleFieldDef(oneOldFieldDef, modifiedFieldDef);
                if (isCompatible) {
                    mergedFieldDefList.add(modifiedFieldDef);
                } else {
                    // Failed to make these field defs compatible. Mark as unsuccessful and break out of the loop.
                    success = false;
                    break;
                }
            }
        }

        if (!success) {
            // We had incompatible fields that couldn't be made compatible. Return a result with just the new field
            // defs and the flag marking the merge as unsuccessful.
            return new MergeSurveySchemaResult(newFieldDefList, false);
        }

        // Some fields from the old list don't show up in the new list. We need to merge those back in. Append them to
        // the end of the merge list in the same order that they came in.
        //noinspection Convert2streamapi
        for (UploadFieldDefinition oneOldFieldDef : oldFieldDefList) {
            if (!newFieldNameSet.contains(oneOldFieldDef.getName())) {
                mergedFieldDefList.add(oneOldFieldDef);
            }
        }

        return new MergeSurveySchemaResult(mergedFieldDefList, true);
    }

    // Helper method to merge two multi-choice answer lists. Since answer lists can be re-ordered, our merging strategy
    // is to copy the new answers to a new list, then append answers from the old list (that don't appear in the new)
    // to the new list, in the original order.
    //
    // For example, if old = (foo, bar, baz, qux), and new = (bar, foo, qwerty, asdf), then merged = (bar, foo, qwerty,
    // asdf, baz, qux)
    private static List<String> mergeMultiChoiceAnswerLists(List<String> oldAnswerList, List<String> newAnswerList) {
        // Since both lists are unsorted, and since order matters (or could potentially matter), a traditional "merge"
        // operation won't work. Instead our approach will be to take the values in old that don't appear in new and
        // append them (in their original relative order) to new.

        // To determine the values in old that don't appear in new, convert new into a set, loop through old and check
        // against the set.

        List<String> mergedAnswerList = new ArrayList<>();
        Set<String> newAnswerSet = new HashSet<>();
        if (newAnswerList != null) {
            mergedAnswerList.addAll(newAnswerList);
            newAnswerSet.addAll(newAnswerList);
        }

        if (oldAnswerList != null) {
            //noinspection Convert2streamapi
            for (String oneOldAnswer : oldAnswerList) {
                if (!newAnswerSet.contains(oneOldAnswer)) {
                    // This answer was removed in the new answer list. Add it back to the merged list.
                    mergedAnswerList.add(oneOldAnswer);
                }
            }
        }

        return mergedAnswerList;
    }

    // Helper struct to return information about merging survey schema fields. This is package-scoped to facilitate
    // unit tests.
    static class MergeSurveySchemaResult {
        private final List<UploadFieldDefinition> fieldDefList;
        private final boolean success;

        // trivial constructor
        public MergeSurveySchemaResult(List<UploadFieldDefinition> fieldDefList, boolean success) {
            this.fieldDefList = fieldDefList;
            this.success = success;
        }

        // The merged field def list. If the merge was not successful, this contains the new field def list.
        public List<UploadFieldDefinition> getFieldDefinitionList() {
            return fieldDefList;
        }

        // True if the merge was successful. False otherwise.
        public boolean isSuccess() {
            return success;
        }
    }

    // Helper method to add survey fields to schemas. This is useful so we have the same attributes for newly created
    // schemas, as well as setting them into updated schemas.
    private static void addSurveySchemaMetadata(UploadSchema schema, Survey survey,
            List<UploadFieldDefinition> fieldDefList) {
        // No need to set rev or version unless updating an existing rev. Study is always taken care of by the APIs.
        schema.setFieldDefinitions(fieldDefList);
        schema.setName(survey.getName());
        schema.setSchemaId(survey.getIdentifier());
        schema.setSchemaType(UploadSchemaType.IOS_SURVEY);
        schema.setSurveyGuid(survey.getGuid());
        schema.setSurveyCreatedOn(survey.getCreatedOn());
    }

    /**
     * Service handler for deleting all revisions of the upload schema with the specified study and schema ID. If there
     * are no schemas with this schema ID, this API throws an EntityNotFoundException.
     */
    public void deleteUploadSchemaById(StudyIdentifier studyId, String schemaId) {
        // Schema ID is validated by getUploadSchemaAllRevisions()

        List<UploadSchema> schemaList = getUploadSchemaAllRevisions(studyId, schemaId);
        uploadSchemaDao.deleteUploadSchemas(schemaList);
    }

    /**
     * Service handler for deleting an upload schema with the specified study, schema ID, and revision. If the schema
     * doesn't exist, this API throws an EntityNotFoundException.
     */
    public void deleteUploadSchemaByIdAndRevision(StudyIdentifier studyId, String schemaId, int rev) {
        // Schema ID and rev are validated by getUploadSchemaByIdAndRev()

        UploadSchema schema = getUploadSchemaByIdAndRev(studyId, schemaId, rev);
        uploadSchemaDao.deleteUploadSchemas(ImmutableList.of(schema));
    }

    /** Returns all revisions of all schemas. */
    public List<UploadSchema> getAllUploadSchemasAllRevisions(StudyIdentifier studyId) {
        return uploadSchemaDao.getAllUploadSchemasAllRevisions(studyId);
    }

    /** Service handler for fetching the most recent revision of all upload schemas in a study. */
    public List<UploadSchema> getUploadSchemasForStudy(StudyIdentifier studyId) {
        // Get all schemas. No simple query for just latest schemas.
        List<UploadSchema> allSchemasAllRevisions = getAllUploadSchemasAllRevisions(studyId);

        // Iterate schemas and pick out latest for each schema ID.
        // Find the most recent version of each schema with a unique schemaId
        Map<String,UploadSchema> schemaMap = new HashMap<>();
        for (UploadSchema schema : allSchemasAllRevisions) {
            UploadSchema existing = schemaMap.get(schema.getSchemaId());
            if (existing == null || schema.getRevision() > existing.getRevision()) {
                schemaMap.put(schema.getSchemaId(), schema);
            }
        }
        // Do we care if it's sorted? What would it be sorted by?
        return ImmutableList.copyOf(schemaMap.values());
    }

    /**
     * Service handler for fetching upload schemas. This method fetches an upload schema for the specified study and
     * schema ID. If there is more than one revision of the schema, this fetches the latest revision. If the schema
     * doesn't exist, this handler throws an InvalidEntityException.
     */
    public UploadSchema getUploadSchema(StudyIdentifier studyId, String schemaId) {
        UploadSchema schema = getUploadSchemaNoThrow(studyId, schemaId);
        if (schema == null) {
            throw new EntityNotFoundException(UploadSchema.class);
        }
        return schema;
    }

    /**
     * Private helper method to get the latest version of an upload schema, but doesn't throw if the schema does not
     * exist. Note that it still validates the user inputs (schemaId) and will throw a BadRequestException.
     */
    private UploadSchema getUploadSchemaNoThrow(StudyIdentifier studyId, String schemaId) {
        if (StringUtils.isBlank(schemaId)) {
            throw new BadRequestException("Schema ID must be specified");
        }
        return uploadSchemaDao.getUploadSchemaLatestRevisionById(studyId, schemaId);
    }

    /**
     * Service handler for fetching upload schemas. This method fetches all revisions of an an upload schema for
     * the specified study and schema ID. If the schema doesn't exist, this handler throws an EntityNotFoundException.
     */
    public List<UploadSchema> getUploadSchemaAllRevisions(StudyIdentifier studyId, String schemaId) {
        if (StringUtils.isBlank(schemaId)) {
            throw new BadRequestException("Schema ID must be specified");
        }

        List<UploadSchema> schemaList = uploadSchemaDao.getUploadSchemaAllRevisionsById(studyId, schemaId);
        if (schemaList.isEmpty()) {
            throw new EntityNotFoundException(UploadSchema.class);
        }
        return schemaList;
    }

    /**
     * Fetches the upload schema for the specified study, schema ID, and revision. If no schema is found, this API
     * throws an EntityNotFoundException
     */
    public UploadSchema getUploadSchemaByIdAndRev(StudyIdentifier studyId, String schemaId, int revision) {
        if (StringUtils.isBlank(schemaId)) {
            throw new BadRequestException("Schema ID must be specified");
        }
        if (revision <= 0) {
            throw new BadRequestException("Revision must be specified and positive");
        }

        UploadSchema schema = uploadSchemaDao.getUploadSchemaByIdAndRevision(studyId, schemaId, revision);
        if (schema == null) {
            throw new EntityNotFoundException(UploadSchema.class);
        }
        return schema;
    }

    /**
     * Gets the latest available revision of the specified schema for the specified client. This API fetches every
     * schema revision for the specified schema ID, then checks the schema's min/maxAppVersion against the clientInfo.
     * If multiple schema revisions match, it returns the latest one.
     */
    public UploadSchema getLatestUploadSchemaRevisionForAppVersion(StudyIdentifier studyId, String schemaId,
            ClientInfo clientInfo) {
        checkNotNull(studyId, "Study ID must be specified");
        checkNotNull(clientInfo, "Client Info must be specified");

        List<UploadSchema> schemaList = getUploadSchemaAllRevisions(studyId, schemaId);
        return schemaList.stream().filter(schema -> isSchemaAvailableForClientInfo(schema, clientInfo))
                .max((schema1, schema2) -> Integer.compare(schema1.getRevision(), schema2.getRevision())).orElse(null);
    }

    // Helper method which checks if a schema is available for a client, by checking the schema's min/maxAppVersion
    // against the client's OS and appVersion.
    //
    // This filter is permissive. If neither the ClientInfo nor the constraints in the schema exclude this schema,
    // then the schema is available.
    //
    // Package-scoped to facilitate unit tests.
    static boolean isSchemaAvailableForClientInfo(UploadSchema schema, ClientInfo clientInfo) {
        String osName = clientInfo.getOsName();
        Integer appVersion = clientInfo.getAppVersion();
        if (osName != null && appVersion != null) {
            Integer minAppVersion = schema.getMinAppVersion(osName);
            if (minAppVersion != null && appVersion < minAppVersion) {
                return false;
            }

            Integer maxAppVersion = schema.getMaxAppVersion(osName);
            if (maxAppVersion != null && appVersion > maxAppVersion) {
                return false;
            }
        }

        // Permissive filter defaults to true.
        return true;
    }

    /**
     * <p>
     * Updates a schema rev using V4 semantics. This also validates that the schema changes are legal. Legal changes
     * means schema fields cannot be deleted or modified.
     * </p>
     * <p>
     * Updating a schema revision that doesn't exist throws an EntityNotFoundException.
     * </p>
     */
    public UploadSchema updateSchemaRevisionV4(StudyIdentifier studyId, String schemaId, int revision,
            UploadSchema schemaToUpdate) {
        // Controller guarantees valid studyId and non-null uploadSchema
        checkNotNull(studyId, "studyId must be non-null");
        checkNotNull(schemaToUpdate, "uploadSchema must be non-null");

        // Get existing schema revision. This also validates schema ID and rev and throws if the schema revision
        // doesn't exist.
        UploadSchema oldSchema = getUploadSchemaByIdAndRev(studyId, schemaId, revision);

        // Set study ID, schema ID, and revision. This ensures we are updating the correct schema in the correct study.
        schemaToUpdate.setStudyId(studyId.getIdentifier());
        schemaToUpdate.setSchemaId(schemaId);
        schemaToUpdate.setRevision(revision);

        // validate schema
        Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, schemaToUpdate);

        // Get field names for old and new schema and compute the fields that have been deleted or retained.
        List<String> errorMessageList = new ArrayList<>();

        Map<String, UploadFieldDefinition> oldFieldMap = getFieldsByName(oldSchema);
        Set<String> oldFieldNameSet = oldFieldMap.keySet();

        Map<String, UploadFieldDefinition> newFieldMap = getFieldsByName(schemaToUpdate);
        Set<String> newFieldNameSet = newFieldMap.keySet();

        Set<String> addedFieldNameSet = Sets.difference(newFieldNameSet, oldFieldNameSet);
        Set<String> deletedFieldNameSet = Sets.difference(oldFieldNameSet, newFieldNameSet);
        Set<String> retainedFieldNameSet = Sets.intersection(oldFieldNameSet, newFieldNameSet);

        // Added fields must be optional.
        Set<String> invalidAddedFieldNameSet = new TreeSet<>();
        for (String oneAddedFieldName : addedFieldNameSet) {
            UploadFieldDefinition addedField = newFieldMap.get(oneAddedFieldName);
            if (addedField.isRequired()) {
                invalidAddedFieldNameSet.add(oneAddedFieldName);
            }
        }
        if (!invalidAddedFieldNameSet.isEmpty()) {
            errorMessageList.add("Added fields must be optional: " + BridgeUtils.COMMA_SPACE_JOINER
                    .join(invalidAddedFieldNameSet));
        }

        // Check deleted fields.
        if (!deletedFieldNameSet.isEmpty()) {
            errorMessageList.add("Can't delete fields: " + BridgeUtils.COMMA_SPACE_JOINER.join(deletedFieldNameSet));
        }

        // Check retained fields, make sure none are modified.
        Set<String> modifiedFieldNameSet = new TreeSet<>();
        for (String oneRetainedFieldName : retainedFieldNameSet) {
            UploadFieldDefinition oldFieldDef = oldFieldMap.get(oneRetainedFieldName);
            UploadFieldDefinition newFieldDef = newFieldMap.get(oneRetainedFieldName);

            if (!UploadUtil.isCompatibleFieldDef(oldFieldDef, newFieldDef)) {
                modifiedFieldNameSet.add(oneRetainedFieldName);
            }
        }
        if (!modifiedFieldNameSet.isEmpty()) {
            errorMessageList.add("Incompatible changes to fields: " + BridgeUtils.COMMA_SPACE_JOINER.join(
                    modifiedFieldNameSet));
        }

        // Can't modify schema types.
        if (oldSchema.getSchemaType() != schemaToUpdate.getSchemaType()) {
            errorMessageList.add("Can't modify schema type, old=" + oldSchema.getSchemaType() + ", new=" +
                    schemaToUpdate.getSchemaType());
        }

        // If we have any errors, concat them together and throw a 400 bad request.
        if (!errorMessageList.isEmpty()) {
            throw new BadRequestException("Can't update study " + studyId.getIdentifier() + " schema " + schemaId +
                    " revision " + revision + ": " + BridgeUtils.SEMICOLON_SPACE_JOINER.join(errorMessageList));
        }

        // Call through to the DAO
        return uploadSchemaDao.updateSchemaRevision(schemaToUpdate);
    }

    // Helper method to get a map of fields by name for an Upload Schema. Returns a TreeMap so our error messaging has
    // the fields in a consistent order.
    private static Map<String, UploadFieldDefinition> getFieldsByName(UploadSchema uploadSchema) {
        Map<String, UploadFieldDefinition> fieldsByName = new TreeMap<>();
        fieldsByName.putAll(Maps.uniqueIndex(uploadSchema.getFieldDefinitions(), UploadFieldDefinition::getName));
        return fieldsByName;
    }
}
