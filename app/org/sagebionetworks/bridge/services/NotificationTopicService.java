package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.NotificationTopicDao;
import org.sagebionetworks.bridge.models.notifications.NotificationTopic;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.validators.NotificationTopicValidator;
import org.sagebionetworks.bridge.validators.Validate;

@Component
public class NotificationTopicService {

    private NotificationTopicDao topicDao;
    
    @Autowired
    final void setNotificationTopicDao(NotificationTopicDao topicDao) {
        this.topicDao = topicDao;
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
}
