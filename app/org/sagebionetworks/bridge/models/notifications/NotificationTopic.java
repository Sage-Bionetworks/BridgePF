package org.sagebionetworks.bridge.models.notifications;

import org.sagebionetworks.bridge.models.BridgeEntity;

public interface NotificationTopic extends BridgeEntity {

    public static NotificationTopic create() {
        return null;
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
