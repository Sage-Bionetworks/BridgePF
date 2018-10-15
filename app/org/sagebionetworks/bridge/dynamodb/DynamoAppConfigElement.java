package org.sagebionetworks.bridge.dynamodb;

import java.util.Objects;

import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.json.DateTimeToLongDeserializer;
import org.sagebionetworks.bridge.json.DateTimeToLongSerializer;
import org.sagebionetworks.bridge.models.appconfig.AppConfigElement;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@DynamoDBTable(tableName = "AppConfigElement")
@BridgeTypeName("AppConfigElement")
public final class DynamoAppConfigElement implements AppConfigElement {
    private String key;
    private String id;
    private Long revision;
    private boolean published;
    private boolean deleted;
    private JsonNode data;
    private long createdOn;
    private long modifiedOn;
    
    @DynamoDBHashKey
    @JsonIgnore
    public String getKey() {
        return key;
    }
    public void setKey(String key) {
        this.key = key;
    }
    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }
    @DynamoDBVersionAttribute
    public Long getRevision() {
        return revision;
    }
    public void setRevision(Long revision) {
        this.revision = revision;
    }
    @Override
    public boolean isPublished() {
        return published;
    }
    @Override
    public void setPublished(boolean published) {
        this.published = published;
    }
    @Override
    public boolean isDeleted() {
        return deleted;
    }
    @Override
    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
    @DynamoDBTypeConverted(converter = JsonNodeMarshaller.class)
    @Override
    public JsonNode getData() {
        return data;
    }
    @DynamoDBTypeConverted(converter = JsonNodeMarshaller.class)
    @Override
    public void setData(JsonNode data) {
        this.data = data;
    }
    @JsonSerialize(using = DateTimeToLongSerializer.class)
    @Override
    public long getCreatedOn() {
        return createdOn;
    }
    @JsonDeserialize(using = DateTimeToLongDeserializer.class)
    @Override
    public void setCreatedOn(long createdOn) {
        this.createdOn = createdOn;
    }
    @JsonSerialize(using = DateTimeToLongSerializer.class)
    @Override
    public long getModifiedOn() {
        return modifiedOn;
    }
    @JsonDeserialize(using = DateTimeToLongDeserializer.class)
    @Override
    public void setModifiedOn(long modifiedOn) {
        this.modifiedOn = modifiedOn;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(createdOn, data, deleted, id, key, modifiedOn, published, revision);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        DynamoAppConfigElement other = (DynamoAppConfigElement) obj;
        return Objects.equals(deleted, other.deleted) &&
                Objects.equals(published, other.published) &&
                Objects.equals(modifiedOn, other.modifiedOn) &&
                Objects.equals(createdOn, other.createdOn) &&
                Objects.equals(id, other.id) &&
                Objects.equals(revision, other.revision) &&
                Objects.equals(key, other.key) &&
                Objects.equals(data, other.data);
    }
}
