package org.sagebionetworks.bridge.services;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestUtils.getNotificationTopic;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dao.NotificationRegistrationDao;
import org.sagebionetworks.bridge.dao.NotificationTopicDao;
import org.sagebionetworks.bridge.dao.TopicSubscriptionDao;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.notifications.NotificationMessage;
import org.sagebionetworks.bridge.models.notifications.NotificationProtocol;
import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;
import org.sagebionetworks.bridge.models.notifications.SubscriptionStatus;
import org.sagebionetworks.bridge.models.notifications.NotificationTopic;
import org.sagebionetworks.bridge.models.notifications.TopicSubscription;

import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.PublishRequest;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@SuppressWarnings({ "rawtypes", "unchecked" })
@RunWith(MockitoJUnitRunner.class)
public class NotificationTopicServiceTest {
    private static final String CRITERIA_GROUP_1 = "criteria-group-1";
    private static final String CRITERIA_GROUP_2 = "criteria-group-2";
    private static final CriteriaContext EMPTY_CONTEXT = new CriteriaContext.Builder().withStudyIdentifier(TEST_STUDY)
            .build();
    private static final String HEALTH_CODE = "health-code";

    private static final NotificationTopic CRITERIA_TOPIC_1;
    static {
        Criteria criteria = Criteria.create();
        criteria.setAllOfGroups(ImmutableSet.of(CRITERIA_GROUP_1));

        CRITERIA_TOPIC_1 = NotificationTopic.create();
        CRITERIA_TOPIC_1.setGuid("criteria-topic-guid-1");
        CRITERIA_TOPIC_1.setName("Criteria Topic 1");
        CRITERIA_TOPIC_1.setCriteria(criteria);
    }

    private static final NotificationTopic CRITERIA_TOPIC_2;
    static {
        Criteria criteria = Criteria.create();
        criteria.setAllOfGroups(ImmutableSet.of(CRITERIA_GROUP_2));

        CRITERIA_TOPIC_2 = NotificationTopic.create();
        CRITERIA_TOPIC_2.setGuid("criteria-topic-guid-2");
        CRITERIA_TOPIC_2.setName("Criteria Topic 2");
        CRITERIA_TOPIC_2.setCriteria(criteria);
    }

    private static final NotificationTopic MANUAL_TOPIC_1;
    static {
        MANUAL_TOPIC_1 = NotificationTopic.create();
        MANUAL_TOPIC_1.setGuid("manual-topic-guid-1");
        MANUAL_TOPIC_1.setName("Manual Topic 1");
    }

    private static final NotificationTopic MANUAL_TOPIC_2;
    static {
        MANUAL_TOPIC_2 = NotificationTopic.create();
        MANUAL_TOPIC_2.setGuid("manual-topic-guid-2");
        MANUAL_TOPIC_2.setName("Manual Topic 2");
    }

    private static final NotificationTopic MANUAL_TOPIC_3;
    static {
        MANUAL_TOPIC_3 = NotificationTopic.create();
        MANUAL_TOPIC_3.setGuid("manual-topic-guid-3");
        MANUAL_TOPIC_3.setName("Manual Topic 3");
    }

    private static final NotificationTopic MANUAL_TOPIC_4;
    static {
        MANUAL_TOPIC_4 = NotificationTopic.create();
        MANUAL_TOPIC_4.setGuid("manual-topic-guid-4");
        MANUAL_TOPIC_4.setName("Manual Topic 4");
    }

    private static final NotificationRegistration PUSH_REGISTRATION;
    static {
        PUSH_REGISTRATION = NotificationRegistration.create();
        PUSH_REGISTRATION.setGuid("push-registration-guid");
        PUSH_REGISTRATION.setHealthCode(HEALTH_CODE);
        PUSH_REGISTRATION.setProtocol(NotificationProtocol.APPLICATION);
        PUSH_REGISTRATION.setEndpoint("dummy-endpoint-arn");
    }

