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
     * In a case whee we have a record of a subscription, but no paired SNS topic subscription, 
     * remove our record. 
     */
    void removeOrphanedSubscription(TopicSubscription subscription);
}
