package org.sagebionetworks.bridge.models.upload;

import javax.annotation.Nonnull;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import org.sagebionetworks.bridge.dynamodb.DynamoUploadFieldDefinition;
import org.sagebionetworks.bridge.models.BridgeEntity;

/**
 * This class represents a field definition for an upload schema. This could map to a top-level key-value pair in the
 * raw JSON, or to a column in a Synapse table.
 */
@JsonDeserialize(as = DynamoUploadFieldDefinition.class)
public interface UploadFieldDefinition extends BridgeEntity {
    /** The field name. */
    @Nonnull String getName();

    /** True if the field is required to have data, false otherwise. */
    boolean isRequired();

    /**
     * The field's type.
     *
     * @see org.sagebionetworks.bridge.models.upload.UploadFieldType
     */
    @Nonnull UploadFieldType getType();
}