    private static final NotificationRegistration SMS_REGISTRATION;
    static {
        SMS_REGISTRATION = NotificationRegistration.create();
        SMS_REGISTRATION.setGuid("sms-registration-guid");
        SMS_REGISTRATION.setHealthCode(HEALTH_CODE);
        SMS_REGISTRATION.setProtocol(NotificationProtocol.SMS);
        SMS_REGISTRATION.setEndpoint("+14255550123");
    }

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
        doReturn(list).when(mockTopicDao).listTopics(TEST_STUDY, true);
        
        List<NotificationTopic> results = service.listTopics(TEST_STUDY, true);
        assertEquals(2, results.size());
        
        verify(mockTopicDao).listTopics(TEST_STUDY, true);
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
    public void deleteTopicPermanently() {
        service.deleteTopicPermanently(TEST_STUDY, "ABC-DEF");
        
        verify(mockTopicDao).deleteTopicPermanently(TEST_STUDY, "ABC-DEF");
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
        doReturn(allTopics).when(mockTopicDao).listTopics(TEST_STUDY, false);
        
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
        doReturn(allTopics).when(mockTopicDao).listTopics(TEST_STUDY, false);
        
        List<SubscriptionStatus> statuses = service.currentSubscriptionStatuses(TEST_STUDY, "healthCode", "registrationGuid");
        assertTrue(statuses.isEmpty());
    }    

    @Test
    public void manageCriteriaBasedSubscriptions_NoCriteriaTopics() {
        // Topic list includes only manual subscriptions.
        when(mockTopicDao.listTopics(TEST_STUDY, true)).thenReturn(ImmutableList.of(MANUAL_TOPIC_1, MANUAL_TOPIC_2));

        // Execute test.
        service.manageCriteriaBasedSubscriptions(TEST_STUDY, EMPTY_CONTEXT, HEALTH_CODE);

        // No subscription changes.
        verifyZeroInteractions(mockSubscriptionDao);
    }

    @Test
    public void manageCriteriaBasedSubscriptions_NoRegistrations() {
        // Mock back-ends.
        when(mockTopicDao.listTopics(TEST_STUDY, true)).thenReturn(ImmutableList.of(CRITERIA_TOPIC_1, CRITERIA_TOPIC_2,
                MANUAL_TOPIC_1, MANUAL_TOPIC_2));
        when(mockRegistrationDao.listRegistrations(HEALTH_CODE)).thenReturn(ImmutableList.of());

        // Execute test.
        service.manageCriteriaBasedSubscriptions(TEST_STUDY, EMPTY_CONTEXT, HEALTH_CODE);

        // No subscription changes.
        verifyZeroInteractions(mockSubscriptionDao);
    }

    @Test
    public void manageCriteriaBasedSubscriptions() {
        // 2 criteria-based topics, 2 manual topics.
        when(mockTopicDao.listTopics(TEST_STUDY, true)).thenReturn(ImmutableList.of(CRITERIA_TOPIC_1, CRITERIA_TOPIC_2,
                MANUAL_TOPIC_1, MANUAL_TOPIC_2));

        // 2 registrations.
        when(mockRegistrationDao.listRegistrations(HEALTH_CODE)).thenReturn(ImmutableList.of(PUSH_REGISTRATION,
                SMS_REGISTRATION));

        // Each registration is subscribed to criteria topic 1 and manual topic 1.
        when(mockSubscriptionDao.listSubscriptions(PUSH_REGISTRATION)).thenReturn((List)ImmutableList.of(
                getSub(CRITERIA_TOPIC_1.getGuid()), getSub(MANUAL_TOPIC_1.getGuid())));
        when(mockSubscriptionDao.listSubscriptions(SMS_REGISTRATION)).thenReturn((List)ImmutableList.of(
                getSub(CRITERIA_TOPIC_1.getGuid()), getSub(MANUAL_TOPIC_1.getGuid())));

        // Create criteria context with data group 2.
        CriteriaContext context = new CriteriaContext.Builder().withContext(EMPTY_CONTEXT).withUserDataGroups(
                ImmutableSet.of(CRITERIA_GROUP_2)).build();

        // Execute test.
        service.manageCriteriaBasedSubscriptions(TEST_STUDY, context, HEALTH_CODE);

        // We un-sub from criteria topic 1 and sub to criteria topic 2.
        verify(mockSubscriptionDao).unsubscribe(PUSH_REGISTRATION, CRITERIA_TOPIC_1);
        verify(mockSubscriptionDao).unsubscribe(SMS_REGISTRATION, CRITERIA_TOPIC_1);
        verify(mockSubscriptionDao).subscribe(PUSH_REGISTRATION, CRITERIA_TOPIC_2);
        verify(mockSubscriptionDao).subscribe(SMS_REGISTRATION, CRITERIA_TOPIC_2);

        // We do not sub or unsub to any manual topics.
        verify(mockSubscriptionDao, never()).unsubscribe(any(), eq(MANUAL_TOPIC_1));
        verify(mockSubscriptionDao, never()).unsubscribe(any(), eq(MANUAL_TOPIC_2));
        verify(mockSubscriptionDao, never()).subscribe(any(), eq(MANUAL_TOPIC_1));
        verify(mockSubscriptionDao, never()).subscribe(any(), eq(MANUAL_TOPIC_2));
    }

