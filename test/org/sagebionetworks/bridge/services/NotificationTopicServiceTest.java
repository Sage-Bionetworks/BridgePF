package org.sagebionetworks.bridge.services;

import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestUtils.getNotificationTopic;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
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
import org.sagebionetworks.bridge.dao.NotificationTopicDao;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.notifications.NotificationMessage;
import org.sagebionetworks.bridge.models.notifications.NotificationTopic;

import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.PublishRequest;
import com.google.common.collect.Lists;

@RunWith(MockitoJUnitRunner.class)
public class NotificationTopicServiceTest {

    @Mock
    private NotificationTopicDao mockTopicDao;
    
    @Mock
    private AmazonSNSClient mockSnsClient;
    
    @Captor
    private ArgumentCaptor<PublishRequest> publishRequestCaptor;
    
    private NotificationTopicService service;
    
    @Before
    public void before() {
        service = new NotificationTopicService();
        service.setNotificationTopicDao(mockTopicDao);
        service.setSnsClient(mockSnsClient);
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
}
