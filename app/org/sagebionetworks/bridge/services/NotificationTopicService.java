package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import com.google.common.collect.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.NotificationRegistrationDao;
import org.sagebionetworks.bridge.dao.NotificationTopicDao;
import org.sagebionetworks.bridge.dao.TopicSubscriptionDao;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.CriteriaUtils;
import org.sagebionetworks.bridge.models.notifications.NotificationMessage;
import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;
import org.sagebionetworks.bridge.models.notifications.SubscriptionStatus;
import org.sagebionetworks.bridge.models.notifications.NotificationTopic;
import org.sagebionetworks.bridge.models.notifications.TopicSubscription;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.validators.NotificationMessageValidator;
import org.sagebionetworks.bridge.validators.NotificationTopicValidator;
import org.sagebionetworks.bridge.validators.Validate;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.NotFoundException;
import com.amazonaws.services.sns.model.PublishRequest;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

@Component
public class NotificationTopicService {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationTopicService.class);
    
    private NotificationRegistrationDao registrationDao;
    
    private NotificationTopicDao topicDao;
    
    private TopicSubscriptionDao subscriptionDao;
    
    private AmazonSNSClient snsClient;
    
    @Autowired
    final void setNotificationRegistrationDao(NotificationRegistrationDao registrationDao) {
        this.registrationDao = registrationDao;
    }

    @Autowired
    final void setNotificationTopicDao(NotificationTopicDao topicDao) {
        this.topicDao = topicDao;
    }
    
    @Autowired
    final void setTopicSubscriptionDao(TopicSubscriptionDao subscriptionDao) {
        this.subscriptionDao = subscriptionDao;
    }
    
    @Resource(name = "snsClient")
    final void setSnsClient(AmazonSNSClient snsClient) {
        this.snsClient = snsClient;
    }
    
    public List<NotificationTopic> listTopics(StudyIdentifier studyId, boolean includeDeleted) {
        checkNotNull(studyId);
        
        return topicDao.listTopics(studyId, includeDeleted);
    }
    
    public NotificationTopic getTopic(StudyIdentifier studyId, String guid) {
        checkNotNull(studyId);
        checkNotNull(guid);
        
        return topicDao.getTopic(studyId, guid);
    }
    
    public NotificationTopic createTopic(NotificationTopic topic) {
        checkNotNull(topic);
        
        Validate.entityThrowingException(NotificationTopicValidator.INSTANCE, topic);
        
        return topicDao.createTopic(topic);
    }
    
    public NotificationTopic updateTopic(NotificationTopic topic) {
        checkNotNull(topic);
        
        Validate.entityThrowingException(NotificationTopicValidator.INSTANCE, topic);
        
        return topicDao.updateTopic(topic);
    }
    
    public void deleteTopic(StudyIdentifier studyId, String guid) {
        checkNotNull(studyId);
        checkNotNull(guid);
        
        topicDao.deleteTopic(studyId, guid);
    }
    
    public void deleteTopicPermanently(StudyIdentifier studyId, String guid) {
        checkNotNull(studyId);
        checkNotNull(guid);
        
        topicDao.deleteTopicPermanently(studyId, guid);
    }
    
    /**
     * Delete all the topics in the study permanently.
     */
    public void deleteAllTopics(StudyIdentifier studyId) {
        checkNotNull(studyId);
        
        topicDao.deleteAllTopics(studyId);
    }
    
    public void sendNotification(StudyIdentifier studyId, String guid, NotificationMessage message) {
        checkNotNull(studyId);
        checkNotNull(guid);
        checkNotNull(message);
        
        Validate.entityThrowingException(NotificationMessageValidator.INSTANCE, message);
        
        NotificationTopic topic = getTopic(studyId, guid);
        
        PublishRequest request = new PublishRequest().withTopicArn(topic.getTopicARN())
                .withSubject(message.getSubject()).withMessage(message.getMessage());
        
        snsClient.publish(request);
    }
    
    public List<SubscriptionStatus> currentSubscriptionStatuses(StudyIdentifier studyId, String healthCode, String registrationGuid) {
        checkNotNull(studyId);
        checkNotNull(healthCode);
        checkNotNull(registrationGuid);
        
        NotificationRegistration registration = registrationDao.getRegistration(healthCode, registrationGuid);
        
        Set<String> subscribedTopicGuids = subscriptionDao.listSubscriptions(registration)
                .stream().map(TopicSubscription::getTopicGuid).collect(Collectors.toSet());
        
        List<NotificationTopic> topics = topicDao.listTopics(studyId, false);
        List<SubscriptionStatus> statuses = Lists.newArrayListWithCapacity(topics.size());
        for (NotificationTopic topic : topics) {
            boolean isCurrentlySubscribed = subscribedTopicGuids.contains(topic.getGuid());
            SubscriptionStatus status = new SubscriptionStatus(topic.getGuid(), topic.getName(), isCurrentlySubscribed);
            statuses.add(status);
        }
        return statuses;
    }

    /**
     * Manages criteria-based subscriptions for the given participant with the given criteria context. All topics that
     * match the criteria context will be subscribed. All other topics will be unsubscribed. This only considers
     * criteria-managed subscriptions. Manually-managed subscriptions will be untouched.
     */
    public void manageCriteriaBasedSubscriptions(StudyIdentifier studyId, CriteriaContext context, String healthCode) {
        checkNotNull(studyId);
        checkNotNull(context);
        checkNotNull(healthCode);
        checkArgument(isNotBlank(healthCode));

        // Check study for topics. Only consider topics with criteria. Include logically deleted topics 
        // so that if they are undeleted, the user's subscription state is correct
        List<NotificationTopic> allTopicList = topicDao.listTopics(studyId, true);
        List<NotificationTopic> criteriaTopicList = allTopicList.stream()
                .filter(topic -> topic.getCriteria() != null).collect(Collectors.toList());
        if (criteriaTopicList.isEmpty()) {
            // Short cut: No topics in the study means nothing to manage.
            return;
        }

        // Check participant for notification registrations.
        List<NotificationRegistration> registrationList = registrationDao.listRegistrations(healthCode);
        if (registrationList.isEmpty()) {
            // Short cut: No registrations means nothing to manage.
            return;
        }

        // Determine topics to subscribe to based on criteria.
        Set<String> desiredTopicGuidSet = criteriaTopicList.stream()
                .filter(topic -> CriteriaUtils.matchCriteria(context, topic.getCriteria()))
                .map(NotificationTopic::getGuid).collect(Collectors.toSet());

        // Subscribe user to topics.
        for (NotificationRegistration oneRegistration : registrationList) {
            setSubscriptionsForRegistration(oneRegistration, criteriaTopicList, desiredTopicGuidSet);
        }
    }

    /**
     * Unsubscribe the given registration from all topics. This is generally used before deleting a registration, to
     * clean up any orphaned subscriptions.
     */
    public void unsubscribeAll(StudyIdentifier studyId, String healthCode, String registrationGuid) {
        checkNotNull(studyId);
        checkNotNull(healthCode);
        checkNotNull(registrationGuid);

        // Get subscriptions.
        NotificationRegistration registration = registrationDao.getRegistration(healthCode, registrationGuid);
        List<? extends TopicSubscription> subscriptionList =  subscriptionDao.listSubscriptions(registration);
        if (subscriptionList.isEmpty()) {
            // Shortcut: No subscriptions to unsubscribe from.
            return;
        }

        // Unsubscribe from each subscription.
        for (TopicSubscription oneSubscription : subscriptionList) {
            String topicGuid = oneSubscription.getTopicGuid();
            try {
                NotificationTopic topic = topicDao.getTopic(studyId, topicGuid);
                subscriptionDao.unsubscribe(registration, topic);
            } catch (RuntimeException ex) {
                LOG.error("Error unsubscribing registration " + registrationGuid + " from topic " + topicGuid + ": " +
                        ex.getMessage(), ex);
            }
        }
    }

    /**
     * For the given account and registration, set their topic subscription to those specified by the
     * desiredTopicGuidSet. All topics in the set will be subscribed, and all topics not in the set will be
     * unsubscribed. Note that this only affects manual subscription topics. Topics managed by criteria are ignored by
     * this method.
     */
    public List<SubscriptionStatus> subscribe(StudyIdentifier studyId, String healthCode, String registrationGuid,
            Set<String> desiredTopicGuidSet) {
        checkNotNull(studyId);
        checkNotNull(healthCode);
        checkNotNull(registrationGuid);
        checkNotNull(desiredTopicGuidSet);

        // This API can only subscribe/unsubscribe from topics that aren't managed by criteria.
        List<NotificationTopic> allTopicList = topicDao.listTopics(studyId, false);
        List<NotificationTopic> manualSubscriptionTopicList = allTopicList.stream()
                .filter(topic -> topic.getCriteria() == null).collect(Collectors.toList());
        if (manualSubscriptionTopicList.isEmpty()) {
            // Short cut: No topics in the study means nothing to manage.
            return ImmutableList.of();
        }

        // Set subscriptions.
        NotificationRegistration registration = registrationDao.getRegistration(healthCode, registrationGuid);
        return setSubscriptionsForRegistration(registration, manualSubscriptionTopicList, desiredTopicGuidSet);
    }

    // Helper method that, given a registration and a set of desired topic GUIDs, sets the user's subscriptions to
    // match that set. All topics in the set will be subscribed, and all topics not in that set will be unsubscribed.
    // The list of eligible topics is passed in. This allows us to have separate "namespaces" for criteria managed
    // topics and manually managed topics.
    @SuppressWarnings("ConstantConditions")
    private List<SubscriptionStatus> setSubscriptionsForRegistration(NotificationRegistration registration,
            List<NotificationTopic> eligibleTopicList, Set<String> desiredTopicGuidSet) {
        // Get set of currently subscribed. While we're at it, do some sanity checking on subscriptions.
        Set<String> subscribedTopicGuidSet = cleanupSubscriptions(registration);

        // Set the subscription status of each topic accordingly.
        List<SubscriptionStatus> statuses = new ArrayList<>(eligibleTopicList.size());
        for (NotificationTopic oneTopic : eligibleTopicList) {
            boolean wantsSubscription = desiredTopicGuidSet.contains(oneTopic.getGuid());
            boolean isCurrentlySubscribed = subscribedTopicGuidSet.contains(oneTopic.getGuid());

            Boolean isSubscribed = null;
            if (wantsSubscription && isCurrentlySubscribed) {
                isSubscribed = Boolean.TRUE;
            } else if (!wantsSubscription && !isCurrentlySubscribed) {
                isSubscribed = Boolean.FALSE;
            } else if (wantsSubscription && !isCurrentlySubscribed) {
                isSubscribed = doSubscribe(registration, oneTopic);
            } else if (!wantsSubscription && isCurrentlySubscribed) {
                isSubscribed = doUnsubscribe(registration, oneTopic);
            }
            SubscriptionStatus status = new SubscriptionStatus(oneTopic.getGuid(), oneTopic.getName(), isSubscribed);
            statuses.add(status);
        }
        return statuses;
    }

    private Boolean doSubscribe(NotificationRegistration registration, NotificationTopic topic) {
        try {
            subscriptionDao.subscribe(registration, topic);
            return Boolean.TRUE;
        } catch(Throwable throwable) {
            // Any exception most likely indicates the subscription did not succeed.
            LOG.error("Error subscribing to topic " + topic.getName() + " (" + topic.getGuid() + ")", throwable);
        }
        return Boolean.FALSE;
    }

    private Boolean doUnsubscribe(NotificationRegistration registration, NotificationTopic topic) {
        try {
            subscriptionDao.unsubscribe(registration, topic);
            return Boolean.FALSE;
        } catch(Throwable throwable) {
            // Will have to assume the user is still subscribed.
            LOG.error("Error unsubscribing to topic " + topic.getName() + " (" + topic.getGuid() + ")", throwable);
        }
        return Boolean.TRUE;
    }

    /**
     * Get a set of the user's current topic subscriptions, but take the opportunity to check for data integrity and 
     * delete any DDB records that don't have a corresponding SNS record... these are unsuccessful unsubscribes 
     * and we're trying here again to finish them.
     */
    private Set<String> cleanupSubscriptions(NotificationRegistration registration) {
        Set<String> subscribedTopicGuids = Sets.newHashSet();
        
        List<? extends TopicSubscription> subscriptions = subscriptionDao.listSubscriptions(registration);
        for (TopicSubscription subscription : subscriptions) {
            try {
                snsClient.getSubscriptionAttributes(subscription.getSubscriptionARN());
                subscribedTopicGuids.add(subscription.getTopicGuid());    
            } catch(NotFoundException e) {
                LOG.warn("SNS topic " + subscription.getTopicGuid() + " not found, deleting DDB record", e);
                subscriptionDao.removeOrphanedSubscription(subscription);
            } catch(AmazonServiceException e) {
                LOG.warn("Error cleaning up subscriptions", e);
                // However, it is there, so include it in the list of subscriptions.
                subscribedTopicGuids.add(subscription.getTopicGuid());
            }
        }
        return subscribedTopicGuids;
    }
}