    @Test
    public void unsubscribeAll_NoSubscriptions() {
        // Mock dependencies.
        when(mockRegistrationDao.getRegistration(HEALTH_CODE, SMS_REGISTRATION.getGuid())).thenReturn(
                SMS_REGISTRATION);
        when(mockSubscriptionDao.listSubscriptions(SMS_REGISTRATION)).thenReturn(ImmutableList.of());

        // Execute.
        service.unsubscribeAll(TEST_STUDY, HEALTH_CODE, SMS_REGISTRATION.getGuid());

        // We don't unsubscribe from anything.
        verify(mockSubscriptionDao, never()).unsubscribe(any(), any());
    }

    @Test
    public void unsubscribeAll() {
        // Mock dependencies.
        when(mockRegistrationDao.getRegistration(HEALTH_CODE, SMS_REGISTRATION.getGuid())).thenReturn(
                SMS_REGISTRATION);
        when(mockSubscriptionDao.listSubscriptions(SMS_REGISTRATION)).thenReturn((List) ImmutableList.of(
                getSub(MANUAL_TOPIC_1.getGuid()), getSub(MANUAL_TOPIC_2.getGuid()), getSub(MANUAL_TOPIC_3.getGuid())));

        when(mockTopicDao.getTopic(TEST_STUDY, MANUAL_TOPIC_1.getGuid())).thenReturn(MANUAL_TOPIC_1);
        when(mockTopicDao.getTopic(TEST_STUDY, MANUAL_TOPIC_2.getGuid())).thenReturn(MANUAL_TOPIC_2);
        when(mockTopicDao.getTopic(TEST_STUDY, MANUAL_TOPIC_3.getGuid())).thenReturn(MANUAL_TOPIC_3);

        // To test error handling, unsubscribing from topic 2 will throw.
        doThrow(RuntimeException.class).when(mockSubscriptionDao).unsubscribe(SMS_REGISTRATION, MANUAL_TOPIC_2);

        // Execute.
        service.unsubscribeAll(TEST_STUDY, HEALTH_CODE, SMS_REGISTRATION.getGuid());

        // Verify we unsubscribe from all 3 topics.
        verify(mockSubscriptionDao).unsubscribe(SMS_REGISTRATION, MANUAL_TOPIC_1);
        verify(mockSubscriptionDao).unsubscribe(SMS_REGISTRATION, MANUAL_TOPIC_2);
        verify(mockSubscriptionDao).unsubscribe(SMS_REGISTRATION, MANUAL_TOPIC_3);
    }

    @Test
    public void subscribe_NoManualTopics() {
        // Topic list includes only criteria subscriptions.
        when(mockTopicDao.listTopics(TEST_STUDY, false)).thenReturn(ImmutableList.of(CRITERIA_TOPIC_1, CRITERIA_TOPIC_2));

        // Execute test. We pass in criteria topic guids for test purposes, but these are ignored.
        List<SubscriptionStatus> statusList = service.subscribe(TEST_STUDY, HEALTH_CODE, PUSH_REGISTRATION.getGuid(),
                ImmutableSet.of(CRITERIA_TOPIC_1.getGuid(), CRITERIA_TOPIC_2.getGuid()));
        assertTrue(statusList.isEmpty());

        // No subscription changes.
        verifyZeroInteractions(mockSubscriptionDao);
    }

