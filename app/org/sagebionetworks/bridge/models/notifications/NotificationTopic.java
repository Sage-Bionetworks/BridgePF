package org.sagebionetworks.bridge.models.notifications;

import org.sagebionetworks.bridge.dynamodb.DynamoNotificationTopic;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.models.Criteria;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@BridgeTypeName("NotificationTopic")
@JsonDeserialize(as=DynamoNotificationTopic.class)
public interface NotificationTopic extends BridgeEntity {

    static NotificationTopic create() {
        return new DynamoNotificationTopic();
    }
    
    /**
     * The guid primary key for identifying this topic.
     */
    String getGuid();
    
    /** @see #getGuid */
    void setGuid(String guid);
    
    /**
     * The study the topic belongs to.
     */
    String getStudyId();
    
    /** @see #getStudyId */
    void setStudyId(String studyId);
    
    /**
     * The name of this topic (visible to study developers and researchers to identify 
     * the purpose of the topic.
     */
    String getName();
    
    /** @see #getName */
    void setName(String name);

    /**
     * Short name for the topic, used as the display name for the topic in SNS. This also appears in SMS
     * notifications. Must be 10 characters or less.
     */
    String getShortName();

    /** @see #getShortName */
    void setShortName(String shortName);

    /**
     * Description text for this topic, if needed.
     */
    String getDescription();
    
    /** @see #getDescription */
    void setDescription(String description);
    
    /**
     * The Bridge topic is mapped to an SNS topic, the foreign key for which is an Amazon 
     * Resource Name (ARN). Messages are sent to the SNS topic to deliver them in bulk to 
     * study participants.
     */
    String getTopicARN();
    
    /** @see #getTopicARN */
    void setTopicARN(String topicARN);
    
    /**
     * The date and time this topic was created.
     */
    long getCreatedOn();
    
    /** @see #getCreatedOn */
    void setCreatedOn(long createdOn);

    /**
     * The date and time this topic was last modified.
     */
    long getModifiedOn();
    
    /** @see #getModifiedOn */
    void setModifiedOn(long modifiedOn);

    /**
     * If a topic has criteria, users can be automatically subscribed and unsubscribed when their criteria context
     * changes.
     */
    Criteria getCriteria();

    /** @see #getCriteria */
    void setCriteria(Criteria criteria);
    
    /**
     * Is this topic deleted? It will not be returned through the API unless deleted items are included.
     */
    boolean isDeleted();
    
    /** @see #isDeleted */
    void setDeleted(boolean deleted);
}
