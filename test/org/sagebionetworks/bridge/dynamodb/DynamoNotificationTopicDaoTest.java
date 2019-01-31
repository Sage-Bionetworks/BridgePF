package org.sagebionetworks.bridge.dynamodb;

import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_IDENTIFIER;
import static org.sagebionetworks.bridge.TestUtils.getNotificationTopic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.Environment;
import org.sagebionetworks.bridge.dao.CriteriaDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.time.DateUtils;
import org.sagebionetworks.bridge.models.notifications.NotificationTopic;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.CreateTopicResult;
import com.amazonaws.services.sns.model.DeleteTopicRequest;
import com.google.common.collect.Lists;

@RunWith(MockitoJUnitRunner.class)
public class DynamoNotificationTopicDaoTest {
    private static final Set<String> ALL_OF_GROUP_SET = ImmutableSet.of("group1", "group2");
    private static final String GUID_WITH_CRITERIA = "topic-guid-with-criteria";
    private static final String GUID_WITHOUT_CRITERIA = "topic-guid-without-criteria";
    private static final String TOPIC_ARN = "topic-arn";

    private static void assertCriteria(String expectedTopicGuid, Criteria criteria) {
        assertEquals(DynamoNotificationTopicDao.CRITERIA_KEY_PREFIX + expectedTopicGuid, criteria.getKey());
        assertEquals(ALL_OF_GROUP_SET, criteria.getAllOfGroups());
    }

    // Helper method to make a criteria. Note that we can't just make this a constant, because the DAO actually
    // modifies this.
    private static Criteria makeCriteria() {
        Criteria criteria = Criteria.create();
        criteria.setKey(DynamoNotificationTopicDao.CRITERIA_KEY_PREFIX + GUID_WITH_CRITERIA);
        criteria.setAllOfGroups(ImmutableSet.of("group1", "group2"));
        return criteria;
    }
    
    @Mock
    private CriteriaDao mockCriteriaDao;
    
    @Mock
    private DynamoDBMapper mockMapper;
    
    @Mock
    private AmazonSNSClient mockSnsClient;

    @Mock
    private BridgeConfig mockConfig;
    
    @Mock
    private QueryResultPage<DynamoNotificationTopic> mockQueryResultPage;
    
    @Captor
    private ArgumentCaptor<DynamoNotificationTopic> topicCaptor;
    
    @Captor
    private ArgumentCaptor<DeleteTopicRequest> deleteTopicRequestCaptor;
    
    @Captor
    private ArgumentCaptor<DynamoDBQueryExpression<DynamoNotificationTopic>> queryExpressionCaptor;
    
    private DynamoNotificationTopicDao dao;
    
    @Before
    public void before() {
        // Mock criteria DAO.
        when(mockCriteriaDao.getCriteria(DynamoNotificationTopicDao.CRITERIA_KEY_PREFIX + GUID_WITH_CRITERIA))
                .thenReturn(makeCriteria());

        // Set up topic DAO.
        dao = new DynamoNotificationTopicDao();
        dao.setCriteriaDao(mockCriteriaDao);
        dao.setNotificationTopicMapper(mockMapper);
        dao.setSnsClient(mockSnsClient);
        dao.setBridgeConfig(mockConfig);

        // Mock config.
        doReturn(Environment.LOCAL).when(mockConfig).getEnvironment();
    }
    
    @Test
    public void createTopic() {
        // Mock SNS client.
        when(mockSnsClient.createTopic(anyString())).thenReturn(new CreateTopicResult().withTopicArn(TOPIC_ARN));

        // Execute.
        NotificationTopic topic = getNotificationTopic();
        topic.setCriteria(null);
        NotificationTopic saved = dao.createTopic(topic);

        // Verify SNS calls.
        verify(mockSnsClient).createTopic("api-local-" + saved.getGuid());
        verify(mockSnsClient).setTopicAttributes(TOPIC_ARN, DynamoNotificationTopicDao.ATTR_DISPLAY_NAME,
                "Short Name");

        // Verify DDB mapper.
        verify(mockMapper).save(topicCaptor.capture());
        DynamoNotificationTopic captured = topicCaptor.getValue();
        assertEquals(topic.getName(), captured.getName());
        assertEquals(topic.getDescription(), captured.getDescription());
        assertEquals(topic.getStudyId(), captured.getStudyId());
        assertEquals(TOPIC_ARN, captured.getTopicARN());
        assertEquals(topic.getGuid(), captured.getGuid());
        assertTrue(captured.getCreatedOn() > 0);
        assertTrue(captured.getModifiedOn() > 0);
        // This was set by the methods. Can't be set by the caller.
        assertNotEquals(getNotificationTopic().getGuid(), captured.getGuid());

        // This topic has no criteria.
        assertNull(captured.getCriteria());
        verifyZeroInteractions(mockCriteriaDao);
    }
    
