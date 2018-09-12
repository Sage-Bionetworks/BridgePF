package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;

import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.dao.CriteriaDao;
import org.sagebionetworks.bridge.dao.NotificationTopicDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.time.DateUtils;
import org.sagebionetworks.bridge.models.notifications.NotificationTopic;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.CreateTopicResult;
import com.amazonaws.services.sns.model.DeleteTopicRequest;
import com.google.common.collect.ImmutableList;

@Component
public class DynamoNotificationTopicDao implements NotificationTopicDao {
    private static final Logger LOG = LoggerFactory.getLogger(DynamoNotificationTopicDao.class);

    static final String ATTR_DISPLAY_NAME = "DisplayName";
    static final String CRITERIA_KEY_PREFIX = "notificationtopic:";

    private DynamoDBMapper mapper;
    private AmazonSNSClient snsClient;
    private BridgeConfig config;
    private CriteriaDao criteriaDao;

    @Resource(name = "notificationTopicMapper")
    final void setNotificationTopicMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }
    
    @Resource(name = "snsClient")
    final void setSnsClient(AmazonSNSClient snsClient) {
        this.snsClient = snsClient;
    }

    @Resource(name = "bridgeConfig")
    public final void setBridgeConfig(BridgeConfig bridgeConfig) {
        this.config = bridgeConfig;
    }

    /** Criteria DAO, because Criteria goes in a separate table. */
    @Autowired
    public final void setCriteriaDao(CriteriaDao criteriaDao) {
        this.criteriaDao = criteriaDao;
    }

    @Override
    public List<NotificationTopic> listTopics(StudyIdentifier studyId, boolean includeDeleted) {
        checkNotNull(studyId);

        // Consistent reads is set to true, because this is the table's primary key, and having reliable tests is more
        // important than saving a small amount of DDB capacity.
        DynamoNotificationTopic hashKey = new DynamoNotificationTopic();
        hashKey.setStudyId(studyId.getIdentifier());
        
        DynamoDBQueryExpression<DynamoNotificationTopic> query = new DynamoDBQueryExpression<DynamoNotificationTopic>();
        query.withConsistentRead(true);
        query.withHashKeyValues(hashKey);
        if (!includeDeleted) {
            query.withQueryFilterEntry("deleted", new Condition()
                .withComparisonOperator(ComparisonOperator.NE)
                .withAttributeValueList(new AttributeValue().withN("1")));
        }
        QueryResultPage<DynamoNotificationTopic> resultPage = mapper.queryPage(DynamoNotificationTopic.class, query);
        List<DynamoNotificationTopic> topicList = resultPage.getResults();

        // Load criteria.
        topicList.forEach(this::loadCriteria);

        return ImmutableList.copyOf(topicList);
    }

    @Override
    public NotificationTopic getTopic(StudyIdentifier studyId, String guid) {
        checkNotNull(studyId);
        checkNotNull(guid);
        
        return getTopicInternal(studyId.getIdentifier(), guid);
    }

    private NotificationTopic getTopicInternal(String studyId, String guid) {
        DynamoNotificationTopic hashKey = new DynamoNotificationTopic();
        hashKey.setStudyId(studyId);
        hashKey.setGuid(guid);

        DynamoNotificationTopic topic = mapper.load(hashKey);
        if (topic == null) {
            throw new EntityNotFoundException(NotificationTopic.class);
        }

        loadCriteria(topic);

        return topic;
    }
    
    @Override
    public NotificationTopic createTopic(NotificationTopic topic) {
        checkNotNull(topic);
        
        // Create SNS topic first. If SNS fails, an exception is thrown. If DDB call fails, the SNS topic is orphaned
        // but that will not break the data integrity of Bridge data.
        
        topic.setGuid(BridgeUtils.generateGuid());
        
        String snsTopicName = createSnsTopicName(topic);
        CreateTopicResult result = snsClient.createTopic(snsTopicName);

        // Display name is required for SMS notifications. Strangely, there's no way to set display name in the
        // create API, only the set attribute API.
        snsClient.setTopicAttributes(result.getTopicArn(), ATTR_DISPLAY_NAME, topic.getShortName());

        topic.setTopicARN(result.getTopicArn());
        long timestamp = DateUtils.getCurrentMillisFromEpoch();
        topic.setCreatedOn(timestamp);
        topic.setModifiedOn(timestamp);

        persistCriteria(topic);

        mapper.save(topic);
        return topic;
    }

    @Override
    public NotificationTopic updateTopic(NotificationTopic topic) {
        checkNotNull(topic);
        checkNotNull(topic.getGuid());

        NotificationTopic existing = getTopicInternal(topic.getStudyId(), topic.getGuid());
        existing.setName(topic.getName());
        existing.setDescription(topic.getDescription());
        existing.setModifiedOn(DateUtils.getCurrentMillisFromEpoch() );
        existing.setCriteria(topic.getCriteria());

        persistCriteria(topic);

        mapper.save(existing);
        return existing;
    }
    
    @Override
    public void deleteTopic(StudyIdentifier studyId, String guid) {
        checkNotNull(studyId);
        checkNotNull(guid);
        
        NotificationTopic existing = getTopicInternal(studyId.getIdentifier(), guid);
        if (existing.isDeleted()) {
            throw new EntityNotFoundException(NotificationTopic.class);
        }
        existing.setDeleted(true);
        mapper.save(existing);
    }

    @Override
    public void deleteTopicPermanently(StudyIdentifier studyId, String guid) {
        checkNotNull(studyId);
        checkNotNull(guid);
        
        NotificationTopic existing = getTopicInternal(studyId.getIdentifier(), guid);
        
        // Delete the DDB record first. If it fails an exception is thrown. If SNS fails, the SNS topic
        // is not deleted, but the DDB record has successfully deleted, so suppress the exception (just 
        // log it) because the topic has been deleted from Bridge without a referential integrity problem.
        DynamoNotificationTopic hashKey = new DynamoNotificationTopic();
        hashKey.setStudyId(studyId.getIdentifier());
        hashKey.setGuid(guid);
        
        mapper.delete(hashKey);

        // Delete criteria, if it exists.
        if (existing.getCriteria() != null) {
            criteriaDao.deleteCriteria(getCriteriaKey(guid));
        }

        // Delete from SNS.
        try {
            DeleteTopicRequest request = new DeleteTopicRequest().withTopicArn(existing.getTopicARN());
            snsClient.deleteTopic(request);
        } catch(AmazonServiceException e) {
            LOG.warn("Bridge topic '" + existing.getName() + "' in study '" + existing.getStudyId()
                    + "' deleted, but SNS topic deletion threw exception", e);
        }
    }
    
    @Override
    public void deleteAllTopics(StudyIdentifier studyId) {
        checkNotNull(studyId);
        
        List<NotificationTopic> topics = listTopics(studyId, true);
        // Delete them individually. 
        for (NotificationTopic topic : topics) {
            deleteTopicPermanently(studyId, topic.getGuid());
        }
    }

    /**
     * So we can find these in the AWS console, we give these a specifically formatted name.
     */
    private String createSnsTopicName(NotificationTopic topic) {
        return topic.getStudyId() + "-" + config.getEnvironment().name().toLowerCase() + "-" + topic.getGuid();
    }

    // Generates the criteria key for the given topic guid.
    private String getCriteriaKey(String topicGuid) {
        return CRITERIA_KEY_PREFIX + topicGuid;
    }

    // Helper method to get the criteria key for a topic.
    private String getCriteriaKey(NotificationTopic topic) {
        return getCriteriaKey(topic.getGuid());
    }

    // Helper method to load criteria, which comes from another table.
    private void loadCriteria(NotificationTopic topic) {
        Criteria criteria = criteriaDao.getCriteria(getCriteriaKey(topic));

        // There are two kinds notification topics: topics with criteria, and topics without. Topics with criteria
        // have their subscribers auto-managed by Bridge server. Topics without need to be subscribed to manually.
        // To ensure we keep this separation clear, only set a criteria into the topic if it exists.
        if (criteria != null) {
            topic.setCriteria(criteria);
        }
    }

    // Helper method which saves the criteria, if it exists. Called by create and update.
    private void persistCriteria(NotificationTopic topic) {
        Criteria criteria = topic.getCriteria();

        // Similarly, only save the criteria if one was provided.
        if (criteria != null) {
            topic.setCriteria(criteria);
            criteria.setKey(getCriteriaKey(topic));
            criteriaDao.createOrUpdateCriteria(criteria);
        }
    }
}
