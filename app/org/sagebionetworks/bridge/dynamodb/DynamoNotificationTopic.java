package org.sagebionetworks.bridge.dynamodb;

import org.sagebionetworks.bridge.json.DateTimeToLongDeserializer;
import org.sagebionetworks.bridge.json.DateTimeToLongSerializer;
import org.sagebionetworks.bridge.models.notifications.NotificationTopic;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@DynamoDBTable(tableName = "NotificationTopic")
public class DynamoNotificationTopic implements NotificationTopic {

    private String guid;
    private String studyId;
    private String name;
    private String description;
    private String topicARN;
    private long createdOn;
    private long modifiedOn;
    
    @DynamoDBHashKey
    @Override
    @JsonIgnore
    public String getStudyId() {
        return studyId;
    }
    @Override
    public void setStudyId(String studyId) {
        this.studyId = studyId;
    }
    @DynamoDBRangeKey
    @Override
    public String getGuid() { 
        return guid;
    }
    @Override
    public void setGuid(String guid) {
        this.guid = guid;
    }
    @Override
    public String getName() {
        return name;
    }
    @Override
    public void setName(String name) {
        this.name = name;
    }
    @Override
    public String getDescription() {
        return description;
    }
    @Override
    public void setDescription(String description) {
        this.description = description;
    }
    @Override
    @JsonIgnore
    public String getTopicARN() {
        return topicARN;
    }
    @Override
    public void setTopicARN(String topicARN) {
        this.topicARN = topicARN;
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
}