    @Test
    public void subscribe() {
        // 4 cases:
        //   1. Subscribed and we stay subscribed.
        //   2. Subscribed and we unsubscribe.
        //   3. Not subscribed and we subscribe.
        //   4. Not subscribed and we stay unsubscribed.

        // 2 criteria-based topics, just to make sure we ignore those. 4 manual topics.
        when(mockTopicDao.listTopics(TEST_STUDY, false)).thenReturn(ImmutableList.of(CRITERIA_TOPIC_1, CRITERIA_TOPIC_2,
                MANUAL_TOPIC_1, MANUAL_TOPIC_2, MANUAL_TOPIC_3, MANUAL_TOPIC_4));

        // Mock registration dao.
        when(mockRegistrationDao.getRegistration(HEALTH_CODE, PUSH_REGISTRATION.getGuid())).thenReturn(
                PUSH_REGISTRATION);

        // We are currently subscribed to 1 and 2.
        when(mockSubscriptionDao.listSubscriptions(PUSH_REGISTRATION)).thenReturn((List)ImmutableList.of(
                getSub(MANUAL_TOPIC_1.getGuid()), getSub(MANUAL_TOPIC_2.getGuid())));

        // Execute. We want to subscribe to 1 and 3.
        List<SubscriptionStatus> statusList = service.subscribe(TEST_STUDY, HEALTH_CODE, PUSH_REGISTRATION.getGuid(),
                ImmutableSet.of(MANUAL_TOPIC_1.getGuid(), MANUAL_TOPIC_3.getGuid()));
        assertEquals(4, statusList.size());

        assertEquals(MANUAL_TOPIC_1.getGuid(), statusList.get(0).getTopicGuid());
        assertEquals(MANUAL_TOPIC_1.getName(), statusList.get(0).getTopicName());
        assertTrue(statusList.get(0).isSubscribed());

        assertEquals(MANUAL_TOPIC_2.getGuid(), statusList.get(1).getTopicGuid());
        assertEquals(MANUAL_TOPIC_2.getName(), statusList.get(1).getTopicName());
        assertFalse(statusList.get(1).isSubscribed());

        assertEquals(MANUAL_TOPIC_3.getGuid(), statusList.get(2).getTopicGuid());
        assertEquals(MANUAL_TOPIC_3.getName(), statusList.get(2).getTopicName());
        assertTrue(statusList.get(2).isSubscribed());

        assertEquals(MANUAL_TOPIC_4.getGuid(), statusList.get(3).getTopicGuid());
        assertEquals(MANUAL_TOPIC_4.getName(), statusList.get(3).getTopicName());
        assertFalse(statusList.get(3).isSubscribed());

        // Verify back-ends. We unsub from topic 2 and sub to topic 3.
        verify(mockSubscriptionDao).unsubscribe(PUSH_REGISTRATION, MANUAL_TOPIC_2);
        verify(mockSubscriptionDao).subscribe(PUSH_REGISTRATION, MANUAL_TOPIC_3);

        // We don't touch topics 1 or 4 or either of the criteria topics.
        verify(mockSubscriptionDao, never()).unsubscribe(any(), eq(MANUAL_TOPIC_1));
        verify(mockSubscriptionDao, never()).unsubscribe(any(), eq(MANUAL_TOPIC_4));
        verify(mockSubscriptionDao, never()).unsubscribe(any(), eq(CRITERIA_TOPIC_1));
        verify(mockSubscriptionDao, never()).unsubscribe(any(), eq(CRITERIA_TOPIC_2));

        verify(mockSubscriptionDao, never()).subscribe(any(), eq(MANUAL_TOPIC_1));
        verify(mockSubscriptionDao, never()).subscribe(any(), eq(MANUAL_TOPIC_4));
        verify(mockSubscriptionDao, never()).subscribe(any(), eq(CRITERIA_TOPIC_1));
        verify(mockSubscriptionDao, never()).subscribe(any(), eq(CRITERIA_TOPIC_2));
    }
}
