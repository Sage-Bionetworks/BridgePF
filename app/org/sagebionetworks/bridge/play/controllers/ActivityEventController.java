package org.sagebionetworks.bridge.play.controllers;

import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import play.mvc.Result;

import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.services.ActivityEventService;

/**
 * Created by jyliu on 6/5/2017.
 */
@Controller
public class ActivityEventController extends BaseController {
    @Autowired
    private ActivityEventService activityEventService;

    public Result createActivityEvent(String eventKey, String timestamp) {
        UserSession session = getAuthenticatedSession();

        DateTime eventTime = DateUtils.getDateTimeOrDefault(timestamp, null);

        activityEventService.publishCustomEvent(session.getStudyIdentifier(), session.getHealthCode(), eventKey,
                eventTime);

        return okResult("Event recorded");
    }
}
