package controllers;

import org.sagebionetworks.bridge.dao.ParticipantOptionsDao.Option;
import org.sagebionetworks.bridge.models.ConsentSignature;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.services.ConsentService;
import org.sagebionetworks.bridge.services.ParticipantOptionsService;

import play.mvc.Result;

public class ConsentController extends BaseController {

    private ConsentService consentService;
    
    private ParticipantOptionsService optionsService;

    public void setConsentService(ConsentService consentService) {
        this.consentService = consentService;
    }
    
    public void setOptionsService(ParticipantOptionsService optionsService) {
        this.optionsService = optionsService;
    }

    public Result give() throws Exception {
        final UserSession session = getAuthenticatedSession();
        final ConsentSignature consent = ConsentSignature.fromJson(requestToJSON(request()));
        final Study study = studyService.getStudyByHostname(getHostname());
        final User user = consentService.consentToResearch(session.getUser(), consent, study, true);
        updateSessionUser(session, user);
        setSessionToken(session.getSessionToken());
        return createdResult("Consent to research has been recorded.");
    }

    public Result emailCopy() throws Exception {
        final UserSession session = getAuthenticatedAndConsentedSession();
        final Study study = studyService.getStudyByHostname(getHostname());
        consentService.emailConsentAgreement(session.getUser(), study);
        return okResult("Emailed consent.");
    }

    public Result suspendDataSharing() throws Exception {
        final UserSession session = getAuthenticatedAndConsentedSession();
        final Study study = studyService.getStudyByHostname(getHostname());
        final User user = session.getUser();
        optionsService.setOption(study, user.getHealthDataCode(), Option.DATA_SHARING, Boolean.FALSE.toString());
        user.setDataSharing(false);
        updateSessionUser(session, user);
        return okResult("Data sharing with the study researchers has been suspended.");
    }

    public Result resumeDataSharing() throws Exception {
        final UserSession session = getAuthenticatedAndConsentedSession();
        final Study study = studyService.getStudyByHostname(getHostname());
        final User user = session.getUser();
        optionsService.setOption(study, user.getHealthDataCode(), Option.DATA_SHARING, Boolean.TRUE.toString());
        user.setDataSharing(true);
        updateSessionUser(session, user);
        return okResult("Data sharing with the study researchers has been resumed.");
    }
}
