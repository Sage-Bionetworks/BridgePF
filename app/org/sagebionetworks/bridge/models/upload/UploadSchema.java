package org.sagebionetworks.bridge.models.upload;

import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;

import org.sagebionetworks.bridge.dynamodb.DynamoUploadSchema;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
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
    ObjectWriter PUBLIC_SCHEMA_WRITER = BridgeObjectMapper.get().writer(new SimpleFilterProvider().addFilter("filter",
                    SimpleBeanPropertyFilter.serializeAllExcept("studyId")));

    /** Creates an UploadSchema using a concrete implementation. */
    static UploadSchema create() {
        return new DynamoUploadSchema();
    }

    /**
     * Get all the operating system names that are used to declare either minimum or maximum app
     * versions (or both). Used to iterate through these collections.
     */
    Set<String> getAppVersionOperatingSystems();

    /** A list of fields defined in the schema. This can be changed across different schema revisions. */
    List<UploadFieldDefinition> getFieldDefinitions();

    /** @see #getFieldDefinitions */
    void setFieldDefinitions(List<UploadFieldDefinition> fieldDefinitionList);

    /**
     * Maximum required app version for this schema to match, specified for an operating system. If
     * the operating system name is specified in the User-Agent string, then the app version must be
     * equal to or less than this value.
     */
    Integer getMaxAppVersion(String osName);

    /** @see #getMaxAppVersion */
    void setMaxAppVersion(String osName, Integer maxAppVersion);

    /**
     * Minimum required app version for this schema to match, specified for an operating system. If
     * the operating system name is specified in the User-Agent string, then the app version must be
     * equal to or greater than this value.
     */
    Integer getMinAppVersion(String osName);

    /** @see #getMinAppVersion */
    void setMinAppVersion(String osName, Integer minAppVersion);

    /** Module ID, if this schema was imported from a shared module. */
    String getModuleId();

    /** @see #getModuleId */
    void setModuleId(String moduleId);

    /** Module version, if this schema was imported from a shared module. */
    Integer getModuleVersion();

    /** @see #getModuleVersion */
    void setModuleVersion(Integer moduleVersion);

    /**
     * Human-friendly displayable schema name, such as "Tapping Activity Task". This can be changed across different
     * schema revisions.
     */
    String getName();

    /** @see #getName */
    void setName(String name);

    /**
     * Revision number. This is a secondary ID used to partition different Synapse tables based on breaking changes in
     * a schema.
     */
    int getRevision();

    /** @see #getRevision */
    void setRevision(int revision);

    /**
     * Unique identifier for the schema. This need only be unique to a given study. This should included in the upload
     * data. This can be human readable, such as "tapping-task". This cannot be changed across different schema
     * revisions.
     */
    String getSchemaId();

    /** @see #getSchemaId */
    void setSchemaId(String schemaId);

    /**
     * Gets the schema key as represented by the UploadSchemaKey object. This is generated from {@link #getStudyId},
     * {@link #getSchemaId}, and {@link #getRevision}.
     */
    UploadSchemaKey getSchemaKey();

    /** Schema type, for example survey vs data. */
    UploadSchemaType getSchemaType();

    /** @see #getSchemaType */
    void setSchemaType(UploadSchemaType schemaType);

    /** The survey GUID if this is a survey schema. */
    String getSurveyGuid();

    /** @see #getSurveyGuid */
    void setSurveyGuid(String surveyGuid);

    /** For survey createdOn (versionedOn) if this is a survey schema. */
    Long getSurveyCreatedOn();

    /** @see #getSurveyCreatedOn */
    void setSurveyCreatedOn(Long surveyCreatedOn);

    /**
     * Study ID that this schema lives in. This is not exposed to the callers of the upload schema API, but is
     * available here for internal usage.
     */
    String getStudyId();

    /** @see #getStudyId */
    void setStudyId(String studyId);
    
    /**
     * Is this schema revision marked as deleted?
     */
    boolean isDeleted();
    
    /** @see #isDeleted */
    void setDeleted(boolean deleted);
}
