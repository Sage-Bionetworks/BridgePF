package org.sagebionetworks.bridge.dynamodb;

import org.sagebionetworks.bridge.models.Date;
import org.sagebionetworks.bridge.models.StudyConsent;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;
import com.fasterxml.jackson.annotation.JsonIgnore;

@DynamoDBTable(tableName = "StudyConsent1")
public class DynamoStudyConsent1 implements StudyConsent, DynamoTable {

    private String studyKey;
    private long createdOn;
    private boolean active;
    private String path;
    private int minAge;
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
    @JsonIgnore
    public long getCreatedOn() {
        return createdOn;
    }
    public void setCreatedOn(long timestamp) {
        this.createdOn = timestamp;
    }

    public String getTimestamp() {
        return new Date(createdOn).getISODateTime();
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
    public int getMinAge() {
        return minAge;
    }
    public void setMinAge(int minAge) {
        this.minAge = minAge;
    }

    @DynamoDBVersionAttribute
    public Long getVersion() {
        return version;
    }
    public void setVersion(Long version) {
        this.version = version;
    }
}
