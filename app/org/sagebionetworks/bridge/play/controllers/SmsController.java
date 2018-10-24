package org.sagebionetworks.bridge.play.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import play.mvc.Result;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.sms.SmsMessage;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.SmsService;

/**
 * Play controller for handling SMS metadata (opt-outs, message logging) and for handling webhooks for receiving SMS.
 */
@Controller
public class SmsController extends BaseController {
    private SmsService smsService;

    /** SMS service. */
    @Autowired
    public final void setSmsService(SmsService smsService) {
        this.smsService = smsService;
    }

    /** Returns the most recent message sent to the phone number of the given user. Used by integration tests. */
    public Result getMostRecentMessage(String userId) {
        UserSession session = getAuthenticatedSession(Roles.ADMIN);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        SmsMessage message = smsService.getMostRecentMessage(study, userId);
        return okResult(message);
    }
}
