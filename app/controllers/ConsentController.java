package controllers;

import org.sagebionetworks.bridge.models.ConsentSignature;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.services.ConsentService;

import play.mvc.Result;

public class ConsentController extends BaseController {

    private ConsentService consentService;

    public void setConsentService(ConsentService consentService) {
        this.consentService = consentService;
    }

    public Result give() throws Exception {
        UserSession session = getAuthenticatedSession();
        ConsentSignature consent = ConsentSignature.fromJson(requestToJSON(request()));
        Study study = studyService.getStudyByHostname(getHostname());

        User user = consentService.consentToResearch(session.getUser(), consent, study, true);
        
        updateSessionUser(session, user);
        setSessionToken(session.getSessionToken());

        return okResult("Consent to research has been recorded.");
    }

    public Result emailCopy() throws Exception {
        UserSession session = getAuthenticatedAndConsentedSession();
        Study study = studyService.getStudyByHostname(getHostname());

        consentService.emailConsentAgreement(session.getUser(), study);

        return okResult("Emailed consent.");
    }

    public Result suspendDataSharing() throws Exception {
        UserSession session = getAuthenticatedAndConsentedSession();
        Study study = studyService.getStudyByHostname(getHostname());

        User user = consentService.suspendDataSharing(session.getUser(), study);
        updateSessionUser(session, user);

        return okResult("Data sharing with the study researchers has been suspended.");
    }

    public Result resumeDataSharing() throws Exception {
        UserSession session = getAuthenticatedAndConsentedSession();
        Study study = studyService.getStudyByHostname(getHostname());

        User user = consentService.resumeDataSharing(session.getUser(), study);
        updateSessionUser(session, user);

        return okResult("Data sharing with the study researchers has been resumed.");
    }
}
