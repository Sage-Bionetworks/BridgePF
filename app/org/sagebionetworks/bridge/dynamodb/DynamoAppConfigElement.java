package org.sagebionetworks.bridge.dynamodb;

import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.json.DateTimeToLongDeserializer;
import org.sagebionetworks.bridge.json.DateTimeToLongSerializer;
import org.sagebionetworks.bridge.models.appconfig.AppConfigElement;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperFieldModel.DynamoDBAttributeType;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTyped;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@DynamoDBTable(tableName = "AppConfigElement")
@BridgeTypeName("AppConfigElement")
public final class DynamoAppConfigElement implements AppConfigElement {
    private String id;
    private String studyId;
    private Long revision;
    private boolean deleted;
    private JsonNode data;
    private long createdOn;
    private long modifiedOn;
    private Long version;
    
    @DynamoDBHashKey
    @JsonIgnore
    public String getKey() {
        if (StringUtils.isBlank(studyId) || StringUtils.isBlank(id)) {
            return null;
        }
        return String.format("%s:%s", studyId, id);
    }
    public void setKey(String key) {
        this.studyId = null;
        this.id = null;
        if (key != null) {
            String[] components = key.split(":", 2);
            if (components.length == 2) {
                this.studyId = components[0];
                this.id = components[1];
            }
        }
    }
    @DynamoDBRangeKey
    public Long getRevision() {
        return revision;
    }
    public void setRevision(Long revision) {
        this.revision = revision;
    }
    @DynamoDBIndexHashKey(attributeName = "studyId", globalSecondaryIndexName = "studyId-index")
    @JsonIgnore
    public String getStudyId() {
        return studyId;
    }
    public void setStudyId(String studyId) {
        this.studyId = studyId;
    }
    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }
    @DynamoDBTyped(DynamoDBAttributeType.BOOL)
    public boolean isDeleted() {
        return deleted;
    }
    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
    @DynamoDBTypeConverted(converter = JsonNodeMarshaller.class)
    public JsonNode getData() {
        return data;
    }
    @DynamoDBTypeConverted(converter = JsonNodeMarshaller.class)
    public void setData(JsonNode data) {
        this.data = data;
    }
    @JsonSerialize(using = DateTimeToLongSerializer.class)
    public long getCreatedOn() {
        return createdOn;
    }
    @JsonDeserialize(using = DateTimeToLongDeserializer.class)
    public void setCreatedOn(long createdOn) {
        this.createdOn = createdOn;
    }
    @JsonSerialize(using = DateTimeToLongSerializer.class)
    public long getModifiedOn() {
        return modifiedOn;
    }
    @JsonDeserialize(using = DateTimeToLongDeserializer.class)
    public void setModifiedOn(long modifiedOn) {
        this.modifiedOn = modifiedOn;
    }
    @DynamoDBVersionAttribute
    public Long getVersion() {
        return version;
    }
    public void setVersion(Long version) {
        this.version = version;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(studyId, id, revision, deleted, data, createdOn, modifiedOn, version);
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        DynamoAppConfigElement other = (DynamoAppConfigElement) obj;
        return Objects.equals(studyId, other.studyId) &&
                Objects.equals(id, other.id) &&
                Objects.equals(revision, other.revision) &&
                Objects.equals(deleted, other.deleted) &&
                Objects.equals(data, other.data) &&
                Objects.equals(createdOn, other.createdOn) &&
                Objects.equals(modifiedOn, other.modifiedOn) &&
                Objects.equals(version, other.version);
    }
}