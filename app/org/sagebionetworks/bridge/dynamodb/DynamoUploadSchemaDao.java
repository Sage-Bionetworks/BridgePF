package org.sagebionetworks.bridge.dynamodb;

import javax.annotation.Resource;

import java.util.List;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBSaveExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.dynamodbv2.model.ExpectedAttributeValue;
import com.google.common.collect.ImmutableList;

import org.springframework.stereotype.Component;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.UploadSchemaDao;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.upload.UploadSchema;

/** DynamoDB implementation of the {@link org.sagebionetworks.bridge.dao.UploadSchemaDao} */
@Component
public class DynamoUploadSchemaDao implements UploadSchemaDao {
    // package-scoped for unit tests
    static final String STUDY_ID_INDEX_NAME = "studyId-index";

    /**
     * DynamoDB save expression for conditional puts if and only if the row doesn't already exist. This save expression
     * is executed on the row that would be written to. We only need to check the hash key, since the entire row won't
     * exist (including both the hash key and the range key).
     */
    private static final DynamoDBSaveExpression DOES_NOT_EXIST_EXPRESSION = new DynamoDBSaveExpression()
            .withExpectedEntry("key", new ExpectedAttributeValue(false));

    private DynamoDBMapper mapper;

    /**
     * This is the DynamoDB mapper that reads from and writes to our DynamoDB table. This is normally configured by
     * Spring.
     */
    @Resource(name = "uploadSchemaDdbMapper")
    public final void setDdbMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }

    /** {@inheritDoc} */
    @Override
    public UploadSchema createSchemaRevision(UploadSchema schema) {
        // Currently, all UploadSchemas are DynamoUploadSchemas, so we don't need to validate this class cast.
        DynamoUploadSchema ddbSchema = (DynamoUploadSchema) schema;

        // Blank of version. This allows people to copy-paste schema revs from other studies, or easily create a new
        // schema rev from a previously existing one. It also means if they get a schema rev and then try to create
        // that schema rev again, we'll correctly throw a ConcurrentModificationException.
        ddbSchema.setVersion(null);

        // Call DDB to create.
        try {
            mapper.save(ddbSchema, DOES_NOT_EXIST_EXPRESSION);
        } catch (ConditionalCheckFailedException ex) {
            throw new ConcurrentModificationException(ddbSchema);
        }
        return ddbSchema;
    }

    /** {@inheritDoc} */
    @Override
    public void deleteUploadSchemas(List<UploadSchema> schemaList) {
        List<DynamoDBMapper.FailedBatch> failureList = mapper.batchDelete(schemaList);
        BridgeUtils.ifFailuresThrowException(failureList);
    }

    /** {@inheritDoc} */
    @Override
    public List<UploadSchema> getAllUploadSchemasAllRevisions(StudyIdentifier studyId) {
        DynamoUploadSchema hashKey = new DynamoUploadSchema();
        hashKey.setStudyId(studyId.getIdentifier());

        // Note that consistent reads are not allowed for global secondary indices.
        DynamoDBQueryExpression<DynamoUploadSchema> query = new DynamoDBQueryExpression<DynamoUploadSchema>()
                .withIndexName(STUDY_ID_INDEX_NAME).withHashKeyValues(hashKey).withConsistentRead(false);

        List<DynamoUploadSchema> schemaList = queryHelper(query);

        return ImmutableList.copyOf(schemaList);
    }

    /** {@inheritDoc} */
    @Override
    public List<UploadSchema> getUploadSchemaAllRevisionsById(StudyIdentifier studyId, String schemaId) {
        // Make hash key.
        DynamoUploadSchema key = new DynamoUploadSchema();
        key.setStudyId(studyId.getIdentifier());
        key.setSchemaId(schemaId);

        // Get all revisions, in reverse sort order (highest first)
        DynamoDBQueryExpression<DynamoUploadSchema> ddbQuery = new DynamoDBQueryExpression<DynamoUploadSchema>()
                .withHashKeyValues(key).withScanIndexForward(false);
        List<DynamoUploadSchema> schemaList = queryHelper(ddbQuery);

        // Convert generic types.
        return ImmutableList.copyOf(schemaList);
    }

    /** {@inheritDoc} */
    @Override
    public UploadSchema getUploadSchemaByIdAndRevision(StudyIdentifier studyId, String schemaId, int revision) {
        DynamoUploadSchema key = new DynamoUploadSchema();
        key.setStudyId(studyId.getIdentifier());
        key.setSchemaId(schemaId);
        key.setRevision(revision);

        return mapper.load(key);
    }

    /** {@inheritDoc} */
    @Override
    public UploadSchema getUploadSchemaLatestRevisionById(StudyIdentifier studyId, String schemaId) {
        // Make hash key.
        DynamoUploadSchema key = new DynamoUploadSchema();
        key.setStudyId(studyId.getIdentifier());
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

    /** {@inheritDoc} */
    @Override
    public UploadSchema updateSchemaRevision(UploadSchema schema) {
        try {
            mapper.save(schema);
        } catch (ConditionalCheckFailedException ex) {
            throw new ConcurrentModificationException(schema);
        }

        return schema;
    }

    // mapper.query() returns a PaginatedQueryList, which is really hard to work with, which makes mapper.query()
    // really hard to mock. So instead, just wrap the whole thing in a helper method that returns a List instead and
    // use a spy() in tests. Package-scoped to be available for spying in unit tests.
    List<DynamoUploadSchema> queryHelper(DynamoDBQueryExpression<DynamoUploadSchema> query) {
        return mapper.query(DynamoUploadSchema.class, query);
    }
}
