package org.sagebionetworks.bridge.dynamodb;

import org.sagebionetworks.bridge.json.DateTimeJsonDeserializer;
import org.sagebionetworks.bridge.json.DateTimeJsonSerializer;
import org.sagebionetworks.bridge.models.studies.StudyConsent;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@DynamoDBTable(tableName = "StudyConsent1")
public class DynamoStudyConsent1 implements StudyConsent {

    private String studyKey;
    private long createdOn;
    private boolean active;
    private String path;
    private String storagePath;
    private Long version;

    @Override
    @DynamoDBHashKey
    @JsonIgnore
    public String getStudyKey() {
        return studyKey;
    }
    public void setStudyKey(String studyKey) {
        this.studyKey = studyKey;
    }

    @Override
    @DynamoDBRangeKey
    @JsonSerialize(using = DateTimeJsonSerializer.class)
    public long getCreatedOn() {
        return createdOn;
    }
    @JsonDeserialize(using = DateTimeJsonDeserializer.class)
    public void setCreatedOn(long timestamp) {
        this.createdOn = timestamp;
    }
    
    @Override
    @DynamoDBAttribute
    public boolean getActive() {
        return active;
    }
    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    @DynamoDBAttribute
    public String getPath() {
        return path;
    }
    public void setPath(String path) {
        this.path = path;
    }

    @Override
    @DynamoDBAttribute
    public String getStoragePath() {
        return storagePath;
    }
    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }
    
    @DynamoDBVersionAttribute
    public Long getVersion() {
        return version;
    }
    public void setVersion(Long version) {
        this.version = version;
    }
    
    @Override
    public String toString() {
        return String.format("DynamoStudyConsent1 [studyKey=%s, createdOn=%s, active=%s, path=%s, storagePath=%s, version=%s]",
            studyKey, createdOn, active, path, storagePath, version);
    }
}
