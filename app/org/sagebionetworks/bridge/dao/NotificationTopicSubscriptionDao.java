package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;
import org.sagebionetworks.bridge.models.notifications.NotificationTopic;
import org.sagebionetworks.bridge.models.notifications.NotificationTopicSubscription;

public interface NotificationTopicSubscriptionDao {

    /**
     * Get a user's subscriptions.
     */
    List<NotificationTopicSubscription> listSubscriptions(String healthCode);
    
    /**
     * Subscribe to a notification topic.
     */
    NotificationTopicSubscription subscribe(String healthCode, NotificationRegistration registration, NotificationTopic topic);
    
    /**
     * Unsubscribe from a notification topic.
     */
    void unsubscribe(String healthCode, NotificationRegistration registration, NotificationTopic topic);
    
}
