package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.notifications.NotificationTopic;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

public interface NotificationTopicDao {

    List<NotificationTopic> listTopics(StudyIdentifier studyId, boolean includeDeleted);
    
    NotificationTopic getTopic(StudyIdentifier studyId, String guid);
    
    NotificationTopic createTopic(NotificationTopic topic);
    
    NotificationTopic updateTopic(NotificationTopic topic);
    
    void deleteTopic(StudyIdentifier studyId, String guid);
    
    void deleteTopicPermanently(StudyIdentifier studyId, String guid);
    
    void deleteAllTopics(StudyIdentifier studyId);
    
}
