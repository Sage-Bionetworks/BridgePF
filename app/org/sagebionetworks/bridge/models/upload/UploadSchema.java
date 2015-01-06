package org.sagebionetworks.bridge.models.upload;

import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import org.sagebionetworks.bridge.dynamodb.DynamoUploadSchema;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;

@BridgeTypeName("UploadSchema")
@JsonDeserialize(as = DynamoUploadSchema.class)
public interface UploadSchema extends BridgeEntity {
    List<UploadFieldDefinition> getFieldDefinitions();

    String getName();

    int getRevision();

    String getSchemaId();

    String getStudyId();
}
