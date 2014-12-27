package org.sagebionetworks.bridge.models.upload;

import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import org.sagebionetworks.bridge.dynamodb.DynamoUploadSchema;

@JsonDeserialize(as = DynamoUploadSchema.class)
public interface UploadSchema {
    List<UploadFieldDefinition> getFieldDefinitions();

    String getId();

    String getName();

    int getRevision();
}
