package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.util.BridgeCollectors.toImmutableList;

import java.util.List;

import javax.annotation.Resource;

import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.dao.NotificationTopicDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.notifications.NotificationTopic;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import com.amazonaws.services.sns.AmazonSNSClient;

@Component
public class DynamoNotificationTopicDao implements NotificationTopicDao {

    private DynamoDBMapper mapper;
    
    private AmazonSNSClient snsClient;
    
    @Resource(name = "notificationTopicMapper")
    final void setNotificationTopicMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }
    
    @Resource(name = "snsClient")
    final void setSnsClient(AmazonSNSClient snsClient) {
        this.snsClient = snsClient;
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
        
        topic.setGuid(BridgeUtils.generateGuid());
        
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

        DynamoNotificationTopic hashKey = new DynamoNotificationTopic();
        hashKey.setStudyId(studyId.getIdentifier());
        hashKey.setGuid(guid);
        
        mapper.delete(hashKey);
    }

}
