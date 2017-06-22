package org.sagebionetworks.bridge.play.controllers;

import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import org.joda.time.DateTime;
import org.sagebionetworks.evaluation.model.Participant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import play.mvc.Result;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.activities.ActivityEvent;
import org.sagebionetworks.bridge.models.activities.ActivityEventImpl;
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

        Study study = studyService.getStudy(session.getStudyIdentifier());

        StudyParticipant participant = participantService.getParticipant(study, session.getId(), false);

        DateTime eventTime = activityEvent.getTimestamp() == null ? null : new DateTime(activityEvent.getTimestamp());

        activityEventService.publishCustomEvent(session.getStudyIdentifier(), participant.getHealthCode(),
                activityEvent.getEventKey(), eventTime);

        return okResult("Event recorded");
    }

    public Result getSelfActivityEvents() {
        UserSession session = getAuthenticatedAndConsentedSession();

        return okResult(getActivityEventList(session.getHealthCode()));
    }

    public Result getActivityEvents(String participantId) {
        UserSession researcherSession = getAuthenticatedSession(Roles.RESEARCHER);

        Study study = studyService.getStudy(researcherSession.getStudyIdentifier());

        StudyParticipant studyParticipant = participantService.getParticipant(study, participantId, false);

        return okResult(getActivityEventList(studyParticipant.getHealthCode()));
    }

    private PagedResourceList<ActivityEvent> getActivityEventList(String healthCode) {
        Map<String, DateTime> activityEvents = activityEventService.getActivityEventMap(healthCode);

        List<ActivityEvent> activityEventList = Lists.newArrayList();
        for (Map.Entry<String, DateTime> entry : activityEvents.entrySet()) {
            DateTime timestamp = entry.getValue();
            activityEventList.add(
                    new ActivityEventImpl(
                            null,
                            entry.getKey(),
                            null,
                            timestamp == null ? null : timestamp.getMillis()
                    ));
        }
        return new PagedResourceList<>(activityEventList, null, activityEventList.size(),
                activityEventList.size());
    }
}
