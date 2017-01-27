package org.sagebionetworks.bridge.models.notifications;

import org.sagebionetworks.bridge.dynamodb.DynamoNotificationTopic;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@BridgeTypeName("NotificationTopic")
@JsonDeserialize(as=DynamoNotificationTopic.class)
public interface NotificationTopic extends BridgeEntity {

    static NotificationTopic create() {
        return new DynamoNotificationTopic();
    }
    
    String getGuid();
    void setGuid(String guid);
    
    String getStudyId();
    void setStudyId(String studyId);
    
    String getName();
    void setName(String name);
    
    String getDescription();
    void setDescription(String description);
    
    String getTopicARN();
    void setTopicARN(String topicARN);
    
    long getCreatedOn();
    void setCreatedOn(long createdOn);

    long getModifiedOn();
    void setModifiedOn(long modifiedOn);
}
