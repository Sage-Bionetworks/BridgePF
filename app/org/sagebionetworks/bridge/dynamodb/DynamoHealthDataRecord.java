package org.sagebionetworks.bridge.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexRangeKey;
import com.fasterxml.jackson.annotation.JsonFilter;

import java.util.Set;

import org.joda.time.LocalDate;
import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.json.DateTimeToLongDeserializer;
import org.sagebionetworks.bridge.json.DateTimeToLongSerializer;
import org.sagebionetworks.bridge.json.LocalDateToStringSerializer;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordBuilder;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMarshalling;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.joda.deser.LocalDateDeserializer;

/** DynamoDB implementation of {@link org.sagebionetworks.bridge.models.healthdata.HealthDataRecord}. */
@DynamoThroughput(readCapacity=43, writeCapacity=10)
@DynamoDBTable(tableName = "HealthDataRecord3")
@JsonFilter("filter")
public class DynamoHealthDataRecord implements HealthDataRecord {
    private Long createdOn;
    private JsonNode data;
    private String healthCode;
    private String id;
    private JsonNode metadata;
    private String schemaId;
    private int schemaRevision;
    private String studyId;
    private LocalDate uploadDate;
    private String uploadId;
    private Long uploadedOn;
    private ParticipantOption.SharingScope userSharingScope;
    private String userExternalId;
    private Set<String> userDataGroups;
    private Long version;
    private ExporterStatus synapseExporterStatus;

    /** {@inheritDoc} */
    @JsonSerialize(using = DateTimeToLongSerializer.class)
    @Override
    public Long getCreatedOn() {
        return createdOn;
    }

    /** @see #getCreatedOn */
    @JsonDeserialize(using = DateTimeToLongDeserializer.class)
    public void setCreatedOn(Long createdOn) {
        this.createdOn = createdOn;
    }

    /** {@inheritDoc} */
    @DynamoDBMarshalling(marshallerClass = JsonNodeMarshaller.class)
    @Override
    public JsonNode getData() {
        return data;
    }

    /** @see #getData */
    public void setData(JsonNode data) {
        this.data = data;
    }

    /** {@inheritDoc} */
    @DynamoDBIndexHashKey(attributeName = "healthCode", globalSecondaryIndexName = "healthCode-index")
    @Override
    public String getHealthCode() {
        return healthCode;
    }

    /** @see #getHealthCode */
    public void setHealthCode(String healthCode) {
        this.healthCode = healthCode;
    }

    /** {@inheritDoc} */
    @DynamoDBHashKey
    @Override
    public String getId() {
        return id;
    }

    /** @see #getId */
    public void setId(String id) {
        this.id = id;
    }

    /** {@inheritDoc} */
    @DynamoDBMarshalling(marshallerClass = JsonNodeMarshaller.class)
    @Override
    public JsonNode getMetadata() {
        return metadata;
    }

    /** @see #getMetadata */
    public void setMetadata(JsonNode metadata) {
        this.metadata = metadata;
    }

    /** {@inheritDoc} */
    @Override
    public String getSchemaId() {
        return schemaId;
    }

    /** @see #getSchemaId */
    public void setSchemaId(String schemaId) {
        this.schemaId = schemaId;
    }

    /** {@inheritDoc} */
    @Override
    public int getSchemaRevision() {
        return schemaRevision;
    }

    /** @see #getSchemaRevision */
    public void setSchemaRevision(int schemaRevision) {
        this.schemaRevision = schemaRevision;
    }

    /** {@inheritDoc} */
    @DynamoDBIndexHashKey(attributeName = "studyId", globalSecondaryIndexName = "study-uploadedOn-index")
    @Override
    public String getStudyId() {
        return studyId;
    }

    /** @see #getStudyId */
    public void setStudyId(String studyId) {
        this.studyId = studyId;
    }

    /** {@inheritDoc} */
    @DynamoDBIndexHashKey(attributeName = "uploadDate", globalSecondaryIndexName = "uploadDate-index")
    @DynamoDBMarshalling(marshallerClass = LocalDateMarshaller.class)
    @JsonSerialize(using = LocalDateToStringSerializer.class)
    @Override
    public LocalDate getUploadDate() {
        return uploadDate;
    }

