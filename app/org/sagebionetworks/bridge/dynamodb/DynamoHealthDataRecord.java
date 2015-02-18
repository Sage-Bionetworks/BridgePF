package org.sagebionetworks.bridge.dynamodb;

import org.sagebionetworks.bridge.json.DateTimeJsonDeserializer;
import org.sagebionetworks.bridge.json.DateTimeJsonSerializer;
import org.sagebionetworks.bridge.json.LocalDateToStringSerializer;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordBuilder;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMarshalling;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.joda.time.LocalDate;

/** DynamoDB implementation of {@link org.sagebionetworks.bridge.models.healthdata.HealthDataRecord}. */
@DynamoDBTable(tableName = "HealthDataRecord3")
public class DynamoHealthDataRecord implements HealthDataRecord {
    private Long createdOn;
    private JsonNode data;
    private String healthCode;
    private String id;
    private JsonNode metadata;
    private String schemaId;
    private LocalDate uploadDate;
    private Long version;

    /** {@inheritDoc} */
    @JsonSerialize(using = DateTimeJsonSerializer.class)
    @Override
    public Long getCreatedOn() {
        return createdOn;
    }

    /** @see #getCreatedOn */
    @JsonDeserialize(using = DateTimeJsonDeserializer.class)
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

    /**
     * DynamoDB version, used for optimistic locking. This is used internally by Bridge and by DynamoDB and is not
     * exposed to users.
     */
    @DynamoDBVersionAttribute
    @JsonIgnore
    public Long getVersion() {
        return version;
    }

    /** @see #getVersion */
    @JsonIgnore
    public void setVersion(Long version) {
        this.version = version;
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
            record.setUploadDate(getUploadDate());
            return record;
        }
    }
}
