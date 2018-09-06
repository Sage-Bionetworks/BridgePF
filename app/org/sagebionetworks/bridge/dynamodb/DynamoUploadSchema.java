package org.sagebionetworks.bridge.dynamodb;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;
import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.apache.commons.lang3.StringUtils;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.json.DateTimeToLongSerializer;
import org.sagebionetworks.bridge.json.DateTimeToPrimitiveLongDeserializer;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.models.upload.UploadSchemaType;
import org.sagebionetworks.bridge.schema.UploadSchemaKey;
import org.sagebionetworks.bridge.validators.Validate;

/**
 * The DynamoDB implementation of UploadSchema. This is a mutable class with getters and setters so that it can work
 * with the DynamoDB mapper.
 */
@DynamoThroughput(readCapacity=15, writeCapacity=1)
@DynamoDBTable(tableName = "UploadSchema")
@JsonFilter("filter")
public class DynamoUploadSchema implements UploadSchema {
    private List<UploadFieldDefinition> fieldDefList = ImmutableList.of();
    private Map<String, Integer> maxAppVersions = new HashMap<>();
    private Map<String, Integer> minAppVersions = new HashMap<>();
    private String moduleId;
    private Integer moduleVersion;
    private String name;
    private int rev;
    private String schemaId;
    private UploadSchemaType schemaType;
    private String surveyGuid;
    private Long surveyCreatedOn;
    private String studyId;
    private Long version;
    private boolean deleted;

    /** {@inheritDoc} */
    @DynamoDBIgnore
    @JsonIgnore
    @Override
    public Set<String> getAppVersionOperatingSystems() {
        return new ImmutableSet.Builder<String>().addAll(minAppVersions.keySet()).addAll(maxAppVersions.keySet())
                .build();
    }

    /** {@inheritDoc} */
    @DynamoDBTypeConverted(converter = FieldDefinitionListMarshaller.class)
    @Override
    public List<UploadFieldDefinition> getFieldDefinitions() {
        return fieldDefList;
    }

    /** @see org.sagebionetworks.bridge.models.upload.UploadSchema#getFieldDefinitions */
    @Override
    public void setFieldDefinitions(List<UploadFieldDefinition> fieldDefList) {
        this.fieldDefList = fieldDefList != null? ImmutableList.copyOf(fieldDefList) : ImmutableList.of();
    }

    /**
     * This is the DynamoDB key. It is used by the DynamoDB mapper. This should not be used directly. The key format is
     * "[studyID]:[schemaID]". The schema ID may contain colons. The study ID may not. Since the key is created
     * from the study ID and schema ID, this will throw an InvalidEntityException if either one is blank.
     */
    @DynamoDBHashKey
    @JsonIgnore
    public String getKey() {
        if (StringUtils.isBlank(studyId)) {
            // No study ID means we can't generate a key. However, we should still return null, because this case might
            // still come up (such as querying by secondary index), and we don't want to crash.
            return null;
        }
        if (StringUtils.isBlank(schemaId)) {
            // Similarly here.
            return null;
        }
        return String.format("%s:%s", studyId, schemaId);
    }

    /**
     * Sets the DynamoDB key. This is generally only called by the DynamoDB mapper. If the key is null, empty, or
     * malformatted, this will throw.
     *
     * @see #getKey
     */
    @JsonProperty
    public void setKey(String key) {
        Preconditions.checkNotNull(key, Validate.CANNOT_BE_NULL, "key");
        Preconditions.checkArgument(!key.isEmpty(), Validate.CANNOT_BE_EMPTY_STRING, "key");

        String[] parts = key.split(":", 2);
        Preconditions.checkArgument(parts.length == 2, "key has wrong number of parts");
        Preconditions.checkArgument(!parts[0].isEmpty(), "key has empty study ID");
        Preconditions.checkArgument(!parts[1].isEmpty(), "key has empty schema ID");

        this.studyId = parts[0];
        this.schemaId = parts[1];
    }

    /**
     * The map-based getter and setter supports DynamoDB persistence and the return of a JSON object/map in the API. In
     * the Java interface for Schema, convenience methods to get/put values for an OS are exposed and the map is not
     * directly accessible.
     */
    @DynamoDBAttribute
    public Map<String, Integer> getMaxAppVersions() {
        return ImmutableMap.copyOf(maxAppVersions);
    }

    /** @see #getMaxAppVersions */
    public void setMaxAppVersions(Map<String, Integer> maxAppVersions) {
        this.maxAppVersions = (maxAppVersions == null) ? new HashMap<>() :
                BridgeUtils.withoutNullEntries(maxAppVersions);
    }

    /** {@inheritDoc} */
    @DynamoDBIgnore
    @JsonIgnore
    @Override
    public Integer getMaxAppVersion(String osName) {
        return maxAppVersions.get(osName);
    }

    /** {@inheritDoc} */
    @DynamoDBIgnore
    @JsonIgnore
    @Override
    public void setMaxAppVersion(String osName, Integer maxAppVersion) {
        BridgeUtils.putOrRemove(maxAppVersions, osName, maxAppVersion);
    }

    /**
     * The map-based getter and setter supports DynamoDB persistence and the return of a JSON object/map in the API. In
     * the Java interface for Schema, convenience methods to get/put values for an OS are exposed and the map is not
     * directly accessible.
     */
    @DynamoDBAttribute
    public Map<String, Integer> getMinAppVersions() {
        return ImmutableMap.copyOf(minAppVersions);
    }

