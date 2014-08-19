package controllers;

import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.ResearchConsent;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.services.ConsentService;

import play.mvc.Result;

public class ConsentController extends BaseController {

    private StudyControllerService studyControllerService;
    private ConsentService consentService;

    public void setStudyControllerService(StudyControllerService studyControllerService) {
        this.studyControllerService = studyControllerService;
    }

    public void setConsentService(ConsentService consentService) {
        this.consentService = consentService;
    }
    
    public Result give() throws Exception {
        // Don't call getSession(), it'll throw an exception due to lack of
        // consent, we know this person has not consented, that's what they're
        // trying to do.
        UserSession session = checkForSession();
        if (session == null) {
            throw new BridgeServiceException("Not signed in.", 401);
        }
        ResearchConsent consent = ResearchConsent.fromJson(request().body().asJson());
        Study study = studyControllerService.getStudyByHostname(request());
        User user = consentService.consentToResearch(session.getUser(), consent, study, true);
        updateSessionUser(session, user);

        return okResult("Consent to research has been recorded.");
    }

    public Result withdraw() throws Exception {
        UserSession session = getSession(); // throws exception if user isn't consented
        if (session == null) {
            throw new BridgeServiceException("Not signed in.", 401);
        } else if (!session.getUser().doesConsent()) {
            throw new BridgeServiceException("Need to consent.", 412);
        }
        Study study = studyControllerService.getStudyByHostname(request());
        consentService.withdrawConsent(session.getUser(), study);

        return okResult("Withdraw consent has been recorded.");
    }

    public Result emailCopy() throws Exception {
        UserSession session = getSession();
        if (session == null) {
            throw new BridgeServiceException("Not signed in.", 401);
        } else if (!session.getUser().doesConsent()) {
            throw new BridgeServiceException("Need to consent.", 412);
        }
        Study study = studyControllerService.getStudyByHostname(request());
        consentService.emailConsentAgreement(session.getUser(), study);

        return okResult("Emailed consent.");
    }
    
    public Result suspendDataSharing() throws Exception {
        UserSession session = checkForSession();
        if (session == null) {
            throw new BridgeServiceException("Not signed in.", 401);
        }
        Study study = studyControllerService.getStudyByHostname(request());
        User user = consentService.suspendDataSharing(session.getUser(), study);
        updateSessionUser(session, user);

        return okResult("Suspended data sharing.");
    }
    
    public Result resumeDataSharing() throws Exception {
        UserSession session = checkForSession();
        if (session == null) {
            throw new BridgeServiceException("Not signed in.", 401);
        }
        Study study = studyControllerService.getStudyByHostname(request());
        User user = consentService.resumeDataSharing(session.getUser(), study);
        updateSessionUser(session, user);
        
        return okResult("Resuming data sharing.");
    }
}
