package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.util.BridgeCollectors.toImmutableList;

import java.util.List;

import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dao.NotificationTopicDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.notifications.NotificationTopic;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.services.NotificationsService;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.CreateTopicRequest;
import com.amazonaws.services.sns.model.CreateTopicResult;
import com.amazonaws.services.sns.model.DeleteTopicRequest;

@Component
public class DynamoNotificationTopicDao implements NotificationTopicDao {
    private static Logger LOG = LoggerFactory.getLogger(DynamoNotificationTopicDao.class);
    
    private DynamoDBMapper mapper;
    
    private AmazonSNSClient snsClient;
    
    private BridgeConfig config;
    
    @Resource(name = "notificationTopicMapper")
    final void setNotificationTopicMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }
    
    @Resource(name = "snsClient")
    final void setSnsClient(AmazonSNSClient snsClient) {
        this.snsClient = snsClient;
    }

    @Resource(name = "bridgeConfig")
    public void setBridgeConfig(BridgeConfig bridgeConfig) {
        this.config = bridgeConfig;
    }
    
    @Override
    public List<NotificationTopic> listTopics(StudyIdentifier studyId) {
        checkNotNull(studyId);
        
        DynamoNotificationTopic hashKey = new DynamoNotificationTopic();
        hashKey.setStudyId(studyId.getIdentifier());

        DynamoDBQueryExpression<DynamoNotificationTopic> query = new DynamoDBQueryExpression<DynamoNotificationTopic>()
                .withConsistentRead(false).withHashKeyValues(hashKey);

        QueryResultPage<DynamoNotificationTopic> resultPage = mapper.queryPage(DynamoNotificationTopic.class, query);
        
        return resultPage.getResults().stream().map(obj -> (NotificationTopic) obj).collect(toImmutableList());
    }

    @Override
    public NotificationTopic getTopic(StudyIdentifier studyId, String guid) {
        checkNotNull(studyId);
        checkNotNull(guid);
        
        DynamoNotificationTopic hashKey = new DynamoNotificationTopic();
        hashKey.setStudyId(studyId.getIdentifier());
        hashKey.setGuid(guid);

        DynamoNotificationTopic topic = mapper.load(hashKey);
        if (topic == null) {
            throw new EntityNotFoundException(NotificationTopic.class);
        }
        
        return topic;
    }

    @Override
    public NotificationTopic createTopic(NotificationTopic topic) {
        checkNotNull(topic);
        
        // Create SNS topic first, if DDB call fails, there's an orphaned topic 
        // but Bridge isn't in an inconsistent state.
        topic.setGuid(BridgeUtils.generateGuid());
        
        String snsTopicName = createSnsTopicName(topic);
        CreateTopicRequest request = new CreateTopicRequest().withName(snsTopicName);
        CreateTopicResult result = snsClient.createTopic(request);
        topic.setTopicARN(result.getTopicArn());
        
        mapper.save(topic);
        return topic;
    }

    @Override
    public NotificationTopic updateTopic(StudyIdentifier studyId, NotificationTopic topic) {
        checkNotNull(topic);
        checkNotNull(topic.getGuid());

        NotificationTopic existing = getTopic(studyId, topic.getGuid());
        existing.setName(topic.getName());
        
        mapper.save(existing);
        
        return existing;
    }

    @Override
    public void deleteTopic(StudyIdentifier studyId, String guid) {
        checkNotNull(studyId);
        checkNotNull(guid);
        
        NotificationTopic existing = getTopic(studyId, guid);
        
        // When deleting, get rid of DDB record first, if it succeeds but second fails, 
        // that's not corrupted, there's just an orphaned SNS topic.
        DynamoNotificationTopic hashKey = new DynamoNotificationTopic();
        hashKey.setStudyId(studyId.getIdentifier());
        hashKey.setGuid(guid);
        
        mapper.delete(hashKey);
        
        // If this fails, don't throw an error for the user. There's no DDB record so 
        // it has been successfully deleted
        try {
            DeleteTopicRequest request = new DeleteTopicRequest().withTopicArn(existing.getTopicARN());
            snsClient.deleteTopic(request);
        } catch(AmazonServiceException e) {
            LOG.info("Error deleting SNS topic; Bridge topic has been deleted", e);
        }
    }

    /**
     * So we can find these in the AWS console, we give these a speciall formatted name.
     */
    private String createSnsTopicName(NotificationTopic topic) {
        return topic.getStudyId() + "-" + config.getEnvironment().name().toLowerCase() + "-" + topic.getGuid();
    }
}
