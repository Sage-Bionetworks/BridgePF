package org.sagebionetworks.bridge.dynamodb;

import javax.annotation.Nonnull;
import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBSaveExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.dynamodbv2.model.ExpectedAttributeValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.springframework.stereotype.Component;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.UploadSchemaDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
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

/** DynamoDB implementation of the {@link org.sagebionetworks.bridge.dao.UploadSchemaDao} */
@Component
public class DynamoUploadSchemaDao implements UploadSchemaDao {
    /**
     * DynamoDB save expression for conditional puts if and only if the row doesn't already exist. This save expression
     * is executed on the row that would be written to. We only need to check the hash key, since the entire row won't
     * exist (including both the hash key and the range key).
     */
    private static final DynamoDBSaveExpression DOES_NOT_EXIST_EXPRESSION = new DynamoDBSaveExpression()
            .withExpectedEntry("key", new ExpectedAttributeValue(false));

    // Mapping from Survey types to Schema types. We have two separate typing systems because the Survey typing system
    // is slightly incompatible with Schemas, most notably around multi-choice and single-choice questions.
    private static final Map<DataType, UploadFieldType> SURVEY_TO_SCHEMA_TYPE =
            ImmutableMap.<DataType, UploadFieldType>builder()
                    // TODO is Duration necessary? Or can we just replace with Integer?
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
        DynamoUploadSchemaDao.singleChoiceDefaultLength = singleChoiceDefaultLength;
    }

    /** Resets the singleChoiceDefaultLength and removes the override. Used for unit tests. */
    static void resetSingleChoiceDefaultLength() {
        DynamoUploadSchemaDao.singleChoiceDefaultLength = SINGLE_CHOICE_DEFAULT_LENGTH;
    }

    private DynamoDBMapper mapper;
    private DynamoIndexHelper studyIdIndex;

    /**
     * This is the DynamoDB mapper that reads from and writes to our DynamoDB table. This is normally configured by
     * Spring.
     */
    @Resource(name = "uploadSchemaDdbMapper")
    public void setDdbMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }

    @Resource(name = "uploadSchemaStudyIdIndex")
    public void setStudyIdIndex(DynamoIndexHelper studyIdIndex) {
        this.studyIdIndex = studyIdIndex;
    }

    /** {@inheritDoc} */
    @Override
    public @Nonnull UploadSchema createSchemaRevisionV4(@Nonnull StudyIdentifier studyId,
            @Nonnull UploadSchema uploadSchema) {
        // Currently, all UploadSchemas are DynamoUploadSchemas, so we don't need to validate this class cast.
        DynamoUploadSchema ddbUploadSchema = (DynamoUploadSchema) uploadSchema;

        // Set revision if needed. 0 represents an unset schema rev.
        if (uploadSchema.getRevision() == 0) {
            int oldRev = getCurrentSchemaRevision(studyId.getIdentifier(), uploadSchema.getSchemaId());
            ddbUploadSchema.setRevision(oldRev + 1);
        }

        // Set study.
        ddbUploadSchema.setStudyId(studyId.getIdentifier());

        // Blank of version. This allows people to copy-paste schema revs from other studies, or easily create a new
        // schema rev from a previously existing one. It also means if they get a schema rev and then try to create
        // that schema rev again, we'll correctly throw a ConcurrentModificationException.
        ddbUploadSchema.setVersion(null);

        // Call DDB to create.
        try {
            mapper.save(ddbUploadSchema);
        } catch (ConditionalCheckFailedException ex) {
            throw new ConcurrentModificationException(ddbUploadSchema);
        }

        return ddbUploadSchema;
    }

    /** {@inheritDoc} */
    @Override
    public @Nonnull UploadSchema createOrUpdateUploadSchema(@Nonnull String studyId,
            @Nonnull UploadSchema uploadSchema) {
        // Request should match old rev. If it does, auto-increment and write.
        // Strictly speaking, the save expression will also catch this condition, but checking this early saves us
        // a DDB round-trip.
        int oldRev = getCurrentSchemaRevision(studyId, uploadSchema.getSchemaId());
        if (oldRev != uploadSchema.getRevision()) {
            throw new ConcurrentModificationException(uploadSchema);
        }

        // We need to (a) increment the rev and (b) inject the study ID (for the DDB index)
        // Currently, all UploadSchemas are DynamoUploadSchemas, so we don't need to validate this class cast.
        DynamoUploadSchema ddbUploadSchema = (DynamoUploadSchema) uploadSchema;
        ddbUploadSchema.setRevision(oldRev + 1);
        ddbUploadSchema.setStudyId(studyId);

        // Clear version. We're creating a new row to handle the new rev, so this starts at version 0 (null).
        ddbUploadSchema.setVersion(null);

        try {
            mapper.save(uploadSchema, DOES_NOT_EXIST_EXPRESSION);
        } catch (ConditionalCheckFailedException ex) {
            throw new ConcurrentModificationException(uploadSchema);
        }
        return uploadSchema;
    }

    /** {@inheritDoc} */
    @Override
    public @Nonnull UploadSchema createUploadSchemaFromSurvey(@Nonnull StudyIdentifier studyIdentifier,
            @Nonnull Survey survey, boolean newSchemaRev) {
        // create upload field definitions from survey questions
        List<UploadFieldDefinition> newFieldDefList = new ArrayList<>();
        for (SurveyQuestion oneQuestion : survey.getUnmodifiableQuestionList()) {
            addFieldDefsForSurveyQuestion(newFieldDefList, oneQuestion);
        }

        // Create schema. No need to set rev or version unless updating an existing rev. Study is always taken care of
        // by the APIs.
        String schemaId = survey.getIdentifier();
        DynamoUploadSchema schemaToCreate = new DynamoUploadSchema();
        schemaToCreate.setFieldDefinitions(newFieldDefList);
        schemaToCreate.setName(survey.getName());
        schemaToCreate.setSchemaId(schemaId);
        schemaToCreate.setSchemaType(UploadSchemaType.IOS_SURVEY);
        schemaToCreate.setSurveyGuid(survey.getGuid());
        schemaToCreate.setSurveyCreatedOn(survey.getCreatedOn());

        // Validate schema. There's nowhere else in the call path that validates it, so we have to do it here.
        Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, schemaToCreate);

        // Short-cut: If we specify we want a new schema rev, then skip directly to creating a new schema rev.
        if (!newSchemaRev) {
            // Get the current rev.
            String studyId = studyIdentifier.getIdentifier();
            DynamoUploadSchema oldUploadSchema = getUploadSchemaNoThrow(studyId, schemaId);
            if (oldUploadSchema != null && oldUploadSchema.getSchemaType() == UploadSchemaType.IOS_SURVEY) {
                List<UploadFieldDefinition> oldFieldDefList = oldUploadSchema.getFieldDefinitions();

                // Optimization: If the new schema and the old schema have the same fields, return the old schema
                // instead of creating a new one.
                //
                // Dump the fieldDefLists into a set, because if we have the same fields in a different order, the
                // schemas are compatible, and we should use the old schema too.
                Set<UploadFieldDefinition> oldFieldDefSet = ImmutableSet.copyOf(oldFieldDefList);
                Set<UploadFieldDefinition> newFieldDefSet = ImmutableSet.copyOf(newFieldDefList);
                if (oldFieldDefSet.equals(newFieldDefSet)) {
                    return oldUploadSchema;
                }

                // Otherwise, merge the old and new field defs to create a new schema.
                MergeSurveySchemaResult mergeResult = mergeSurveySchemaFields(oldFieldDefList, newFieldDefList);
                if (mergeResult.isSuccess()) {
                    // We successfully merged the field def lists. We can successfully update the existing schema
                    // in-place.
                    schemaToCreate.setVersion(oldUploadSchema.getVersion());
                    schemaToCreate.setFieldDefinitions(mergeResult.getFieldDefinitionList());

                    // We changed the schema, so we have to re-validate it.
                    Validate.entityThrowingException(UploadSchemaValidator.INSTANCE, schemaToCreate);

                    return updateSchemaRevisionV4(studyIdentifier, schemaId, oldUploadSchema.getRevision(),
                            schemaToCreate);
                }
            }
        }

        // We were unable to reconcile this with the existing schema. Create a new schema. (Create API will
        // automatically bump the rev number if an old schema revision exists.)
        return createSchemaRevisionV4(studyIdentifier, schemaToCreate);
    }

    // Helper method to convert a survey question into one or more schema field defs. As some survey questions can
    // generate more than one field (such as unit fields), callers should pass in a field def list, and this method
    // will append created field defs to that list.
    private static void addFieldDefsForSurveyQuestion(List<UploadFieldDefinition> fieldDefList,
            SurveyQuestion question) {
        // These preconditions should never happen, but we have a Preconditions check here just in case.
        Preconditions.checkNotNull(question);

        Constraints constraints = question.getConstraints();
        Preconditions.checkNotNull(constraints);

        DataType surveyQuestionType = constraints.getDataType();
        Preconditions.checkNotNull(surveyQuestionType);

        // Init field def builder with basic fields. Note that all survey questions are skippable, so mark the field as
        // optional (not required).
        String fieldName = question.getIdentifier();
        DynamoUploadFieldDefinition.Builder fieldDefBuilder = new DynamoUploadFieldDefinition.Builder()
                .withName(fieldName).withRequired(false);

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
            UploadFieldDefinition unitFieldDef = new DynamoUploadFieldDefinition.Builder()
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
                DynamoUploadFieldDefinition.Builder modifiedFieldDefBuilder = new DynamoUploadFieldDefinition.Builder()
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

    /** {@inheritDoc} */
    @Override
    public void deleteUploadSchemaByIdAndRev(@Nonnull StudyIdentifier studyIdentifier, @Nonnull String schemaId,
            int rev) {
        String studyId = studyIdentifier.getIdentifier();

        // query DDB to make sure the schema exists before we issue a delete request
        DynamoUploadSchema key = new DynamoUploadSchema();
        key.setStudyId(studyId);
        key.setSchemaId(schemaId);
        key.setRevision(rev);
        DynamoUploadSchema schemaToDelete = mapper.load(key);
        if (schemaToDelete == null) {
            throw new EntityNotFoundException(UploadSchema.class, String.format(
                    "Upload schema not found for study %s, schema ID %s, revision %d", studyId, schemaId, rev));
        }

        // now delete it
        mapper.delete(schemaToDelete);
    }

    /** {@inheritDoc} */
    @Override
    public void deleteUploadSchemaById(@Nonnull StudyIdentifier studyIdentifier, @Nonnull String schemaId) {
        String studyId = studyIdentifier.getIdentifier();

        // query DDB to get all of the rows that need to be deleted
        DynamoUploadSchema key = new DynamoUploadSchema();
        key.setStudyId(studyId);
        key.setSchemaId(schemaId);
        DynamoDBQueryExpression<DynamoUploadSchema> ddbQuery = new DynamoDBQueryExpression<DynamoUploadSchema>()
                .withHashKeyValues(key);
        List<DynamoUploadSchema> schemaList = mapper.query(DynamoUploadSchema.class, ddbQuery);
        if (schemaList.isEmpty()) {
            throw new EntityNotFoundException(UploadSchema.class, String.format(
                    "Upload schema not found for study %s, schema ID %s", studyId, schemaId));
        }

        // now batch delete these schemas
        List<DynamoDBMapper.FailedBatch> failureList = mapper.batchDelete(schemaList);
        BridgeUtils.ifFailuresThrowException(failureList);
    }

    /** {@inheritDoc} */
    @Override
    public @Nonnull UploadSchema getUploadSchema(@Nonnull String studyId, @Nonnull String schemaId) {
        UploadSchema uploadSchema = getUploadSchemaNoThrow(studyId, schemaId);
        if (uploadSchema == null) {
            throw new EntityNotFoundException(UploadSchema.class, String.format(
                    "Upload schema not found for study %s, schema ID %s", studyId, schemaId));
        }
        return uploadSchema;
    }
    
    /** {@inheritDoc} */
    @Override
    public @Nonnull List<UploadSchema> getUploadSchemaAllRevisions(@Nonnull StudyIdentifier studyIdentifier, @Nonnull String schemaId) {
        List<DynamoUploadSchema> uploadSchemas = getUploadSchemaAllRevisionsNoThrow(studyIdentifier.getIdentifier(), schemaId);
        if (uploadSchemas.isEmpty()) {
            throw new EntityNotFoundException(UploadSchema.class, String.format(
                "Upload schema not found for study %s, schema ID %s", studyIdentifier.getIdentifier(), schemaId));
        }
        return ImmutableList.<UploadSchema>copyOf(uploadSchemas);
    }

    /** {@inheritDoc} */
    @Override
    public @Nonnull UploadSchema getUploadSchemaByIdAndRev(@Nonnull StudyIdentifier studyIdentifier,
            @Nonnull String schemaId, int schemaRev) {
        String studyId = studyIdentifier.getIdentifier();

        DynamoUploadSchema key = new DynamoUploadSchema();
        key.setStudyId(studyId);
        key.setSchemaId(schemaId);
        key.setRevision(schemaRev);

        DynamoUploadSchema schema = mapper.load(key);
        if (schema == null) {
            throw new EntityNotFoundException(UploadSchema.class, String.format(
                    "Upload schema not found for study %s, schema ID %s, revision %d", studyId, schemaId, schemaRev));
        }
        return schema;
    }

    /**
     * <p>
     * Helper function, which gets a schema from DDB. This is used by the get (which validates afterwards) and
     * the put (which needs to check for concurrent modification exceptions). The return value of this helper method
     * may be null.
     * </p>
     * <p>
     * Package-scoped to facilitate unit tests.
     * </p>
     */
    DynamoUploadSchema getUploadSchemaNoThrow(@Nonnull String studyId, @Nonnull String schemaId) {
        DynamoUploadSchema key = new DynamoUploadSchema();
        key.setStudyId(studyId);
        key.setSchemaId(schemaId);

        // Get the latest revision. This is accomplished by scanning the range key backwards.
        DynamoDBQueryExpression<DynamoUploadSchema> ddbQuery = new DynamoDBQueryExpression<DynamoUploadSchema>()
                .withHashKeyValues(key).withScanIndexForward(false).withLimit(1);
        QueryResultPage<DynamoUploadSchema> resultPage = mapper.queryPage(DynamoUploadSchema.class, ddbQuery);
        List<DynamoUploadSchema> schemaList = resultPage.getResults();
        if (schemaList.isEmpty()) {
            return null;
        } else {
            return schemaList.get(0);
        }
    }
    
    private List<DynamoUploadSchema> getUploadSchemaAllRevisionsNoThrow(@Nonnull String studyId, @Nonnull String schemaId) {
        DynamoUploadSchema key = new DynamoUploadSchema();
        key.setStudyId(studyId);
        key.setSchemaId(schemaId);

        // Get all revisions, in reverse sort order (highest first)
        DynamoDBQueryExpression<DynamoUploadSchema> ddbQuery = new DynamoDBQueryExpression<DynamoUploadSchema>()
                .withHashKeyValues(key).withScanIndexForward(false);
        return mapper.query(DynamoUploadSchema.class, ddbQuery);
    }

    /**
     * Private helper function to get the current schema revision of the specified schema ID. Returns 0 if the schema
     * doesn't exist. This is generally useful for validating the proper revision number for making updates.
     */
    private int getCurrentSchemaRevision(@Nonnull String studyId, @Nonnull String schemaId) {
        DynamoUploadSchema oldUploadSchema = getUploadSchemaNoThrow(studyId, schemaId);
        if (oldUploadSchema != null) {
            return oldUploadSchema.getRevision();
        } else {
            return 0;
        }
    }

    /** {@inheritDoc} */
    @Override
    public @Nonnull List<UploadSchema> getUploadSchemasForStudy(@Nonnull StudyIdentifier studyId) {
        List<UploadSchema> allSchemasAllRevisions = studyIdIndex.query(UploadSchema.class, "studyId", studyId.getIdentifier(), null);
        
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

    /** {@inheritDoc} */
    @Override
    public @Nonnull UploadSchema updateSchemaRevisionV4(@Nonnull StudyIdentifier studyId, @Nonnull String schemaId,
            int schemaRev, @Nonnull UploadSchema uploadSchema) {
        List<String> errorMessageList = new ArrayList<>();

        // Get existing version of the schema rev (throws if it doesn't exist).
        UploadSchema oldSchema = getUploadSchemaByIdAndRev(studyId, schemaId, schemaRev);

        // Get field names for old and new schema and compute the fields that have been deleted or retained.
        Map<String, UploadFieldDefinition> oldFieldMap = getFieldsByName(oldSchema);
        Set<String> oldFieldNameSet = oldFieldMap.keySet();

        Map<String, UploadFieldDefinition> newFieldMap = getFieldsByName(uploadSchema);
        Set<String> newFieldNameSet = newFieldMap.keySet();

        Set<String> addedFieldNameSet = Sets.difference(newFieldNameSet, oldFieldNameSet);
        Set<String> deletedFieldNameSet = Sets.difference(oldFieldNameSet, newFieldNameSet);
        Set<String> retainedFieldNameSet = Sets.intersection(oldFieldNameSet, newFieldNameSet);

        // Added required fields must have minAppVersion set.
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
        if (oldSchema.getSchemaType() != uploadSchema.getSchemaType()) {
            errorMessageList.add("Can't modify schema type, old=" + oldSchema.getSchemaType() + ", new=" +
                    uploadSchema.getSchemaType());
        }

        // If we have any errors, concat them together and throw a 400 bad request.
        if (!errorMessageList.isEmpty()) {
            throw new BadRequestException("Can't update study " + studyId.getIdentifier() + " schema " + schemaId +
                    " revision " + schemaRev + ": " + BridgeUtils.SEMICOLON_SPACE_JOINER.join(errorMessageList));
        }

        // Currently, all UploadSchemas are DynamoUploadSchemas, so we don't need to validate this class cast.
        DynamoUploadSchema ddbUploadSchema = (DynamoUploadSchema) uploadSchema;

        // Set the study ID, since clients don't have this.
        ddbUploadSchema.setStudyId(studyId.getIdentifier());

        // Use the schema ID and rev from the params. This allows callers to copy schemas and schema revs.
        ddbUploadSchema.setSchemaId(schemaId);
        ddbUploadSchema.setRevision(schemaRev);

        // Everything checks out. Write the upload schema. Watch out for concurrent modification.
        try {
            mapper.save(ddbUploadSchema);
        } catch (ConditionalCheckFailedException ex) {
            throw new ConcurrentModificationException(ddbUploadSchema);
        }

        return ddbUploadSchema;
    }

    // Helper method to get a map of fields by name for an Upload Schema. Returns a TreeMap so our error messaging has
    // the fields in a consistent order.
    private static Map<String, UploadFieldDefinition> getFieldsByName(UploadSchema uploadSchema) {
        Map<String, UploadFieldDefinition> fieldsByName = new TreeMap<>();
        fieldsByName.putAll(Maps.uniqueIndex(uploadSchema.getFieldDefinitions(), UploadFieldDefinition::getName));
        return fieldsByName;
    }
}
