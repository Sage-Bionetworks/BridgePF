package org.sagebionetworks.bridge.dynamodb;

import org.sagebionetworks.bridge.models.notifications.NotificationTopic;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class DynamoNotificationTopic implements NotificationTopic {

    private String guid;
    private String studyId;
    private String name;
    private String topicARN;
    
    @DynamoDBHashKey
    @Override
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
    @JsonIgnore
    public String getTopicARN() {
        return topicARN;
    }
    @Override
    public void setTopicARN(String topicARN) {
        this.topicARN = topicARN;
    }

}
