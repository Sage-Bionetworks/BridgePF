package org.sagebionetworks.bridge.play.controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import play.mvc.Result;

import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.activities.ActivityEvent;
import org.sagebionetworks.bridge.models.activities.CustomActivityEventRequest;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.ActivityEventService;

@Controller
public class ActivityEventController extends BaseController {
    private ActivityEventService activityEventService;

    @Autowired
    public ActivityEventController(ActivityEventService activityEventService) {
        this.activityEventService = activityEventService;
    }

    public Result createCustomActivityEvent() {
        UserSession session = getAuthenticatedAndConsentedSession();
        CustomActivityEventRequest activityEvent = parseJson(request(), CustomActivityEventRequest.class);

        Study study = studyService.getStudy(session.getStudyIdentifier());
        activityEventService.publishCustomEvent(study, session.getHealthCode(),
                activityEvent.getEventKey(), activityEvent.getTimestamp());

        return createdResult("Event recorded");
    }

    public Result getSelfActivityEvents() throws Exception {
        UserSession session = getAuthenticatedAndConsentedSession();

        List<ActivityEvent> activityEvents = activityEventService.getActivityEventList(session.getHealthCode());
        
        return okResult(activityEvents);
    }
}
