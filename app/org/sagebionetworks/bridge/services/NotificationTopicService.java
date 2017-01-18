package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

import org.sagebionetworks.bridge.dao.NotificationTopicDao;
import org.sagebionetworks.bridge.models.notifications.NotificationTopic;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

public class NotificationTopicService {

    private NotificationTopicDao topicDao;
    
    @Autowired
    final void setNotificationTopicDao(NotificationTopicDao topicDao) {
        this.topicDao = topicDao;
    }
    
    List<NotificationTopic> listTopics(StudyIdentifier studyId) {
        checkNotNull(studyId);
        
        return null;
    }
    
    NotificationTopic getTopic(StudyIdentifier studyId, String guid) {
        checkNotNull(studyId);
        checkNotNull(guid);
        return null;
    }
    
    NotificationTopic createTopic(NotificationTopic topic) {
        checkNotNull(topic);
        return null;
    }
    
    NotificationTopic updateTopic(StudyIdentifier studyId, NotificationTopic topic) {
        checkNotNull(studyId, topic);
        return null;
    }
    
    void deleteTopic(StudyIdentifier studyId, String guid) {
    }
    
    void deleteAllTopics(StudyIdentifier studyId) {
        
    }
    
    
}
