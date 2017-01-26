package org.sagebionetworks.bridge.dynamodb;

import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

import javax.annotation.Resource;

import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.TopicSubscriptionDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;
import org.sagebionetworks.bridge.models.notifications.NotificationTopic;
import org.sagebionetworks.bridge.models.notifications.TopicSubscription;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.SubscribeRequest;
import com.amazonaws.services.sns.model.SubscribeResult;
import com.google.common.collect.ImmutableList;

@Component
public class DynamoTopicSubscriptionDao implements TopicSubscriptionDao {

    private DynamoDBMapper mapper;
    
    private AmazonSNSClient snsClient;
    
    @Resource(name = "topicSubscriptionMapper")
    final void setTopicSubscriptionMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }
    
    @Resource(name = "snsClient")
    final void setSnsClient(AmazonSNSClient snsClient) {
        this.snsClient = snsClient;
    }
    
    public List<TopicSubscription> listSubscriptions(NotificationRegistration registration) {
        checkNotNull(registration);
        
        DynamoTopicSubscription hashKey = new DynamoTopicSubscription();
        hashKey.setRegistrationGuid(registration.getGuid());
        
        DynamoDBQueryExpression<DynamoTopicSubscription> query = new DynamoDBQueryExpression<DynamoTopicSubscription>()
                .withConsistentRead(false).withHashKeyValues(hashKey);

        QueryResultPage<DynamoTopicSubscription> resultPage = mapper.queryPage(DynamoTopicSubscription.class, query);
        return ImmutableList.copyOf(resultPage.getResults());
    }
    
    public TopicSubscription subscribe(NotificationRegistration registration, NotificationTopic topic) {
        checkNotNull(registration);
        checkNotNull(topic);

        // If it already exists, this will just overwrite it.
        DynamoTopicSubscription subscription = new DynamoTopicSubscription();
        subscription.setRegistrationGuid(registration.getGuid());
        subscription.setTopicGuid(topic.getGuid());
        
        SubscribeRequest request = new SubscribeRequest()
                .withEndpoint(registration.getEndpointARN())
                .withProtocol("application")
                .withTopicArn(topic.getTopicARN());
        
        SubscribeResult result = snsClient.subscribe(request);
        String subscriptionARN = result.getSubscriptionArn();
        subscription.setSubscriptionARN(subscriptionARN);
        
        try {
            
            mapper.save(subscription);
            return subscription;
            
        } catch(Throwable throwable) {
            snsClient.unsubscribe(subscriptionARN);
            throw throwable;
        }
    }

    public void unsubscribe(NotificationRegistration registration, NotificationTopic topic) {
        checkNotNull(registration);
        checkNotNull(topic);

        DynamoTopicSubscription hashKey = new DynamoTopicSubscription();
        hashKey.setRegistrationGuid(registration.getGuid());
        hashKey.setTopicGuid(topic.getGuid());
        
        DynamoTopicSubscription subscription = mapper.load(hashKey);
        if (subscription == null) {
            throw new EntityNotFoundException(TopicSubscription.class);
        }
        
        snsClient.unsubscribe(subscription.getSubscriptionARN());
        
        // If this fails, user is told there was an error, and they are still subscribed. This is okay, they can try 
        // and unsubscribe again and that will work (and my finally succeed). We will also fix this during reconciliation
        // in the service
        mapper.delete(subscription);
    }

    @Override
    public void delete(TopicSubscription subscription) {
        checkNotNull(subscription);
        // No exception is caught here, so user will see errors, but nevertheless we will
        // try and clean up both data stores.
        try {
            snsClient.unsubscribe(subscription.getSubscriptionARN());
        } finally {
            mapper.delete(subscription);
        }
    }

}
