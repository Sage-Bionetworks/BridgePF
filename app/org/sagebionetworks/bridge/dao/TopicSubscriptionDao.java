package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;
import org.sagebionetworks.bridge.models.notifications.NotificationTopic;
import org.sagebionetworks.bridge.models.notifications.TopicSubscription;

/**
 * The TopicSubscriptionDao attempts to keep SNS and DynamoDB records in sync; there is also 
 * integrity checking of the records in the TopicSubscriptionService.
 */
public interface TopicSubscriptionDao {

    /**
     * Get a user's subscriptions for a specific device registration.
     */
    List<? extends TopicSubscription> listSubscriptions(NotificationRegistration registration);
    
    /**
     * Subscribe to a notification topic.
     */
    TopicSubscription subscribe(NotificationRegistration registration, NotificationTopic topic);
    
    /**
     * Unsubscribe from a notification topic.
     */
    void unsubscribe(NotificationRegistration registration, NotificationTopic topic);
    
    /**
     * Delete a subscription. 
     */
    void delete(TopicSubscription subscription);
}
