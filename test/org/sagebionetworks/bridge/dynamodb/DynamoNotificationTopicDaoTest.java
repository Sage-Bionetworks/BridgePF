package org.sagebionetworks.bridge.dynamodb;

import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;
import static org.sagebionetworks.bridge.TestUtils.getNotificationTopic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;


import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.notifications.NotificationTopic;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.CreateTopicRequest;
import com.amazonaws.services.sns.model.CreateTopicResult;
import com.amazonaws.services.sns.model.DeleteTopicRequest;
import com.google.common.collect.Lists;

@RunWith(MockitoJUnitRunner.class)
public class DynamoNotificationTopicDaoTest {

    @Mock
    private DynamoDBMapper mockMapper;
    
    @Mock
    private AmazonSNSClient mockSnsClient;
    
    @Mock
    private BridgeConfig mockConfig;
    
    @Mock
    private CreateTopicResult mockCreateTopicResult;
    
    @Mock
    private QueryResultPage<DynamoNotificationTopic> mockQueryResultPage;
    
    @Mock
    private PaginatedQueryList<DynamoNotificationTopic> mockPaginatedQueryList;
    
    @Captor
    private ArgumentCaptor<CreateTopicRequest> createTopicRequestCaptor;
    
    @Captor
    private ArgumentCaptor<CreateTopicResult> createTopicResultCaptor;
    
    @Captor
    private ArgumentCaptor<DynamoNotificationTopic> topicCaptor;
    
    @Captor
    private ArgumentCaptor<DeleteTopicRequest> deleteTopicRequestCaptor;
    
    @Captor
    private ArgumentCaptor<DynamoDBQueryExpression<DynamoNotificationTopic>> queryExpressionCaptor;
    
    private DynamoNotificationTopicDao dao;
    
    @Before
    public void before() {
        dao = new DynamoNotificationTopicDao();
        dao.setNotificationTopicMapper(mockMapper);
        dao.setSnsClient(mockSnsClient);
        dao.setBridgeConfig(mockConfig);
        
        doReturn(Environment.LOCAL).when(mockConfig).getEnvironment();
    }
    
