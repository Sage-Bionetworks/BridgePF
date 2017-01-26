package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.NotificationRegistrationDao;
import org.sagebionetworks.bridge.dao.NotificationTopicDao;
import org.sagebionetworks.bridge.dao.TopicSubscriptionDao;
import org.sagebionetworks.bridge.models.notifications.NotificationMessage;
import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;
import org.sagebionetworks.bridge.models.notifications.SubscriptionStatus;
import org.sagebionetworks.bridge.models.notifications.NotificationTopic;
import org.sagebionetworks.bridge.models.notifications.SubscriptionRequest;
import org.sagebionetworks.bridge.models.notifications.TopicSubscription;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.validators.NotificationMessageValidator;
import org.sagebionetworks.bridge.validators.NotificationTopicValidator;
import org.sagebionetworks.bridge.validators.Validate;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.NotFoundException;
import com.amazonaws.services.sns.model.PublishRequest;
import com.google.common.collect.Sets;
import com.newrelic.agent.deps.com.google.common.collect.Lists;

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
    
    public List<NotificationTopic> listTopics(StudyIdentifier studyId) {
        checkNotNull(studyId);
        
        return topicDao.listTopics(studyId);
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
        NotificationRegistration registration = registrationDao.getRegistration(healthCode, registrationGuid);
        
        Set<String> subscribedTopicGuids = subscriptionDao.listSubscriptions(registration)
                .stream().map(TopicSubscription::getTopicGuid).collect(Collectors.toSet());
        
        List<NotificationTopic> topics = topicDao.listTopics(studyId);
        List<SubscriptionStatus> statuses = Lists.newArrayListWithCapacity(topics.size());
        for (NotificationTopic topic : topics) {
            boolean isCurrentlySubscribed = subscribedTopicGuids.contains(topic.getGuid());
            SubscriptionStatus status = new SubscriptionStatus(topic.getGuid(), topic.getName(), isCurrentlySubscribed);
            statuses.add(status);
        }
        return statuses;
    }
    
    public List<SubscriptionStatus> subscribe(StudyIdentifier studyId, String healthCode, SubscriptionRequest request) {
        String registrationGuid = request.getRegistrationGuid();
        NotificationRegistration registration = registrationDao.getRegistration(healthCode, registrationGuid);
        
        Set<String> subscribedTopicGuids = cleanupSubscriptions(registration);
        
        List<NotificationTopic> topics = topicDao.listTopics(studyId);
        List<SubscriptionStatus> statuses = Lists.newArrayListWithCapacity(topics.size());
        
        for (NotificationTopic topic : topics) {
            boolean wantsSubscription = request.getTopicGuids().contains(topic.getGuid());
            boolean isCurrentlySubscribed = subscribedTopicGuids.contains(topic.getGuid());
            
            Boolean isSubscribed = null; 
            if (wantsSubscription && isCurrentlySubscribed) {
                isSubscribed = Boolean.TRUE;
            } else if (!wantsSubscription && !isCurrentlySubscribed) {
                isSubscribed = Boolean.FALSE;
            } else if (wantsSubscription && !isCurrentlySubscribed) {
                isSubscribed = doSubscribe(registration, topic);
            } else if (!wantsSubscription && isCurrentlySubscribed) {
                isSubscribed = doUnsubscribe(registration, topic);
            }
            SubscriptionStatus status = new SubscriptionStatus(topic.getGuid(), topic.getName(), isSubscribed);
            statuses.add(status);
        }
        return statuses;
    }

    private Boolean doSubscribe(NotificationRegistration registration, NotificationTopic topic) {
        try {
            subscriptionDao.subscribe(registration, topic);
            return Boolean.TRUE;
        } catch(Throwable throwable) {
            // Any exception is most likely to indicate the subscription did not succeed.
            LOG.warn("Error subscribing to topic " + topic.getName() + " (" + topic.getGuid() + ")", throwable);
        }
        return Boolean.FALSE;
    }

    private Boolean doUnsubscribe(NotificationRegistration registration, NotificationTopic topic) {
        try {
            subscriptionDao.unsubscribe(registration, topic);
            return Boolean.FALSE;
        } catch(Throwable throwable) {
            // Will have to assume the user is still subscribed, though that's not 100% certain.
            LOG.warn("Error unsubscribing to topic " + topic.getName() + " (" + topic.getGuid() + ")", throwable);
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
        
        List<TopicSubscription> subscriptions = subscriptionDao.listSubscriptions(registration);
        for (TopicSubscription subscription : subscriptions) {
            try {
                snsClient.getSubscriptionAttributes(subscription.getSubscriptionARN());
                subscribedTopicGuids.add(subscription.getTopicGuid());
            } catch(NotFoundException e) {
                subscriptionDao.delete(subscription);
            } catch(AmazonServiceException e) {
                LOG.warn("Error cleaning up subscriptions", e);
            }
        }
        return subscribedTopicGuids;
    }
}
