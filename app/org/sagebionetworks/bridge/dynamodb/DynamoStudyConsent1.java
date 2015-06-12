package org.sagebionetworks.bridge.dynamodb;

import java.util.Objects;

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
public final class DynamoStudyConsent1 implements StudyConsent {

    private String studyKey;
    private long createdOn;
    private boolean active;
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
    @JsonIgnore
    public String getStoragePath() {
        return storagePath;
    }
    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    @DynamoDBVersionAttribute
    @JsonIgnore
    public Long getVersion() {
        return version;
    }
    public void setVersion(Long version) {
        this.version = version;
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Objects.hash(active);
        result = prime * result + Objects.hash(createdOn);
        result = prime * result + Objects.hash(storagePath);
        result = prime * result + Objects.hash(studyKey);
        result = prime * result + Objects.hash(version);
        return result;
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        DynamoStudyConsent1 other = (DynamoStudyConsent1) obj;
        return (Objects.equals(active, other.active) && Objects.equals(createdOn, other.createdOn) && 
                Objects.equals(storagePath, other.storagePath) &&
                Objects.equals(studyKey, other.studyKey) && Objects.equals(version, other.version));
    }
    
    @Override
    public String toString() {
        return String.format("DynamoStudyConsent1 [studyKey=%s, createdOn=%s, active=%s, storagePath=%s, version=%s]",
            studyKey, createdOn, active, storagePath, version);
    }
}