    @Test
    public void listTopics() {
        List<NotificationTopic> results = Lists.newArrayList(getNotificationTopic());
        doReturn(results).when(mockQueryResultPage).getResults();
        doReturn(mockQueryResultPage).when(mockMapper).queryPage(eq(DynamoNotificationTopic.class), any());
        
        List<NotificationTopic> topics = dao.listTopics(TEST_STUDY);
        assertEquals(1, topics.size());
        
        verify(mockMapper).queryPage(eq(DynamoNotificationTopic.class), queryExpressionCaptor.capture());
        
        DynamoDBQueryExpression<DynamoNotificationTopic> capturedQuery = queryExpressionCaptor.getValue();
        DynamoNotificationTopic capturedTopic = capturedQuery.getHashKeyValues();
        assertEquals(TEST_STUDY_IDENTIFIER, capturedTopic.getStudyId());
        assertNull(capturedTopic.getGuid());
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void getTopicNotFound() {
        dao.getTopic(TEST_STUDY, getNotificationTopic().getGuid());
    }
    
    @Test
    public void getTopic() {
        NotificationTopic topic = getNotificationTopic();
        doReturn(topic).when(mockMapper).load(any());
        
        NotificationTopic updated = dao.getTopic(TEST_STUDY, topic.getGuid());
        assertEquals(TEST_STUDY_IDENTIFIER, updated.getStudyId());
        assertEquals("ABC-DEF", updated.getGuid());
        
        verify(mockMapper).load(topicCaptor.capture());
        DynamoNotificationTopic capturedTopic = topicCaptor.getValue();
        assertEquals(updated.getStudyId(), capturedTopic.getStudyId());
        assertEquals(updated.getGuid(), capturedTopic.getGuid());
    }
    
    @Test
    public void createTopic() {
        doReturn("new-topic-arm").when(mockCreateTopicResult).getTopicArn();
        doReturn(mockCreateTopicResult).when(mockSnsClient).createTopic(any(CreateTopicRequest.class));
        
        NotificationTopic topic = getNotificationTopic();
        
        NotificationTopic saved = dao.createTopic(topic);
        
        verify(mockSnsClient).createTopic(createTopicRequestCaptor.capture());
        CreateTopicRequest request = createTopicRequestCaptor.getValue();
        assertEquals("api-local-"+saved.getGuid(), request.getName());
        
        verify(mockMapper).save(topicCaptor.capture());
        DynamoNotificationTopic captured = topicCaptor.getValue();
        assertEquals(topic.getName(), captured.getName());
        assertEquals(topic.getStudyId(), captured.getStudyId());
        assertEquals("new-topic-arm", captured.getTopicARN());
        assertEquals(topic.getGuid(), captured.getGuid());
        // This was set by the methods. Can't be set by the caller.
        assertNotEquals(getNotificationTopic().getGuid(), captured.getGuid());
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void updateTopicNotFound() {
        dao.updateTopic(TEST_STUDY, getNotificationTopic());
    }
    
    @Test
    public void updateTopic() {
        doReturn(getNotificationTopic()).when(mockMapper).load(any());
        
        NotificationTopic topic = getNotificationTopic();
        topic.setName("The updated name");
        
        NotificationTopic updated = dao.updateTopic(TEST_STUDY, topic);
        assertEquals("The updated name", updated.getName());
        
        verify(mockMapper).save(topicCaptor.capture());
        NotificationTopic captured = topicCaptor.getValue();
        assertEquals("The updated name", captured.getName());
        assertEquals(topic.getTopicARN(), captured.getTopicARN());
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void deleteTopicNotFound() {
        dao.deleteTopic(TEST_STUDY, "anything");
    }
    
    @Test
    public void deleteTopic() {
        NotificationTopic topic = getNotificationTopic();
        doReturn(topic).when(mockMapper).load(any());

        dao.deleteTopic(TEST_STUDY, "anything");
        
        verify(mockSnsClient).deleteTopic(deleteTopicRequestCaptor.capture());
        DeleteTopicRequest capturedRequest = deleteTopicRequestCaptor.getValue();
        assertEquals(topic.getTopicARN(), capturedRequest.getTopicArn());
        
        verify(mockMapper).delete(topicCaptor.capture());
        NotificationTopic capturedTopic = topicCaptor.getValue();
        assertEquals(TEST_STUDY_IDENTIFIER, capturedTopic.getStudyId());
        assertEquals("anything", capturedTopic.getGuid());
    }
    
    @Test
    public void deleteAllTopics() {
        NotificationTopic topic1 = getNotificationTopic();
        NotificationTopic topic2 = getNotificationTopic();
        topic2.setGuid("GHI-JKL");
        topic2.setTopicARN("other:topic:arn");
        
        when(mockMapper.load(any())).thenReturn(topic1, topic2);
        
        List<NotificationTopic> results = Lists.newArrayList(topic1, topic2);
        doReturn(results).when(mockQueryResultPage).getResults();
        doReturn(mockQueryResultPage).when(mockMapper).queryPage(eq(DynamoNotificationTopic.class), any());
        
        dao.deleteAllTopics(TEST_STUDY);
        
        verify(mockMapper, times(2)).delete(topicCaptor.capture());
        NotificationTopic captured = topicCaptor.getAllValues().get(0);
        assertEquals(topic1.getStudyId(), captured.getStudyId());
        assertEquals(topic1.getGuid(), captured.getGuid());
        
        captured = topicCaptor.getAllValues().get(1);
        assertEquals(topic2.getStudyId(), captured.getStudyId());
        assertEquals(topic2.getGuid(), captured.getGuid());
        
        verify(mockSnsClient, times(2)).deleteTopic(deleteTopicRequestCaptor.capture());
        DeleteTopicRequest request = deleteTopicRequestCaptor.getAllValues().get(0);
        assertEquals(topic1.getTopicARN(), request.getTopicArn());
        
        request = deleteTopicRequestCaptor.getAllValues().get(1);
        assertEquals(topic2.getTopicARN(), request.getTopicArn());
    }
    
    
    // If the SNS topic creation fails, no DDB record will exist.
    // (okay if SNS topic exists, but DDB record doesn't, so don't need to test that path)
    @Test
    public void noOrphanOnPartialCreate() {
        doThrow(new RuntimeException()).when(mockSnsClient).createTopic(any(CreateTopicRequest.class));
        
        try {
            dao.createTopic(getNotificationTopic());
            fail("Should have thrown exception");
        } catch(RuntimeException e) {
        }
        verify(mockMapper, never()).save(any());
    }
    
    // If the SNS record fails to delete, the DDB record will still be there.
    @Test
    public void noOrphanOnDeleteWhereSNSFails() {
        doReturn(getNotificationTopic()).when(mockMapper).load(any());
        doThrow(new AmazonServiceException("error")).when(mockSnsClient).deleteTopic(any(DeleteTopicRequest.class));
        
        dao.deleteTopic(TEST_STUDY, "guid");
        
        verify(mockMapper).delete(any());
    }
    
    // If the DDB record fails to delete, the SNS record will still be there.
    @Test
    public void noOrphanOnDeleteWhereDDBFails() {
        doReturn(getNotificationTopic()).when(mockMapper).load(any());
        doThrow(new RuntimeException()).when(mockMapper).delete(any());
        
        try {
            dao.deleteTopic(TEST_STUDY, "guid");
            fail("Should have thrown exception");
        } catch(RuntimeException e) {
        }
        verify(mockSnsClient, never()).deleteTopic(any(DeleteTopicRequest.class));
    }
}
