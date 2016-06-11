package org.sagebionetworks.bridge.models.upload;

import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.sagebionetworks.bridge.dynamodb.DynamoUploadFieldDefinition;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * This class represents a field definition for an upload schema. This could map to a top-level key-value pair in the
 * raw JSON, or to a column in a Synapse table.
 */
@JsonDeserialize(as = DynamoUploadFieldDefinition.class)
@BridgeTypeName("UploadFieldDefinition")
public interface UploadFieldDefinition extends BridgeEntity {
    /**
     * Used for ATTACHMENT_V2 types. Used as a hint by BridgeEX to preserve the file extension as a quality-of-life
     * improvement. Optional, defaults to ".tmp".
     * */
    @Nullable String getFileExtension();

    /**
     * Used for ATTACHMENT_V2 types. Used as a hint by BridgeEX to mark a Synapse file handle with the correct MIME
     * type as a quality-of-life improvement. Optional, defaults to "application/octet-stream".
     */
    @Nullable String getMimeType();

    /**
     * The oldest app version number for which this field is required. App versions before this will treat this field
     * as optional, as it doesn't exist yet. Does nothing if required is false.
     */
    @Nullable Integer getMinAppVersion();

    /**
     * Similar to minAppVersion. This is used for when required fields are removed from the app, but we want to re-use
     * the old Synapse table.
     */
    @Nullable Integer getMaxAppVersion();

    /**
     * Used for STRING, SINGLE_CHOICE, and INLINE_JSON_BLOB types. This is a hint for BridgeEX to create a Synapse
     * column with the right width.
     */
    @Nullable Integer getMaxLength();

    /**
     * Used for MULTI_CHOICE types. This lists all valid answers for this field. It is used by BridgeEX to create the
     * Synapse table columns for MULTI_CHOICE fields. This is a list because order matters, in terms of Synapse
     * column order. Must be specified if the field type is a MULTI_CHOICE.
     */
    @Nullable List<String> getMultiChoiceAnswerList();

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
