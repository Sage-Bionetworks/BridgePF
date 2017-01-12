package org.sagebionetworks.bridge.play.controllers;

import java.util.List;

import org.springframework.stereotype.Controller;

import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;
import org.sagebionetworks.bridge.services.NotificationsService;

import play.mvc.Result;

@Controller
public class NotificationsController extends BaseController {
    
    private NotificationsService notificationsService;
    
    final void setNotificationService(NotificationsService notificationsService) {
        this.notificationsService = notificationsService;
    }

    public Result getAllRegistrations() {
        UserSession session = getAuthenticatedAndConsentedSession();
        
        List<NotificationRegistration> registrations = notificationsService.listRegistrations(session.getHealthCode());
        
        return okResult(registrations);
    }
    
    public Result createRegistration() {
        return null;
    }
    
    public Result getRegistration(String guid) {
        return null;
    }
    
    public Result deleteRegistration(String guid) {
        return null;
    }
    
    public Result updateRegistration(String guid) {
        return null;
    }
}
