package org.sagebionetworks.bridge.models.upload;

import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import org.sagebionetworks.bridge.dynamodb.DynamoUploadSchema;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.schema.UploadSchemaKey;

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
     * Revision number. This is a secondary ID used to partition different Synapse tables based on breaking changes in
     * a schema.
     */
    int getRevision();

    /**
     * Unique identifier for the schema. This need only be unique to a given study. This should included in the upload
     * data. This can be human readable, such as "tapping-task". This cannot be changed across different schema
     * revisions.
     */
    String getSchemaId();

    /** Gets the schema key as represented by the UploadSchemaKey object. */
    UploadSchemaKey getSchemaKey();

    /** Schema type, for example survey vs data. */
    UploadSchemaType getSchemaType();

    /** The survey GUID if this is a survey schema. */
    String getSurveyGuid();

    /** For survey createdOn (versionedOn) if this is a survey schema. */
    Long getSurveyCreatedOn();

    /**
     * Study ID that this schema lives in. This is not exposed to the callers of the upload schema API, but is
     * available here for internal usage.
     */
    String getStudyId();

    /**
     * <p>
     * Version number of a particular schema revision. This is used to detect concurrent modification. Callers should
     * not modify this value. This will be automatically incremented by Bridge.
     * </p>
     * <p>
     * This is currently ignored by the Schema v3 API, which manages its own versioning via revision. However, the
     * Schema v4 API needs this as revision and versions are now independent of each other.
     * </p>
     */
    Long getVersion();
}
