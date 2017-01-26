package org.sagebionetworks.bridge.services;

import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestUtils.getNotificationTopic;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.NotificationRegistrationDao;
import org.sagebionetworks.bridge.dao.NotificationTopicDao;
import org.sagebionetworks.bridge.dao.TopicSubscriptionDao;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.notifications.NotificationMessage;
import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;
import org.sagebionetworks.bridge.models.notifications.SubscriptionStatus;
import org.sagebionetworks.bridge.models.notifications.NotificationTopic;
import org.sagebionetworks.bridge.models.notifications.SubscriptionRequest;
import org.sagebionetworks.bridge.models.notifications.TopicSubscription;

import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.PublishRequest;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

@RunWith(MockitoJUnitRunner.class)
public class NotificationTopicServiceTest {

    @Mock
    private NotificationTopicDao mockTopicDao;
    
    @Mock
    private AmazonSNSClient mockSnsClient;
    
    @Mock
    private NotificationRegistrationDao mockRegistrationDao;
    
    @Mock
    private TopicSubscriptionDao mockSubscriptionDao;
    
    @Mock
    private NotificationRegistration mockNotificationRegistration;
    
    @Captor
    private ArgumentCaptor<PublishRequest> publishRequestCaptor;
    
    @Captor
    private ArgumentCaptor<NotificationTopic> topicCaptor;
    
    private NotificationTopicService service;
    
    @Before
    public void before() {
        service = new NotificationTopicService();
        service.setNotificationTopicDao(mockTopicDao);
        service.setSnsClient(mockSnsClient);
        service.setNotificationRegistrationDao(mockRegistrationDao);
        service.setTopicSubscriptionDao(mockSubscriptionDao);
    }
    
    @Test
    public void listTopics() {
        List<NotificationTopic> list = Lists.newArrayList(getNotificationTopic(), getNotificationTopic());
        doReturn(list).when(mockTopicDao).listTopics(TEST_STUDY);
        
        List<NotificationTopic> results = service.listTopics(TEST_STUDY);
        assertEquals(2, results.size());
        
        verify(mockTopicDao).listTopics(TEST_STUDY);
    }
    
    @Test
    public void getTopic() {
        NotificationTopic topic = getNotificationTopic();
        doReturn(topic).when(mockTopicDao).getTopic(TEST_STUDY, topic.getGuid());
        
        NotificationTopic result = service.getTopic(TEST_STUDY, topic.getGuid());
        assertEquals(topic, result);
        
        verify(mockTopicDao).getTopic(TEST_STUDY, topic.getGuid());
    }
    
    @Test
    public void createTopic() {
        NotificationTopic topic = getNotificationTopic();
        doReturn(topic).when(mockTopicDao).createTopic(topic);
        
        NotificationTopic result = service.createTopic(topic);
        assertEquals(topic, result);
        
        verify(mockTopicDao).createTopic(topic);
    }
    
    @Test
    public void createdTopicValidatesObject() {
        NotificationTopic topic = getNotificationTopic();
        topic.setName(null);
        try {
            service.createTopic(topic);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            verify(mockTopicDao, never()).createTopic(topic);
        }
    }
    
    @Test
    public void updateTopic() {
        NotificationTopic topic = getNotificationTopic();
        doReturn(topic).when(mockTopicDao).updateTopic(topic);
        
        NotificationTopic result = service.updateTopic(topic);
        assertEquals(topic, result);
        
        verify(mockTopicDao).updateTopic(topic);
    }
    
    @Test
    public void updateTopicValidatesObject() {
        NotificationTopic topic = getNotificationTopic();
        topic.setName(null);
        try {
            service.updateTopic(topic);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            verify(mockTopicDao, never()).updateTopic(topic);
        }        
    }
    
    @Test
    public void deleteTopic() {
        service.deleteTopic(TEST_STUDY, "ABC-DEF");
        
        verify(mockTopicDao).deleteTopic(TEST_STUDY, "ABC-DEF");
    }
    
    @Test
    public void deleteAllTopics() {
        service.deleteAllTopics(TEST_STUDY);
        
        verify(mockTopicDao).deleteAllTopics(TEST_STUDY);
    }
    
    @Test
    public void sendNotification() {
        NotificationMessage message = TestUtils.getNotificationMessage();
        NotificationTopic topic = getNotificationTopic();
        topic.setTopicARN("topicARN");
        doReturn(topic).when(mockTopicDao).getTopic(TEST_STUDY, "ABC-DEF");
        
        service.sendNotification(TEST_STUDY,  "ABC-DEF", message);
        
        verify(mockSnsClient).publish(publishRequestCaptor.capture());
        PublishRequest request = publishRequestCaptor.getValue();
        assertEquals("a subject", request.getSubject());
        assertEquals("a message", request.getMessage());
        assertEquals("topicARN", request.getTopicArn());
    }
    
