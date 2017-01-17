package org.sagebionetworks.bridge.models.notifications;

import org.sagebionetworks.bridge.dynamodb.DynamoNotificationTopic;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@BridgeTypeName("NotificationTopic")
@JsonDeserialize(as=DynamoNotificationTopic.class)
public interface NotificationTopic extends BridgeEntity {

    public static NotificationTopic create() {
        return new DynamoNotificationTopic();
    }
    
    public String getGuid();
    public void setGuid(String guid);
    
    public String getStudyId();
    public void setStudyId(String studyId);
    
    public String getName();
    public void setName(String name);
    
    public String getTopicARN();
    public void setTopicARN(String topicARN);
    
}
