package org.sagebionetworks.bridge.dynamodb;

import java.util.List;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBSaveExpression;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.dynamodbv2.model.ExpectedAttributeValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.UploadSchemaDao;
import org.sagebionetworks.bridge.exceptions.ConcurrentModificationException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.upload.UploadSchema;

@Component
public class DynamoUploadSchemaDao implements UploadSchemaDao {
    private static final DynamoDBSaveExpression DOES_NOT_EXIST_EXPRESSION = new DynamoDBSaveExpression()
            .withExpectedEntry("key", new ExpectedAttributeValue(false));

    private DynamoDBMapper mapper;

    @Autowired
    public void setDynamoDbClient(AmazonDynamoDB client) {
        DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig.Builder()
                .withTableNameOverride(TableNameOverrideFactory.getTableNameOverride(DynamoUploadSchema.class)).build();
        mapper = new DynamoDBMapper(client, mapperConfig);
    }

    @Override
    public UploadSchema createOrUpdateUploadSchema(String studyId, String schemaId, UploadSchema uploadSchema) {
        // Get the current version of the uploadSchema, if it exists
        DynamoUploadSchema oldUploadSchema = getUploadSchemaNoThrow(studyId, schemaId);
        int oldRev;
        if (oldUploadSchema != null) {
            oldRev = oldUploadSchema.getRevision();
        } else {
            oldRev = 0;
        }

        // Request should match old rev. If it does, auto-increment and write.
        if (oldRev != uploadSchema.getRevision()) {
            // TODO throw something
        }

        // Use Jackson to build a DynamoUploadSchema object, so we can update the rev.
        DynamoUploadSchema ddbUploadSchema;
        if (uploadSchema instanceof DynamoUploadSchema) {
            ddbUploadSchema = (DynamoUploadSchema) uploadSchema;
        } else {
            ddbUploadSchema = BridgeObjectMapper.get().convertValue(uploadSchema, DynamoUploadSchema.class);
        }
        ddbUploadSchema.setRevision(oldRev + 1);

        try {
            mapper.save(uploadSchema, DOES_NOT_EXIST_EXPRESSION);
        } catch (ConditionalCheckFailedException ex) {
            throw new ConcurrentModificationException(uploadSchema);
        }
        return uploadSchema;
    }

    @Override
    public UploadSchema getUploadSchema(String studyId, String schemaId) {
        UploadSchema uploadSchema = getUploadSchemaNoThrow(studyId, schemaId);
        // TODO: Validate
        return uploadSchema;
    }

    private DynamoUploadSchema getUploadSchemaNoThrow(String studyId, String schemaId) {
        DynamoUploadSchema key = new DynamoUploadSchema();
        key.setStudyId(studyId);
        key.setSchemaId(schemaId);

        DynamoDBQueryExpression<DynamoUploadSchema> ddbQuery = new DynamoDBQueryExpression<DynamoUploadSchema>()
                .withHashKeyValues(key).withScanIndexForward(false).withLimit(1);
        List<DynamoUploadSchema> schemaList = mapper.query(DynamoUploadSchema.class, ddbQuery);
        if (schemaList.isEmpty()) {
            return null;
        } else {
            return schemaList.get(0);
        }
    }
}
