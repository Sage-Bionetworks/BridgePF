package org.sagebionetworks.bridge.play.controllers;

import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import org.sagebionetworks.bridge.models.GuidHolder;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.notifications.NotificationMessage;
import org.sagebionetworks.bridge.models.notifications.NotificationTopic;
import org.sagebionetworks.bridge.services.NotificationTopicService;

import play.mvc.Result;

@Controller
public class NotificationTopicController extends BaseController {
    
    private NotificationTopicService topicService;
    
    @Autowired
    final void setNotificationTopicService(NotificationTopicService topicService) {
        this.topicService = topicService;
    }
    
    public Result getAllTopics() {
        UserSession session = getAuthenticatedSession(DEVELOPER, RESEARCHER);
        
        List<NotificationTopic> list = topicService.listTopics(session.getStudyIdentifier());
        
        return okResult(list);
    }
    
    public Result createTopic() throws Exception {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        
        NotificationTopic topic = parseJson(request(), NotificationTopic.class);
        topic.setStudyId(session.getStudyIdentifier().getIdentifier());
        
        NotificationTopic saved = topicService.createTopic(topic);
        
        return createdResult(new GuidHolder(saved.getGuid()));
    }
    
    public Result getTopic(String guid) {
        UserSession session = getAuthenticatedSession(DEVELOPER, RESEARCHER);

        NotificationTopic topic = topicService.getTopic(session.getStudyIdentifier(), guid);
        
        return okResult(topic);
    }
    
    public Result updateTopic(String guid) {
        UserSession session = getAuthenticatedSession(DEVELOPER);

        NotificationTopic topic = parseJson(request(), NotificationTopic.class);
        topic.setStudyId(session.getStudyIdentifier().getIdentifier());
        topic.setGuid(guid);
        
        NotificationTopic updated = topicService.updateTopic(topic);
        
        return okResult(new GuidHolder(updated.getGuid()));
    }
    
    public Result deleteTopic(String guid) {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        
        topicService.deleteTopic(session.getStudyIdentifier(), guid);
        
        return okResult("Topic deleted.");
    }
    
    public Result sendNotification(String guid) {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        
        NotificationMessage message = parseJson(request(), NotificationMessage.class);
        
        topicService.sendNotification(session.getStudyIdentifier(), guid, message);
        
        return acceptedResult("Message has been sent to external notification service.");
    }

}
