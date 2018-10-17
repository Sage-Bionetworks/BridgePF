package org.sagebionetworks.bridge.play.controllers;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import play.mvc.Result;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.sms.SmsMessage;
import org.sagebionetworks.bridge.models.sms.SmsOptOutSettings;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.SmsService;
import org.sagebionetworks.bridge.sms.IncomingSms;
import org.sagebionetworks.bridge.sms.TwilioHelper;

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

    /** API for Twilio Webhook. See https://www.twilio.com/docs/sms/tutorials/how-to-receive-and-reply-java */
    public Result handleIncomingSms() {
        // todo validate incoming Twilio request.
        // See https://www.twilio.com/docs/usage/tutorials/how-to-secure-your-servlet-app-by-validating-incoming-twilio-requests

        // Convert inputs.
        Map<String, String[]> formPostMap = request().body().asFormUrlEncoded();
        IncomingSms incomingSms = TwilioHelper.convertIncomingSms(formPostMap);

        // Call service.
        String responseContent = smsService.handleIncomingSms(incomingSms);

        // Convert response.
        String responseBody;
        if (responseContent == null) {
            responseBody = TwilioHelper.RESPONSE_NOOP;
        } else {
            responseBody = String.format(TwilioHelper.RESPONSE_MESSAGE, responseContent);
        }

        return ok(responseBody).as("application/xml");
    }

    /** Returns the most recent message sent to the phone number of the given user. Used by integration tests. */
    public Result getMostRecentMessage(String userId) {
        UserSession session = getAuthenticatedSession(Roles.ADMIN);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        SmsMessage message = smsService.getMostRecentMessage(study, userId);
        return okResult(message);
    }

    /** Returns the opt-out settings for the phone number of the given user. Used by integration tests. */
    public Result getOptOutSettings(String userId) {
        UserSession session = getAuthenticatedSession(Roles.ADMIN);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        SmsOptOutSettings optOutSettings = smsService.getOptOutSettings(study, userId);
        return okResult(optOutSettings);
    }

    /** Sets the opt-out settings for the phone number of the given user. Used by integration tests. */
    public Result setOptOutSettings(String userId) {
        UserSession session = getAuthenticatedSession(Roles.ADMIN);
        Study study = studyService.getStudy(session.getStudyIdentifier());

        SmsOptOutSettings optOutSettings = parseJson(request(), SmsOptOutSettings.class);
        smsService.setOptOutSettings(study, userId, optOutSettings);

        return okResult("SMS opt-out settings updated");
    }
}
