package org.sagebionetworks.bridge.dynamodb;

import java.io.IOException;
import java.util.List;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMappingException;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMarshaller;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMarshalling;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.DateTimeToLongSerializer;
import org.sagebionetworks.bridge.json.DateTimeToPrimitiveLongDeserializer;
import org.sagebionetworks.bridge.json.JsonUtils;
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
public class DynamoUploadSchema implements UploadSchema {
    private List<UploadFieldDefinition> fieldDefList;
    private String name;
    private int rev;
    private String schemaId;
    private UploadSchemaType schemaType;
    private String surveyGuid;
    private Long surveyCreatedOn;
    private String studyId;
    private Long version;
    
    /** {@inheritDoc} */
    @DynamoDBMarshalling(marshallerClass = FieldDefinitionListMarshaller.class)
    @Override
    public List<UploadFieldDefinition> getFieldDefinitions() {
        return fieldDefList;
    }

    /** @see org.sagebionetworks.bridge.models.upload.UploadSchema#getFieldDefinitions */
    public void setFieldDefinitions(List<UploadFieldDefinition> fieldDefList) {
        this.fieldDefList = ImmutableList.copyOf(fieldDefList);
    }

    /**
     * This is the DynamoDB key. It is used by the DynamoDB mapper. This should not be used directly. The key format is
     * "[studyID]:[schemaID]". The schema ID may contain colons. The study ID may not. Since the key is created
     * from the study ID and schema ID, this will throw an InvalidEntityException if either one is blank.
     */
    @DynamoDBHashKey
    @JsonIgnore
    public String getKey() throws InvalidEntityException {
        if (Strings.isNullOrEmpty(studyId)) {
            throw new InvalidEntityException(this, String.format(Validate.CANNOT_BE_BLANK, "studyId"));
        }
        if (Strings.isNullOrEmpty(schemaId)) {
            throw new InvalidEntityException(this, String.format(Validate.CANNOT_BE_BLANK, "schemaId"));
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

    /** {@inheritDoc} */
    @Override
    public String getName() {
        return name;
    }

    /** @see org.sagebionetworks.bridge.models.upload.UploadSchema#getName */
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
    @DynamoDBMarshalling(marshallerClass = EnumMarshaller.class)
    @Override
    public UploadSchemaType getSchemaType() {
        return schemaType;
    }

    /** @see org.sagebionetworks.bridge.models.upload.UploadSchema#getSchemaType */
    public void setSchemaType(UploadSchemaType schemaType) {
        this.schemaType = schemaType;
    }

    /** {@inheritDoc} */
    @Override
    public String getSurveyGuid() {
        return surveyGuid;
    }

    /** @see #getSurveyGuid */
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
    @JsonIgnore
    @Override
    public String getStudyId() {
        return studyId;
    }

    /** @see #getStudyId */
    @JsonIgnore
    public void setStudyId(String studyId) {
        this.studyId = studyId;
    }

    /** {@inheritDoc} */
    @DynamoDBVersionAttribute
    @Override
    public Long getVersion() {
        return version;
    }

    /** @see #getVersion */
    public void setVersion(Long version) {
        this.version = version;
    }

    /** Custom DynamoDB marshaller for the field definition list. This uses Jackson to convert to and from JSON. */
    public static class FieldDefinitionListMarshaller implements DynamoDBMarshaller<List<UploadFieldDefinition>> {
        /** {@inheritDoc} */
        @Override
        public String marshall(List<UploadFieldDefinition> fieldDefList) {
            try {
                return JsonUtils.INTERNAL_OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(
                        fieldDefList);
            } catch (JsonProcessingException ex) {
                throw new DynamoDBMappingException(ex);
            }
        }

        /** {@inheritDoc} */
        @Override
        public List<UploadFieldDefinition> unmarshall(Class<List<UploadFieldDefinition>> clazz, String json) {
            try {
                return JsonUtils.INTERNAL_OBJECT_MAPPER.readValue(json,
                        new TypeReference<List<UploadFieldDefinition>>() {});
            } catch (IOException ex) {
                throw new DynamoDBMappingException(ex);
            }
        }
    }
}
