package org.sagebionetworks.bridge.dynamodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;
import org.sagebionetworks.bridge.models.notifications.NotificationTopic;
import org.sagebionetworks.bridge.models.notifications.TopicSubscription;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.SubscribeRequest;
import com.amazonaws.services.sns.model.SubscribeResult;
import com.amazonaws.services.sns.model.UnsubscribeRequest;

@RunWith(MockitoJUnitRunner.class)
public class DynamoTopicSubscriptionDaoTest {
    
    private static final AmazonServiceException EXCEPTION = new AmazonServiceException("Somethin' bad happened [test]");

    private DynamoTopicSubscriptionDao subDao;
    
    @Mock
    private DynamoDBMapper mockMapper;
    
    @Mock
    private AmazonSNSClient mockSnsClient;
    
    @Mock
    private SubscribeResult mockSubscribeResult;
    
    @Mock
    private DynamoTopicSubscription mockSubscription;
    
    @Captor
    private ArgumentCaptor<SubscribeRequest> subscribeRequestCaptor;
    
    @Captor
    private ArgumentCaptor<DynamoTopicSubscription> subscriptionCaptor;
    
    @Captor
    private ArgumentCaptor<String> unsubscribeStringCaptor;
    
    @Before
    public void before() {
        subDao = new DynamoTopicSubscriptionDao();
        subDao.setTopicSubscriptionMapper(mockMapper);
        subDao.setSnsClient(mockSnsClient);
    }
    
    @Test
    public void successfulSubscribe() {
        doReturn("subscriptionARN").when(mockSubscribeResult).getSubscriptionArn();
        doReturn(mockSubscribeResult).when(mockSnsClient).subscribe(any());
        
        NotificationRegistration registration = createRegistration();
        NotificationTopic topic = createTopic();

        TopicSubscription sub = subDao.subscribe(registration, topic);

        verify(mockSnsClient).subscribe(subscribeRequestCaptor.capture());
        
        SubscribeRequest request = subscribeRequestCaptor.getValue();
        assertEquals("topicARN", request.getTopicArn());
        assertEquals("endpointARN", request.getEndpoint());
        
        verify(mockMapper).save(subscriptionCaptor.capture());
        
        TopicSubscription saved = subscriptionCaptor.getValue();
        assertEquals("registrationGuid", saved.getRegistrationGuid());
        assertEquals("subscriptionARN", saved.getSubscriptionARN());
        assertEquals("topicGuid", saved.getTopicGuid());
        
        assertEquals("registrationGuid", sub.getRegistrationGuid());
        assertEquals("subscriptionARN", sub.getSubscriptionARN());
        assertEquals("topicGuid", sub.getTopicGuid());
    }
    
    @Test
    public void subscribeWhenSnsFails() {
        doReturn("subscriptionARN").when(mockSubscribeResult).getSubscriptionArn();
        doReturn(mockSubscribeResult).when(mockSnsClient).subscribe(any());
        
        doThrow(EXCEPTION).when(mockSnsClient).subscribe(any(SubscribeRequest.class));
        
        NotificationRegistration registration = createRegistration();
        NotificationTopic topic = createTopic();

        try {
            subDao.subscribe(registration, topic);
        } catch(AmazonServiceException e) {
            verify(mockMapper, never()).save(subscriptionCaptor.capture());
        }
    }
    
    @Test
    public void subscribeWhenMapperFails() {
        doReturn("subscriptionARN").when(mockSubscribeResult).getSubscriptionArn();
        doReturn(mockSubscribeResult).when(mockSnsClient).subscribe(any());
        
        doThrow(EXCEPTION).when(mockMapper).save(any());
        
        NotificationRegistration registration = createRegistration();
        NotificationTopic topic = createTopic();
        
        try {
            subDao.subscribe(registration, topic);
        } catch(AmazonServiceException e) {
        }
        
        verify(mockSnsClient).subscribe(any());
        verify(mockMapper).save(subscriptionCaptor.capture());
        verify(mockSnsClient).unsubscribe(unsubscribeStringCaptor.capture());
        
        TopicSubscription saved = subscriptionCaptor.getValue();
        assertEquals("registrationGuid", saved.getRegistrationGuid());
        assertEquals("subscriptionARN", saved.getSubscriptionARN());
        assertEquals("topicGuid", saved.getTopicGuid());
        
        String subscriptionARN = unsubscribeStringCaptor.getValue();
        assertEquals("subscriptionARN", subscriptionARN);
    }
    
    @Test
    public void successfulUnsubscribe() {
        doReturn(mockSubscription).when(mockMapper).load(any());
        doReturn("subscriptionARN").when(mockSubscription).getSubscriptionARN();
        doReturn("registrationGuid").when(mockSubscription).getRegistrationGuid();
        doReturn("topicGuid").when(mockSubscription).getTopicGuid();
        
        NotificationRegistration registration = createRegistration();
        NotificationTopic topic = createTopic();
        
        subDao.unsubscribe(registration, topic);
        
        verify(mockMapper).load(subscriptionCaptor.capture());
        verify(mockSnsClient).unsubscribe(unsubscribeStringCaptor.capture());
        verify(mockMapper).delete(subscriptionCaptor.capture());
        
        DynamoTopicSubscription loadKey = subscriptionCaptor.getAllValues().get(0);
        assertEquals("registrationGuid", loadKey.getRegistrationGuid());
        assertEquals("topicGuid", loadKey.getTopicGuid());
        
        String subscriptionARN = unsubscribeStringCaptor.getValue();
        assertEquals("subscriptionARN", subscriptionARN);
        
        DynamoTopicSubscription deleteObj = subscriptionCaptor.getAllValues().get(1);
        assertEquals("registrationGuid", deleteObj.getRegistrationGuid());
        assertEquals("topicGuid", deleteObj.getTopicGuid());
    }
    
