package org.sagebionetworks.bridge.models.upload;

import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import org.sagebionetworks.bridge.dynamodb.DynamoUploadSchema;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;

/**
 * This class represents a schema for the uploads sent by the mobile apps. This can be created and updated by study
 * researchers.
 */
@BridgeTypeName("UploadSchema")
@JsonDeserialize(as = DynamoUploadSchema.class)
public interface UploadSchema extends BridgeEntity {
    /** A list of fields defined in the schema. This can be changed across different schema revisions. */
    List<UploadFieldDefinition> getFieldDefinitions();

    /**
     * Human-friendly displayable schema name, such as "Tapping Activity Task". This can be changed across different
     * schema revisions.
     */
    String getName();

    /**
     * Schema revision number. This is managed by the Bridge back-end. For creating new schemas, this should initially
     * be unset (or set to the default value of zero). For updating schemas, this should be set to the revision number
     * of the schema you are updating, to ensure that you aren't updating an older version of the schema. Upon creating
     * or updating a schema, the Bridge back-end will automatically increment this revision number by 1 (for updating
     * existing schemas) or from 0 to 1 (for creating new schemas).
     */
    int getRevision();

    /**
     * Unique identifier for the schema. This need only be unique to a given study. This should included in the upload
     * data. This can be human readable, such as "tapping-task". This cannot be changed across different schema
     * revisions.
     */
    String getSchemaId();

    /** Schema type, for example survey vs data. */
    UploadSchemaType getSchemaType();
}