    /** @see #getUploadDate */
    @JsonDeserialize(using = LocalDateDeserializer.class)
    public void setUploadDate(LocalDate uploadDate) {
        this.uploadDate = uploadDate;
    }

    /** {@inheritDoc} */
    @Override
    public String getUploadId() {
        return uploadId;
    }

    /** @see #getUploadId */
    public void setUploadId(String uploadId) {
        this.uploadId = uploadId;
    }

    /** {@inheritDoc} */
    @DynamoDBIndexRangeKey(attributeName = "uploadedOn", globalSecondaryIndexName = "study-uploadedOn-index")
    @JsonSerialize(using = DateTimeToLongSerializer.class)
    @Override
    public Long getUploadedOn() {
        return uploadedOn;
    }

    /** @see #getUploadedOn */
    @JsonDeserialize(using = DateTimeToLongDeserializer.class)
    public void setUploadedOn(Long uploadedOn) {
        this.uploadedOn = uploadedOn;
    }

    /** {@inheritDoc} */
    @DynamoDBMarshalling(marshallerClass = EnumMarshaller.class)
    @Override
    public ParticipantOption.SharingScope getUserSharingScope() {
        return userSharingScope;
    }

    /** @see #getUserSharingScope */
    public void setUserSharingScope(ParticipantOption.SharingScope userSharingScope) {
        this.userSharingScope = userSharingScope;
    }
    
    /** {@inheritDoc} */
    @Override
    public String getUserExternalId() {
        return userExternalId;
    }

    /** @see #getUserExternalId */
    public void setUserExternalId(String userExternalId) {
        this.userExternalId = userExternalId;
    }

    /** {@inheritDoc} */
    @Override
    public Set<String> getUserDataGroups() {
        return userDataGroups;
    }
    
    /** @see #getUserDataGroups() */
    public void setUserDataGroups(Set<String> userDataGroups) {
        // DDB doesn't support empty sets, use null reference for empty set. This is also enforced by the builder.
        this.userDataGroups = (userDataGroups != null && !userDataGroups.isEmpty()) ? userDataGroups : null;
    }
    
    /** {@inheritDoc} */
    @DynamoDBVersionAttribute
    public Long getVersion() {
        return version;
    }

    /** @see #getVersion */
    public void setVersion(Long version) {
        this.version = version;
    }

    /** {@inheritDoc} */
    @DynamoDBMarshalling(marshallerClass = EnumMarshaller.class)
    public ExporterStatus getSynapseExporterStatus() {
        return synapseExporterStatus;
    }

    /** @see #getSynapseExporterStatus */
    public void setSynapseExporterStatus(ExporterStatus synapseExporterStatus) {
        this.synapseExporterStatus = synapseExporterStatus;
    }

    /** DynamoDB implementation of {@link org.sagebionetworks.bridge.models.healthdata.HealthDataRecordBuilder}. */
    public static class Builder extends HealthDataRecordBuilder {
        /** {@inheritDoc} */
        @Override
        protected HealthDataRecord buildUnvalidated() {
            DynamoHealthDataRecord record = new DynamoHealthDataRecord();
            record.setCreatedOn(getCreatedOn());
            record.setData(getData());
            record.setHealthCode(getHealthCode());
            record.setId(getId());
            record.setMetadata(getMetadata());
            record.setSchemaId(getSchemaId());
            record.setSchemaRevision(getSchemaRevision());
            record.setStudyId(getStudyId());
            record.setUploadDate(getUploadDate());
            record.setUploadId(getUploadId());
            record.setUploadedOn(getUploadedOn());
            record.setUserSharingScope(getUserSharingScope());
            record.setUserExternalId(getUserExternalId());
            record.setUserDataGroups(getUserDataGroups());
            record.setVersion(getVersion());
            record.setSynapseExporterStatus(getSynapseExporterStatus());
            return record;
        }
    }
}
