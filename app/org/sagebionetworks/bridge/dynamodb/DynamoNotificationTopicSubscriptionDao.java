package org.sagebionetworks.bridge.dynamodb;

import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

import javax.annotation.Resource;

import org.sagebionetworks.bridge.dao.NotificationTopicSubscriptionDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;
import org.sagebionetworks.bridge.models.notifications.NotificationTopic;
import org.sagebionetworks.bridge.models.notifications.NotificationTopicSubscription;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.SubscribeRequest;
import com.amazonaws.services.sns.model.SubscribeResult;
import com.amazonaws.services.sns.model.UnsubscribeRequest;
import com.google.common.collect.ImmutableList;

public class DynamoNotificationTopicSubscriptionDao implements NotificationTopicSubscriptionDao {

    private DynamoDBMapper mapper;
    
    private AmazonSNSClient snsClient;
    
    @Resource(name = "notificationTopicSubscriptionMapper")
    final void setNotificationTopicSubscriptionMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }
    
    @Resource(name = "snsClient")
    final void setSnsClient(AmazonSNSClient snsClient) {
        this.snsClient = snsClient;
    }
    
    public List<NotificationTopicSubscription> listSubscriptions(String healthCode) {
        checkNotNull(healthCode);
        
        DynamoNotificationTopicSubscription hashKey = new DynamoNotificationTopicSubscription();
        hashKey.setHealthCode(healthCode);
        DynamoDBQueryExpression<DynamoNotificationTopicSubscription> query = new DynamoDBQueryExpression<DynamoNotificationTopicSubscription>()
                .withConsistentRead(false).withHashKeyValues(hashKey);

        QueryResultPage<DynamoNotificationTopicSubscription> resultPage = mapper.queryPage(DynamoNotificationTopicSubscription.class, query);
        return ImmutableList.copyOf(resultPage.getResults());
    }
    
    public NotificationTopicSubscription subscribe(String healthCode, NotificationRegistration registration, NotificationTopic topic) {
        checkNotNull(healthCode);
        checkNotNull(registration);
        checkNotNull(topic);

        DynamoNotificationTopicSubscription subscription = new DynamoNotificationTopicSubscription();
        subscription.setHealthCode(healthCode);
        subscription.setRegistrationGuid(registration.getGuid());
        subscription.setTopicGuid(topic.getGuid());
        
        SubscribeRequest request = new SubscribeRequest()
                .withEndpoint(registration.getEndpointARN())
                .withTopicArn(topic.getTopicARN());
        
        SubscribeResult result = snsClient.subscribe(request);
        String subscriptionARN = result.getSubscriptionArn();
        subscription.setSubscriptionARN(subscriptionARN);
        
        try {
            
            mapper.save(subscription);
            return subscription;
            
        } catch(Throwable throwable) {
            UnsubscribeRequest unsubRequest = new UnsubscribeRequest().withSubscriptionArn(subscriptionARN);
            snsClient.unsubscribe(unsubRequest);
            throw throwable;
        }
    }

    public void unsubscribe(String healthCode, NotificationRegistration registration, NotificationTopic topic) {
        checkNotNull(healthCode);
        checkNotNull(registration);
        checkNotNull(topic);

        DynamoNotificationTopicSubscription hashKey = new DynamoNotificationTopicSubscription();
        hashKey.setHealthCode(healthCode);
        hashKey.setRegistrationGuid(registration.getGuid());

        DynamoNotificationTopicSubscription subscription = mapper.load(hashKey);
        if (subscription == null) {
            throw new EntityNotFoundException(NotificationTopicSubscription.class);
        }
        
        UnsubscribeRequest request = new UnsubscribeRequest().withSubscriptionArn(subscription.getSubscriptionARN());
        snsClient.unsubscribe(request);
        
        // If this fails, user is told there was an error, and they are still subscribed. This is okay, they can try 
        // and unsubscribe again and that will work (and my finally succeed). Meanwhile they don't get notifications, 
        // which is the most important thing (never send when they don't want you to).
        mapper.delete(subscription);
    }
}