    private NotificationTopic createTopic(String guid) {
        NotificationTopic topic = NotificationTopic.create();
        topic.setGuid(guid);
        topic.setTopicARN("arn:"+guid);
        return topic;
    }

    private TopicSubscription getSub(String topicGuid) {
        TopicSubscription sub = TopicSubscription.create();
        sub.setTopicGuid(topicGuid);
        return sub;
    }
    
    @Test
    public void currentSubscriptionStatuses() {
        List<TopicSubscription> subscribedTopicsStartOfTest = Lists.newArrayList(getSub("topicA"), getSub("topicC"));

        List<NotificationTopic> allTopics = Lists.newArrayList(createTopic("topicA"), createTopic("topicB"), createTopic("topicC"));
        
        doReturn(mockNotificationRegistration).when(mockRegistrationDao).getRegistration("healthCode", "registrationGuid");
        doReturn(subscribedTopicsStartOfTest).when(mockSubscriptionDao).listSubscriptions(mockNotificationRegistration);
        doReturn(allTopics).when(mockTopicDao).listTopics(TEST_STUDY);
        
        List<SubscriptionStatus> statuses = service.currentSubscriptionStatuses(TEST_STUDY, "healthCode", "registrationGuid");
        
        ImmutableMap<String,SubscriptionStatus> statusesByTopicId = Maps.uniqueIndex(statuses, SubscriptionStatus::getTopicGuid);
        
        assertTrue(statusesByTopicId.get("topicA").isSubscribed());
        assertFalse(statusesByTopicId.get("topicB").isSubscribed());
        assertTrue(statusesByTopicId.get("topicC").isSubscribed());
    }
    
    @Test
    public void currentSubscriptionStatusesNoTopics() {
        List<TopicSubscription> subscribedTopicsStartOfTest = Lists.newArrayList(getSub("topicA"), getSub("topicC"));
        List<NotificationTopic> allTopics = Lists.newArrayList();
        
        doReturn(mockNotificationRegistration).when(mockRegistrationDao).getRegistration("healthCode", "registrationGuid");
        doReturn(subscribedTopicsStartOfTest).when(mockSubscriptionDao).listSubscriptions(mockNotificationRegistration);
        doReturn(allTopics).when(mockTopicDao).listTopics(TEST_STUDY);
        
        List<SubscriptionStatus> statuses = service.currentSubscriptionStatuses(TEST_STUDY, "healthCode", "registrationGuid");
        assertTrue(statuses.isEmpty());
    }    
    
    @Test
    public void subscribe() {
        List<TopicSubscription> subscribedTopicsStartOfTest = Lists.newArrayList(getSub("topicA"), getSub("topicC"));
        List<NotificationTopic> allTopics = Lists.newArrayList(createTopic("topicA"), createTopic("topicB"), createTopic("topicC"));
        
        doReturn(mockNotificationRegistration).when(mockRegistrationDao).getRegistration("healthCode", "registrationGuid");
        doReturn(subscribedTopicsStartOfTest).when(mockSubscriptionDao).listSubscriptions(mockNotificationRegistration);
        doReturn(allTopics).when(mockTopicDao).listTopics(TEST_STUDY);
        
        SubscriptionRequest request = new SubscriptionRequest("registrationGuid", Sets.newHashSet("topicA", "topicB"));
        
        List<SubscriptionStatus> statuses = service.subscribe(TEST_STUDY, "healthCode", request);
        
        ImmutableMap<String,SubscriptionStatus> statusesByTopicId = Maps.uniqueIndex(statuses, SubscriptionStatus::getTopicGuid);
        assertTrue(statusesByTopicId.get("topicA").isSubscribed());
        assertTrue(statusesByTopicId.get("topicB").isSubscribed());
        assertFalse(statusesByTopicId.get("topicC").isSubscribed());
        
        // expect to subscribe to B, and unsubscribe from C
        verify(mockSubscriptionDao, times(1)).subscribe(eq(mockNotificationRegistration), topicCaptor.capture());
        assertEquals("arn:topicB", topicCaptor.getValue().getTopicARN());
        
        verify(mockSubscriptionDao, times(1)).unsubscribe(eq(mockNotificationRegistration), topicCaptor.capture());
        assertEquals("arn:topicC", topicCaptor.getValue().getTopicARN());
    }
}
