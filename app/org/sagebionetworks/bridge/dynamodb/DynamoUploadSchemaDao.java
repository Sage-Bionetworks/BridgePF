package org.sagebionetworks.bridge.dynamodb;

import javax.annotation.Nonnull;
import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBSaveExpression;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.dynamodbv2.model.ExpectedAttributeValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import com.google.common.collect.Sets;
import org.springframework.stereotype.Component;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.UploadSchemaDao;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
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

    // Empirically, an array of 37 ints would break the 100 char string length. To give some safety buffer, set the max
    // array length to 20.
    private static final int MAX_MULTI_VALUE_INT_LENGTH = 20;

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
        // that schema rev again, we'll correctly through a ConcurrentModificationException.
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
                } else if (multiValueConstraints.getEnumeration() != null
                        && multiValueConstraints.getEnumeration().size() > MAX_MULTI_VALUE_INT_LENGTH) {
                    // Even if it's not a string, if there are too many options and someone selects all of them, it
                    // could exceed the 100-char limit.
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

    /** {@inheritDoc} */
    @Override
    public @Nonnull UploadSchema updateSchemaRevisionV4(@Nonnull StudyIdentifier studyId, @Nonnull String schemaId,
            int schemaRev, @Nonnull UploadSchema uploadSchema) {
        List<String> errorMessageList = new ArrayList<>();

        // Get existing version of the schema rev (throws if it doesn't exist).
        UploadSchema oldSchema = getUploadSchemaByIdAndRev(studyId, schemaId, schemaRev);

        // Get field names for old and new schema and compute the fields that have been deleted or retained. (Don't
        // care about added fields.)
        Map<String, UploadFieldDefinition> oldFieldMap = getFieldsByName(oldSchema);
        Set<String> oldFieldNameSet = oldFieldMap.keySet();

        Map<String, UploadFieldDefinition> newFieldMap = getFieldsByName(uploadSchema);
        Set<String> newFieldNameSet = newFieldMap.keySet();

        Set<String> deletedFieldNameSet = Sets.difference(oldFieldNameSet, newFieldNameSet);
        Set<String> retainedFieldNameSet = Sets.intersection(oldFieldNameSet, newFieldNameSet);

        // Check deleted fields.
        if (!deletedFieldNameSet.isEmpty()) {
            errorMessageList.add("Can't delete fields: " + BridgeUtils.COMMA_SPACE_JOINER.join(deletedFieldNameSet));
        }

        // Check retained fields, make sure none are modified.
        Set<String> modifiedFieldNameSet = new TreeSet<>();
        for (String oneRetainedFieldName : retainedFieldNameSet) {
            UploadFieldDefinition oldFieldDef = oldFieldMap.get(oneRetainedFieldName);
            UploadFieldDefinition newFieldDef = newFieldMap.get(oneRetainedFieldName);

            if (!equalsExceptMaxAppVersion(oldFieldDef, newFieldDef)) {
                modifiedFieldNameSet.add(oneRetainedFieldName);
            }
        }
        if (!modifiedFieldNameSet.isEmpty()) {
            errorMessageList.add("Can't modify fields: " + BridgeUtils.COMMA_SPACE_JOINER.join(modifiedFieldNameSet));
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
        List<UploadFieldDefinition> fieldDefList = uploadSchema.getFieldDefinitions();
        for (UploadFieldDefinition oneFieldDef : fieldDefList) {
            fieldsByName.put(oneFieldDef.getName(), oneFieldDef);
        }
        return fieldsByName;
    }

    // Helper method to test that two field defs are identical except for the maxAppVersion field (which can only be
    // added to newFieldDef). This is because adding maxAppVersion is how we mark fields as deprecated. Package-scoped
    // to facilitate unit tests.
    static boolean equalsExceptMaxAppVersion(UploadFieldDefinition oldFieldDef, UploadFieldDefinition newFieldDef) {
        // Short-cut: If they're equal with maxAppVersion, then they're equal regardless.
        if (oldFieldDef.equals(newFieldDef)) {
            return true;
        }

        // Make copies of both, but blank out maxAppVersion so we can use .equals().
        UploadFieldDefinition copyOldFieldDef = new DynamoUploadFieldDefinition.Builder().copyOf(oldFieldDef)
                .withMaxAppVersion(null).build();
        UploadFieldDefinition copyNewFieldDef = new DynamoUploadFieldDefinition.Builder().copyOf(newFieldDef)
                .withMaxAppVersion(null).build();

        // If the .equals() fails, short-circuit.
        if (!copyOldFieldDef.equals(copyNewFieldDef)) {
            return false;
        }

        // Now we know they differ only in the maxAppVersion. This is valid if old has no maxAppVersion. (Because we
        // know the differ in maxAppVersion, if old is null, we don't need to check new.)
        return oldFieldDef.getMaxAppVersion() == null;
    }
}
