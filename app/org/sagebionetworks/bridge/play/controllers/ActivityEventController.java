package org.sagebionetworks.bridge.play.controllers;

import static org.sagebionetworks.bridge.models.activities.ActivityEvent.ACTIVITY_EVENT_WRITER;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import play.mvc.Result;

import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.activities.ActivityEvent;
import org.sagebionetworks.bridge.models.activities.CustomActivityEventRequest;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.ActivityEventService;
import org.sagebionetworks.bridge.services.ParticipantService;
import org.sagebionetworks.bridge.services.StudyService;

@Controller
public class ActivityEventController extends BaseController {
    private ActivityEventService activityEventService;
    private StudyService studyService;
    private ParticipantService participantService;

    @Autowired
    public ActivityEventController(ActivityEventService activityEventService, StudyService studyService,
            ParticipantService participantService) {
        this.activityEventService = activityEventService;
        this.studyService = studyService;
        this.participantService = participantService;
    }

    public Result createCustomActivityEvent() {
        UserSession session = getAuthenticatedAndConsentedSession();
        CustomActivityEventRequest activityEvent = parseJson(request(), CustomActivityEventRequest.class);

        activityEventService.publishCustomEvent(session.getStudyIdentifier(), session.getHealthCode(),
                activityEvent.getEventKey(), activityEvent.getTimestamp());

        return createdResult("Event recorded");
    }

    public Result getSelfActivityEvents() throws Exception {
        UserSession session = getAuthenticatedAndConsentedSession();

        List<ActivityEvent> activityEvents = activityEventService.getActivityEventList(session.getHealthCode());

        return okResult(ACTIVITY_EVENT_WRITER.writeValueAsString(activityEvents));
    }
}
