package org.sagebionetworks.bridge.models.upload;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import org.sagebionetworks.bridge.dynamodb.DynamoUploadFieldDefinition;

@JsonDeserialize(as = DynamoUploadFieldDefinition.class)
public interface UploadFieldDefinition {
    String getName();

    boolean isRequired();

    UploadFieldType getType();
}
