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
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;


import org.sagebionetworks.bridge.dao.NotificationTopicDao;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.notifications.NotificationTopic;

import com.google.common.collect.Lists;

@RunWith(MockitoJUnitRunner.class)
public class NotificationTopicServiceTest {

    @Mock
    private NotificationTopicDao topicDao;
    
    private NotificationTopicService service;
    
    @Before
    public void before() {
        service = new NotificationTopicService();
        service.setNotificationTopicDao(topicDao);
    }
    
    @Test
    public void listTopics() {
        List<NotificationTopic> list = Lists.newArrayList(getNotificationTopic(), getNotificationTopic());
        doReturn(list).when(topicDao).listTopics(TEST_STUDY);
        
        List<NotificationTopic> results = service.listTopics(TEST_STUDY);
        assertEquals(2, results.size());
        
        verify(topicDao).listTopics(TEST_STUDY);
    }
    
    @Test
    public void getTopic() {
        NotificationTopic topic = getNotificationTopic();
        doReturn(topic).when(topicDao).getTopic(TEST_STUDY, topic.getGuid());
        
        NotificationTopic result = service.getTopic(TEST_STUDY, topic.getGuid());
        assertEquals(topic, result);
        
        verify(topicDao).getTopic(TEST_STUDY, topic.getGuid());
    }
    
    @Test
    public void createTopic() {
        NotificationTopic topic = getNotificationTopic();
        doReturn(topic).when(topicDao).createTopic(topic);
        
        NotificationTopic result = service.createTopic(TEST_STUDY, topic);
        assertEquals(topic, result);
        
        verify(topicDao).createTopic(topic);
    }
    
    @Test
    public void createdTopicValidatesObject() {
        NotificationTopic topic = getNotificationTopic();
        topic.setName(null);
        try {
            service.createTopic(TEST_STUDY, topic);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            verify(topicDao, never()).createTopic(topic);
        }
    }
    
    @Test
    public void updateTopic() {
        NotificationTopic topic = getNotificationTopic();
        doReturn(topic).when(topicDao).updateTopic(TEST_STUDY, topic);
        
        NotificationTopic result = service.updateTopic(TEST_STUDY, topic);
        assertEquals(topic, result);
        
        verify(topicDao).updateTopic(TEST_STUDY, topic);
    }
    
    @Test
    public void updateTopicValidatesObject() {
        NotificationTopic topic = getNotificationTopic();
        topic.setName(null);
        try {
            service.updateTopic(TEST_STUDY, topic);
            fail("Should have thrown exception");
        } catch(InvalidEntityException e) {
            verify(topicDao, never()).updateTopic(TEST_STUDY, topic);
        }        
    }
    
    @Test
    public void deleteTopic() {
        service.deleteTopic(TEST_STUDY, "ABC-DEF");
        
        verify(topicDao).deleteTopic(TEST_STUDY, "ABC-DEF");
    }
    
    @Test
    public void deleteAllTopics() {
        service.deleteAllTopics(TEST_STUDY);
        
        verify(topicDao).deleteAllTopics(TEST_STUDY);
    }
    
}
