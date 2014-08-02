package org.sagebionetworks.bridge.dynamodb;

import org.sagebionetworks.bridge.models.StudyConsent;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;

@DynamoDBTable(tableName = "StudyConsent")
public class DynamoStudyConsent implements StudyConsent, DynamoTable {

    private String studyKey;
    private long timestamp;
    private boolean active;
    private String path;
    private int minAge;
    private Long version;

    @Override
    @DynamoDBHashKey
    public String getStudyKey() {
        return studyKey;
    }
    public void setStudyKey(String studyKey) {
        this.studyKey = studyKey;
    }

    @Override
    @DynamoDBRangeKey
    public long getTimestamp() {
        return timestamp;
    }
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
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