    @Test
    public void createTopicWithCriteria() {
        // Mock SNS client.
        when(mockSnsClient.createTopic(anyString())).thenReturn(new CreateTopicResult().withTopicArn(TOPIC_ARN));

        // Execute.
        NotificationTopic topic = getNotificationTopic();
        topic.setCriteria(makeCriteria());
        dao.createTopic(topic);

        // Verify that the saved topic has the right criteria. (Everything else is tested elsewhere.)
        verify(mockMapper).save(topicCaptor.capture());
        DynamoNotificationTopic captured = topicCaptor.getValue();
        assertCriteria(captured.getGuid(), captured.getCriteria());

        // Verify the saved criteria is correct.
        ArgumentCaptor<Criteria> savedCriteriaCaptor = ArgumentCaptor.forClass(Criteria.class);
        verify(mockCriteriaDao).createOrUpdateCriteria(savedCriteriaCaptor.capture());
        assertCriteria(captured.getGuid(), savedCriteriaCaptor.getValue());
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
    
    @Test
    public void deleteTopic() {
        NotificationTopic existingTopic = getNotificationTopic();
        when(mockMapper.load(any())).thenReturn(existingTopic);
        
        dao.deleteTopic(TEST_STUDY, "ABC-DEF");
        
        verify(mockMapper).save(topicCaptor.capture());
        assertTrue(topicCaptor.getValue().isDeleted());
    }

    @Test(expected = EntityNotFoundException.class)
    public void deleteTopicAlreadyDeleted() {
        NotificationTopic existingTopic = getNotificationTopic();
        existingTopic.setDeleted(true);
        when(mockMapper.load(any())).thenReturn(existingTopic);
        
        dao.deleteTopic(TEST_STUDY, "ABC-DEF");
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void deleteTopicNotFound() {
        dao.deleteTopic(TEST_STUDY, "ABC-DEF");
    }
    
    @Test
    public void deleteTopicPermanently() {
        NotificationTopic topic = getNotificationTopic();
        doReturn(topic).when(mockMapper).load(any());

        dao.deleteTopicPermanently(TEST_STUDY, "anything");
        
        verify(mockSnsClient).deleteTopic(deleteTopicRequestCaptor.capture());
        DeleteTopicRequest capturedRequest = deleteTopicRequestCaptor.getValue();
        assertEquals(topic.getTopicARN(), capturedRequest.getTopicArn());
        
        verify(mockMapper).delete(topicCaptor.capture());
        NotificationTopic capturedTopic = topicCaptor.getValue();
        assertEquals(TEST_STUDY_IDENTIFIER, capturedTopic.getStudyId());
        assertEquals("anything", capturedTopic.getGuid());
    }

    @Test(expected = EntityNotFoundException.class)
    public void deleteTopicPermanentlyNotFound() {
        dao.deleteTopicPermanently(TEST_STUDY, "anything");
    }

    @Test
    public void deleteTopicPermanentlyWithCriteria() {
        // Mock existing topic with criteria. Note that criteria is loaded in a separate table.
        NotificationTopic existingTopic = getNotificationTopic();
        existingTopic.setGuid(GUID_WITH_CRITERIA);
        existingTopic.setCriteria(null);
        when(mockMapper.load(any())).thenReturn(existingTopic);

        // Execute.
        dao.deleteTopicPermanently(TEST_STUDY, GUID_WITH_CRITERIA);

        // Verify criteria DAO.
        verify(mockCriteriaDao).deleteCriteria(DynamoNotificationTopicDao.CRITERIA_KEY_PREFIX +
                GUID_WITH_CRITERIA);
    }
    
    @Test
    public void getTopic() {
        NotificationTopic topic = getNotificationTopic();
        topic.setCriteria(null);
        doReturn(topic).when(mockMapper).load(any());
        
        NotificationTopic updated = dao.getTopic(TEST_STUDY, topic.getGuid());
        assertEquals(TEST_STUDY_IDENTIFIER, updated.getStudyId());
        assertEquals("topicGuid", updated.getGuid());
        assertNull(updated.getCriteria());
        
        verify(mockMapper).load(topicCaptor.capture());
        DynamoNotificationTopic capturedTopic = topicCaptor.getValue();
        assertEquals(updated.getStudyId(), capturedTopic.getStudyId());
        assertEquals(updated.getGuid(), capturedTopic.getGuid());
    }
    
    @Test(expected = EntityNotFoundException.class)
    public void getTopicNotFound() {
        dao.getTopic(TEST_STUDY, getNotificationTopic().getGuid());
    }
    
    @Test
    public void getTopicWithCriteria() {
        // Mock mapper.
        NotificationTopic topic = getNotificationTopic();
        topic.setGuid(GUID_WITH_CRITERIA);
        topic.setCriteria(null);
        when(mockMapper.load(any())).thenReturn(topic);

        // Execute and validate.
        NotificationTopic result = dao.getTopic(TEST_STUDY, GUID_WITH_CRITERIA);
        assertEquals(TEST_STUDY_IDENTIFIER, result.getStudyId());
        assertEquals(GUID_WITH_CRITERIA, result.getGuid());
        assertCriteria(GUID_WITH_CRITERIA, result.getCriteria());
    }
    
    @Test
    public void listTopicsExcludeDeleted() {
        mockListCall();

        // Execute and verify.
        dao.listTopics(TEST_STUDY, false);

        // Verify query.
        verify(mockMapper).queryPage(eq(DynamoNotificationTopic.class), queryExpressionCaptor.capture());
        
        // Query expression does include filter of deleted items.
        DynamoDBQueryExpression<DynamoNotificationTopic> capturedQuery = queryExpressionCaptor.getValue();
        assertEquals("{AttributeValueList: [{N: 1,}],ComparisonOperator: NE}",
                capturedQuery.getQueryFilter().get("deleted").toString());
    }
    
    @Test
    public void listTopicsIncludeDeleted() {
        // Mock DDB to return topics.
        DynamoNotificationTopic topicWithoutCriteria = (DynamoNotificationTopic) getNotificationTopic();
        topicWithoutCriteria.setGuid(GUID_WITHOUT_CRITERIA);
        topicWithoutCriteria.setCriteria(null);

        DynamoNotificationTopic topicWithCriteria = (DynamoNotificationTopic) getNotificationTopic();
        topicWithCriteria.setGuid(GUID_WITH_CRITERIA);
        topicWithCriteria.setCriteria(null);

        mockListCall(topicWithoutCriteria, topicWithCriteria);

        // Execute and verify.
        List<NotificationTopic> topics = dao.listTopics(TEST_STUDY, true);
        assertEquals(2, topics.size());

        assertEquals(GUID_WITHOUT_CRITERIA, topics.get(0).getGuid());
        assertNull(topics.get(0).getCriteria());

        assertEquals(GUID_WITH_CRITERIA, topics.get(1).getGuid());
        assertCriteria(GUID_WITH_CRITERIA, topics.get(1).getCriteria());

        // Verify query.
        verify(mockMapper).queryPage(eq(DynamoNotificationTopic.class), queryExpressionCaptor.capture());
        
        DynamoDBQueryExpression<DynamoNotificationTopic> capturedQuery = queryExpressionCaptor.getValue();
        DynamoNotificationTopic capturedTopic = capturedQuery.getHashKeyValues();
        assertEquals(TEST_STUDY_IDENTIFIER, capturedTopic.getStudyId());
        assertNull(capturedTopic.getGuid());
        assertNull(capturedQuery.getQueryFilter()); // deleted are not being filtered out
    }

    // Helper method to mock the list API.
    private void mockListCall(DynamoNotificationTopic... topics) {
        List<DynamoNotificationTopic> topicList = ImmutableList.copyOf(topics);
        when(mockQueryResultPage.getResults()).thenReturn(topicList);
        when(mockMapper.queryPage(eq(DynamoNotificationTopic.class), any())).thenReturn(mockQueryResultPage);
    }
    
    // If the SNS topic creation fails, no DDB record will exist.
    // (okay if SNS topic exists, but DDB record doesn't, so don't need to test that path)
    @Test
    public void noOrphanOnPartialCreate() {
        doThrow(new RuntimeException()).when(mockSnsClient).createTopic(any(String.class));
        
        try {
            dao.createTopic(getNotificationTopic());
            fail("Should have thrown exception");
        } catch(RuntimeException e) {
            // expected exception
        }
        verify(mockMapper, never()).save(any());
    }
    
    
    // If the DDB record fails to delete, the SNS record will still be there.
    @Test
    public void noOrphanOnPermanentDeleteWhereDDBFails() {
        doReturn(getNotificationTopic()).when(mockMapper).load(any());
        doThrow(new RuntimeException()).when(mockMapper).delete(any());
        
        try {
            dao.deleteTopicPermanently(TEST_STUDY, "guid");
            fail("Should have thrown exception");
        } catch(RuntimeException e) {
            // expected exception
        }
        verify(mockSnsClient, never()).deleteTopic(any(DeleteTopicRequest.class));
    }
    
    // If the SNS record fails to delete, the DDB record will still be there.
    @Test
    public void noOrphanOnPermanentDeleteWhereSNSFails() {
        doReturn(getNotificationTopic()).when(mockMapper).load(any());
        doThrow(new AmazonServiceException("error")).when(mockSnsClient).deleteTopic(any(DeleteTopicRequest.class));
        
        dao.deleteTopicPermanently(TEST_STUDY, "guid");
        
        verify(mockMapper).delete(any());
    }
    
    @Test
    public void updateTopic() throws Exception {
        long timestamp = DateUtils.getCurrentMillisFromEpoch();
        
        NotificationTopic persistedTopic = getNotificationTopic();
        persistedTopic.setCreatedOn(timestamp);
        persistedTopic.setModifiedOn(timestamp);
        doReturn(persistedTopic).when(mockMapper).load(any());
        
        NotificationTopic topic = getNotificationTopic();
        topic.setName("The updated name");
        topic.setDescription("The updated description");
        
        Thread.sleep(3); // forced modifiedOn to be different from timestamp
        
        NotificationTopic updated = dao.updateTopic(topic);
        assertEquals("The updated name", updated.getName());
        assertEquals("The updated description", updated.getDescription());
        assertEquals(timestamp, updated.getCreatedOn());
        assertNotEquals(timestamp, updated.getModifiedOn());
        
        verify(mockMapper).save(topicCaptor.capture());
        NotificationTopic captured = topicCaptor.getValue();
        assertEquals("The updated name", captured.getName());
        assertEquals("The updated description", captured.getDescription());
        assertEquals(topic.getTopicARN(), captured.getTopicARN());
    }

    @Test
    public void updateTopicCanAddCriteria() {
        // Mock existing topic with no criteria. Note that criteria is loaded in a separate table (but is not present
        // in the ohter table).
        NotificationTopic existingTopic = getNotificationTopic();
        existingTopic.setGuid(GUID_WITHOUT_CRITERIA);
        existingTopic.setCriteria(null);
        when(mockMapper.load(any())).thenReturn(existingTopic);

        // Execute. Submit a topic with criteria.
        NotificationTopic topicToUpdate = getNotificationTopic();
        topicToUpdate.setGuid(GUID_WITHOUT_CRITERIA);
        topicToUpdate.setCriteria(makeCriteria());
        dao.updateTopic(topicToUpdate);

        // Verify that the saved topic has the right criteria. (Everything else is tested elsewhere.)
        verify(mockMapper).save(topicCaptor.capture());
        NotificationTopic savedTopic = topicCaptor.getValue();
        assertCriteria(GUID_WITHOUT_CRITERIA, savedTopic.getCriteria());

        // Verify the saved criteria is correct.
        ArgumentCaptor<Criteria> savedCriteriaCaptor = ArgumentCaptor.forClass(Criteria.class);
        verify(mockCriteriaDao).createOrUpdateCriteria(savedCriteriaCaptor.capture());
        assertCriteria(GUID_WITHOUT_CRITERIA, savedCriteriaCaptor.getValue());
    }

    @Test
    public void updateTopicCanRemoveCriteria() {
        // Mock existing topic with criteria. Note that criteria is loaded in a separate table.
        NotificationTopic existingTopic = getNotificationTopic();
        existingTopic.setGuid(GUID_WITH_CRITERIA);
        existingTopic.setCriteria(null);
        when(mockMapper.load(any())).thenReturn(existingTopic);

        // Execute. The topic we submit has no criteria.
        NotificationTopic topicToUpdate = getNotificationTopic();
        topicToUpdate.setGuid(GUID_WITH_CRITERIA);
        topicToUpdate.setCriteria(null);
        dao.updateTopic(topicToUpdate);

        // Saved topic has no criteria.
        verify(mockMapper).save(topicCaptor.capture());
        NotificationTopic savedTopic = topicCaptor.getValue();
        assertNull(savedTopic.getCriteria());

        // Criteria DAO is never called to save the criteria.
        verify(mockCriteriaDao, never()).createOrUpdateCriteria(any());
    }

    @Test(expected = EntityNotFoundException.class)
    public void updateTopicNotFound() {
        dao.updateTopic(getNotificationTopic());
    }
}