    @Test
    public void unsubscribeWhenSnsFails() {
        doReturn(mockSubscription).when(mockMapper).load(any());
        doReturn("subscriptionARN").when(mockSubscription).getSubscriptionARN();
        doReturn("registrationGuid").when(mockSubscription).getRegistrationGuid();
        doReturn("topicGuid").when(mockSubscription).getTopicGuid();
        
        doThrow(EXCEPTION).when(mockSnsClient).unsubscribe(any(String.class));
        
        NotificationRegistration registration = createRegistration();
        NotificationTopic topic = createTopic();
        
        // Do not delete DDB record if the unsubscribe call fails for any reason.
        try {
            subDao.unsubscribe(registration, topic);
            fail("Should have thrown exception");
        } catch(AmazonServiceException e) {
        }
        
        verify(mockMapper).load(subscriptionCaptor.capture());
        verify(mockSnsClient).unsubscribe(unsubscribeStringCaptor.capture());
        verify(mockMapper, never()).delete(subscriptionCaptor.capture());
        
        DynamoTopicSubscription loadKey = subscriptionCaptor.getAllValues().get(0);
        assertEquals("registrationGuid", loadKey.getRegistrationGuid());
        assertEquals("topicGuid", loadKey.getTopicGuid());
        
        String subscriptionARN = unsubscribeStringCaptor.getValue();
        assertEquals("subscriptionARN", subscriptionARN);
    }
    
    @Test
    public void unsubscribeWhenMapperFails() {
        doReturn(mockSubscription).when(mockMapper).load(any());
        doReturn("subscriptionARN").when(mockSubscription).getSubscriptionARN();
        doReturn("registrationGuid").when(mockSubscription).getRegistrationGuid();
        doReturn("topicGuid").when(mockSubscription).getTopicGuid();
        
        doThrow(EXCEPTION).when(mockMapper).delete(any());
        
        NotificationRegistration registration = createRegistration();
        NotificationTopic topic = createTopic();
        
        try {
            subDao.unsubscribe(registration, topic);
        } catch(AmazonServiceException e) {
        }
        
        verify(mockMapper).load(subscriptionCaptor.capture());
        verify(mockSnsClient).unsubscribe(unsubscribeStringCaptor.capture());
        verify(mockMapper).delete(subscriptionCaptor.capture());
        
        DynamoTopicSubscription loadKey = subscriptionCaptor.getAllValues().get(0);
        assertEquals("registrationGuid", loadKey.getRegistrationGuid());
        assertEquals("topicGuid", loadKey.getTopicGuid());
        
        String subscriptionARN = unsubscribeStringCaptor.getValue();
        assertEquals("subscriptionARN", subscriptionARN);
        
        DynamoTopicSubscription deleteObj = subscriptionCaptor.getAllValues().get(1);
        assertEquals("registrationGuid", deleteObj.getRegistrationGuid());
        
        // This leaves no SNS topic subscription, but a record in DDB. This is okay and we will
        // attempt to clean this up from the service any time the user retrieves the records.
    }
    
    @Test
    public void deleteWorks() {
        TopicSubscription subscription = TopicSubscription.create();
        subscription.setSubscriptionARN("subscriptionARN");
        
        subDao.delete(subscription);
        
        verify(mockSnsClient).unsubscribe(unsubscribeStringCaptor.capture());
        String subscriptionARN = unsubscribeStringCaptor.getValue();
        assertEquals("subscriptionARN", subscriptionARN);
        
        verify(mockMapper).delete(subscriptionCaptor.capture());
        DynamoTopicSubscription capturedSub = subscriptionCaptor.getValue();
        assertEquals(subscription, capturedSub);
    }
    
    @Test
    public void deleteTriesBothStoresIfSnsFails() {
        TopicSubscription subscription = TopicSubscription.create();
        subscription.setSubscriptionARN("subscriptionARN");
        
        doThrow(EXCEPTION).when(mockSnsClient).unsubscribe(any(UnsubscribeRequest.class));
        
        try {
            subDao.delete(subscription);    
        } catch(AmazonServiceException e) {
        }
        
        verify(mockSnsClient).unsubscribe(unsubscribeStringCaptor.capture());
        String subscriptionARN = unsubscribeStringCaptor.getValue();
        assertEquals("subscriptionARN", subscriptionARN);
        
        verify(mockMapper).delete(subscriptionCaptor.capture());
        DynamoTopicSubscription capturedSub = subscriptionCaptor.getValue();
        assertEquals(subscription, capturedSub);
    }

    private NotificationTopic createTopic() {
        NotificationTopic topic = NotificationTopic.create();
        topic.setGuid("topicGuid");
        topic.setTopicARN("topicARN");
        return topic;
    }

    private NotificationRegistration createRegistration() {
        NotificationRegistration registration = NotificationRegistration.create();
        registration.setDeviceId("deviceId");
        registration.setEndpointARN("endpointARN");
        registration.setGuid("registrationGuid");
        registration.setHealthCode("healthCode");
        registration.setOsName("osName");
        return registration;
    }
}