    /** @see #getMaxAppVersions */
    public void setMinAppVersions(Map<String, Integer> minAppVersions) {
        this.minAppVersions = (minAppVersions == null) ? new HashMap<>() :
                BridgeUtils.withoutNullEntries(minAppVersions);
    }

    /** {@inheritDoc} */
    @DynamoDBIgnore
    @JsonIgnore
    @Override
    public Integer getMinAppVersion(String osName) {
        return minAppVersions.get(osName);
    }

    /** {@inheritDoc} */
    @DynamoDBIgnore
    @JsonIgnore
    @Override
    public void setMinAppVersion(String osName, Integer minAppVersion) {
        BridgeUtils.putOrRemove(minAppVersions, osName, minAppVersion);
    }

    /** {@inheritDoc} */
    @Override
    public String getModuleId() {
        return moduleId;
    }

    /** {@inheritDoc} */
    @Override
    public void setModuleId(String moduleId) {
        this.moduleId = moduleId;
    }

    /** {@inheritDoc} */
    @Override
    public Integer getModuleVersion() {
        return moduleVersion;
    }

    /** {@inheritDoc} */
    @Override
    public void setModuleVersion(Integer moduleVersion) {
        this.moduleVersion = moduleVersion;
    }

    /** {@inheritDoc} */
    @Override
    public String getName() {
        return name;
    }

    /** @see org.sagebionetworks.bridge.models.upload.UploadSchema#getName */
    @Override
    public void setName(String name) {
        this.name = name;
    }

    /** {@inheritDoc} */
    // This is separate from the DynamoDBVersionAttribute. Different schema revisions can co-exist. They correspond to
    // different data versions and different schema tables. Schema revisions themselves can be modified, so they have
    // versions in DDB to support concurrent modification detection and optimistic locking.
    @DynamoDBRangeKey
    @Override
    public int getRevision() {
        return rev;
    }

    /** @see org.sagebionetworks.bridge.models.upload.UploadSchema#getRevision */
    @Override
    public void setRevision(int rev) {
        this.rev = rev;
    }

    /** {@inheritDoc} */
    @DynamoDBIgnore
    @Override
    public String getSchemaId() {
        return schemaId;
    }

    /** @see org.sagebionetworks.bridge.models.upload.UploadSchema#getSchemaId */
    @Override
    public void setSchemaId(String schemaId) {
        this.schemaId = schemaId;
    }

    /** {@inheritDoc} */
    @DynamoDBIgnore
    @JsonIgnore
    @Override
    public UploadSchemaKey getSchemaKey() {
        return new UploadSchemaKey.Builder().withStudyId(studyId).withSchemaId(schemaId).withRevision(rev).build();
    }

    /** {@inheritDoc} */
    @DynamoDBTypeConverted(converter=EnumMarshaller.class)
    @Override
    public UploadSchemaType getSchemaType() {
        return schemaType;
    }

    /** @see org.sagebionetworks.bridge.models.upload.UploadSchema#getSchemaType */
    @Override
    public void setSchemaType(UploadSchemaType schemaType) {
        this.schemaType = schemaType;
    }

    /** {@inheritDoc} */
    @Override
    public String getSurveyGuid() {
        return surveyGuid;
    }

    /** @see #getSurveyGuid */
    @Override
    public void setSurveyGuid(String surveyGuid) {
        this.surveyGuid = surveyGuid;
    }

    /** {@inheritDoc} */
    @JsonSerialize(using = DateTimeToLongSerializer.class)
    @Override
    public Long getSurveyCreatedOn() {
        return surveyCreatedOn;
    }

    /** @see #getSurveyCreatedOn */
    @JsonDeserialize(using = DateTimeToPrimitiveLongDeserializer.class)
    @Override
    public void setSurveyCreatedOn(Long surveyCreatedOn) {
        this.surveyCreatedOn = surveyCreatedOn;
    }

    /**
     * <p>
     * The ID of the study that this schema lives in. This is not exposed to the callers of the upload schema API, but
     * is needed internally to create a secondary index on the study. This index is needed by:
     *   <ul>
     *     <li>the exporter will want all schemas for a particular study to match a particular upload</li>
     *     <li>researchers may want to list all schemas in their study for schema management</li>
     *   </ul>
     * </p>
     */
    @DynamoDBIndexHashKey(attributeName = "studyId", globalSecondaryIndexName = "studyId-index")
    @Override
    public String getStudyId() {
        return studyId;
    }

    /** @see #getStudyId */
    @Override
    public void setStudyId(String studyId) {
        this.studyId = studyId;
    }

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
    @DynamoDBVersionAttribute
    public Long getVersion() {
        return version;
    }

    /** @see #getVersion */
    public void setVersion(Long version) {
        this.version = version;
    }
    
    @DynamoDBAttribute
    public boolean isDeleted() {
        return deleted;
    }
    
    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    /** Custom DynamoDB marshaller for the field definition list. This uses Jackson to convert to and from JSON. */
    public static class FieldDefinitionListMarshaller extends ListMarshaller<UploadFieldDefinition> {
        private static final TypeReference<List<UploadFieldDefinition>> FIELD_LIST_TYPE =
                new TypeReference<List<UploadFieldDefinition>>() {};

        /** {@inheritDoc} */
        @Override
        public TypeReference<List<UploadFieldDefinition>> getTypeReference() {
            return FIELD_LIST_TYPE;
        }
    }
}
