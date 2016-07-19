package org.sagebionetworks.bridge.dynamodb;

import java.util.Objects;

import org.sagebionetworks.bridge.json.DateTimeToPrimitiveLongDeserializer;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsent;
import org.sagebionetworks.bridge.json.DateTimeToLongSerializer;

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

    private String subpopGuid;
    private long createdOn;
    private String storagePath;
    private Long version;

    @Override
    @DynamoDBHashKey(attributeName="studyKey")
    public String getSubpopulationGuid() {
        return subpopGuid;
    }
    public void setSubpopulationGuid(String subpopGuid) {
        this.subpopGuid = subpopGuid;
    }

    @Override
    @DynamoDBRangeKey
    @JsonSerialize(using = DateTimeToLongSerializer.class)
    public long getCreatedOn() {
        return createdOn;
    }
    @JsonDeserialize(using = DateTimeToPrimitiveLongDeserializer.class)
    public void setCreatedOn(long timestamp) {
        this.createdOn = timestamp;
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
        return Objects.hash(createdOn, storagePath, subpopGuid, version);
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        DynamoStudyConsent1 other = (DynamoStudyConsent1) obj;
        return (Objects.equals(createdOn, other.createdOn) && Objects.equals(storagePath, other.storagePath) 
                && Objects.equals(subpopGuid, other.subpopGuid) && Objects.equals(version, other.version));
    }
    
    @Override
    public String toString() {
        return String.format("DynamoStudyConsent1 [subpopGuid=%s, createdOn=%s, storagePath=%s, version=%s]",
            subpopGuid, createdOn, storagePath, version);
    }
}
