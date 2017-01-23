package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;

import javax.annotation.Resource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.NotificationTopicDao;
import org.sagebionetworks.bridge.models.notifications.NotificationMessage;
import org.sagebionetworks.bridge.models.notifications.NotificationTopic;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.validators.NotificationMessageValidator;
import org.sagebionetworks.bridge.validators.NotificationTopicValidator;
import org.sagebionetworks.bridge.validators.Validate;

import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.PublishRequest;

@Component
public class NotificationTopicService {

    private NotificationTopicDao topicDao;
    
    private AmazonSNSClient snsClient;

    @Autowired
    final void setNotificationTopicDao(NotificationTopicDao topicDao) {
        this.topicDao = topicDao;
    }
    
    @Resource(name = "snsClient")
    final void setSnsClient(AmazonSNSClient snsClient) {
        this.snsClient = snsClient;
    }
    
    public List<NotificationTopic> listTopics(StudyIdentifier studyId) {
        checkNotNull(studyId);
        
        return topicDao.listTopics(studyId);
    }
    
    public NotificationTopic getTopic(StudyIdentifier studyId, String guid) {
        checkNotNull(studyId);
        checkNotNull(guid);
        
        return topicDao.getTopic(studyId, guid);
    }
    
    public NotificationTopic createTopic(NotificationTopic topic) {
        checkNotNull(topic);
        
        Validate.entityThrowingException(NotificationTopicValidator.INSTANCE, topic);
        
        return topicDao.createTopic(topic);
    }
    
    public NotificationTopic updateTopic(NotificationTopic topic) {
        checkNotNull(topic);
        
        Validate.entityThrowingException(NotificationTopicValidator.INSTANCE, topic);
        
        return topicDao.updateTopic(topic);
    }
    
    public void deleteTopic(StudyIdentifier studyId, String guid) {
        checkNotNull(studyId);
        checkNotNull(guid);
        
        topicDao.deleteTopic(studyId, guid);
    }
    
    public void deleteAllTopics(StudyIdentifier studyId) {
        checkNotNull(studyId);
        
        topicDao.deleteAllTopics(studyId);
    }
    
    public void sendNotification(StudyIdentifier studyId, String guid, NotificationMessage message) {
        checkNotNull(studyId);
        checkNotNull(guid);
        checkNotNull(message);
        
        Validate.entityThrowingException(NotificationMessageValidator.INSTANCE, message);
        
        NotificationTopic topic = getTopic(studyId, guid);
        
        PublishRequest request = new PublishRequest().withTopicArn(topic.getTopicARN())
                .withSubject(message.getSubject()).withMessage(message.getMessage());
        
        snsClient.publish(request);
    }
}
