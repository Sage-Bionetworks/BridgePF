package org.sagebionetworks.bridge.dynamodb;

import javax.annotation.Nonnull;
import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBSaveExpression;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.dynamodbv2.model.ExpectedAttributeValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.springframework.stereotype.Component;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.UploadSchemaDao;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.surveys.Constraints;
import org.sagebionetworks.bridge.models.surveys.DataType;
import org.sagebionetworks.bridge.models.surveys.MultiValueConstraints;
import org.sagebionetworks.bridge.models.surveys.StringConstraints;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.surveys.SurveyQuestion;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.models.upload.UploadSchemaType;

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

    private static final int MAX_STRING_LENGTH = 100;

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
            @Nonnull Survey survey) {
        // create upload field definitions from survey questions
        List<UploadFieldDefinition> fieldDefList = new ArrayList<>();
        for (SurveyQuestion oneQuestion : survey.getUnmodifiableQuestionList()) {
            String name = oneQuestion.getIdentifier();
            UploadFieldType type = getFieldTypeFromConstraints(oneQuestion.getConstraints());

            // All survey questions are skippable, so mark the field as optional (not required)
            UploadFieldDefinition oneFieldDef = new DynamoUploadFieldDefinition.Builder().withName(name)
                    .withType(type).withRequired(false).build();
            fieldDefList.add(oneFieldDef);
        }

        // Get the current rev.
        String studyId = studyIdentifier.getIdentifier();
        String schemaId = survey.getIdentifier();
        DynamoUploadSchema oldUploadSchema = getUploadSchemaNoThrow(studyId, schemaId);
        int oldRev = 0;
        if (oldUploadSchema != null) {
            oldRev = oldUploadSchema.getRevision();

            // Optimization: If the new schema and the old schema have the same fields, return the old schema instead
            // of creating a new one.
            //
            // Dump the fieldDefLists into a set, because if we have the same fields in a different order, the schemas
            // are compatible, and we should use the old schema too.
            Set<UploadFieldDefinition> oldFieldDefSet = ImmutableSet.copyOf(oldUploadSchema.getFieldDefinitions());
            Set<UploadFieldDefinition> newFieldDefSet = ImmutableSet.copyOf(fieldDefList);
            if (oldFieldDefSet.equals(newFieldDefSet)) {
                return oldUploadSchema;
            }
        }

        // Create schema.
        DynamoUploadSchema schema = new DynamoUploadSchema();
        schema.setFieldDefinitions(fieldDefList);
        schema.setName(survey.getName());
        schema.setRevision(oldRev);
        schema.setSchemaId(schemaId);
        schema.setSchemaType(UploadSchemaType.IOS_SURVEY);
        schema.setStudyId(studyId);
        UploadSchema createdSchema = createOrUpdateUploadSchema(studyId, schema);

        return createdSchema;
    }

    /**
     * Private helper function that converts a survey question constraints object into an upload schema field type.
     * This is used to help convert surveys into upload schemas.
     */
    private static UploadFieldType getFieldTypeFromConstraints(Constraints constraints) {
        if (constraints == null) {
            // Could be anything. When in doubt, default to inline_json_blob.
            return UploadFieldType.INLINE_JSON_BLOB;
        }

        if (constraints instanceof MultiValueConstraints) {
            MultiValueConstraints multiValueConstraints = (MultiValueConstraints) constraints;

            if (multiValueConstraints.getAllowMultiple()) {
                if (constraints.getDataType() == DataType.STRING) {
                    // Multiple choice string answers frequently become longer than the 100-char limit. This will have
                    // to be a JSON attachment.
                    return UploadFieldType.ATTACHMENT_JSON_BLOB;
                } else {
                    // Multiple choice non-string answers (frequently ints) tend to be very short, so this can fit in
                    // an inline_json_blob.
                    return UploadFieldType.INLINE_JSON_BLOB;
                }
            } else {
                // iOS always returns a JSON array with a single element, so we treat this as an INLINE_JSON_BLOB.
                // TODO: revisit this in the new upload data format
                return UploadFieldType.INLINE_JSON_BLOB;
            }
        } else {
            switch (constraints.getDataType()) {
                case DURATION:
                    // Upload schemas don't have this concept. Treat it as a string. (Durations are short, so this
                    // is fine).
                    return UploadFieldType.STRING;
                case STRING:
                    if (constraints instanceof StringConstraints) {
                        Integer maxLength = ((StringConstraints) constraints).getMaxLength();
                        if (maxLength == null || maxLength > MAX_STRING_LENGTH) {
                            // No max length, or max length is longer than what we can fit in Synapse. This has to
                            // be an attachment.
                            return UploadFieldType.ATTACHMENT_BLOB;
                        } else {
                            // Short string. Can be a string.
                            return UploadFieldType.STRING;
                        }
                    } else {
                        // Constraints aren't StringConstraints, so we can't determine max length (if any). To be
                        // safe, make this an attachment.
                        return UploadFieldType.ATTACHMENT_BLOB;
                    }
                case INTEGER:
                    return UploadFieldType.INT;
                case DECIMAL:
                    return UploadFieldType.FLOAT;
                case BOOLEAN:
                    return UploadFieldType.BOOLEAN;
                case DATE:
                    return UploadFieldType.CALENDAR_DATE;
                case TIME:
                    // Upload schema has no concept of local time. Accept a string.
                    return UploadFieldType.STRING;
                case DATETIME:
                    return UploadFieldType.TIMESTAMP;
                default:
                    // Again, when in doubt, default to inline_json_blob.
                    return UploadFieldType.INLINE_JSON_BLOB;
            }
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
     * Private helper function, which gets a schema from DDB. This is used by the get (which validates afterwards) and
     * the put (which needs to check for concurrent modification exceptions). The return value of this helper method
     * may be null.
     */
    private DynamoUploadSchema getUploadSchemaNoThrow(@Nonnull String studyId, @Nonnull String schemaId) {
        DynamoUploadSchema key = new DynamoUploadSchema();
        key.setStudyId(studyId);
        key.setSchemaId(schemaId);

        // Get the latest revision. This is accomplished by scanning the range key backwards.
        DynamoDBQueryExpression<DynamoUploadSchema> ddbQuery = new DynamoDBQueryExpression<DynamoUploadSchema>()
                .withHashKeyValues(key).withScanIndexForward(false).withLimit(1);
        List<DynamoUploadSchema> schemaList = mapper.query(DynamoUploadSchema.class, ddbQuery);
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
}
