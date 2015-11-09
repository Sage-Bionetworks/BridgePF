package org.sagebionetworks.bridge.play.controllers;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.models.accounts.SharingOption;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.accounts.Withdrawal;
import org.sagebionetworks.bridge.models.studies.ConsentSignature;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.ConsentService;
import org.sagebionetworks.bridge.services.ParticipantOptionsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import play.mvc.Result;

@Controller
public class ConsentController extends BaseController {

    private ConsentService consentService;

    private ParticipantOptionsService optionsService;

    @Autowired
    public void setConsentService(ConsentService consentService) {
        this.consentService = consentService;
    }
    @Autowired
    public void setOptionsService(ParticipantOptionsService optionsService) {
        this.optionsService = optionsService;
    }

    public Result getConsentSignature() throws Exception {
        final UserSession session = getAuthenticatedAndConsentedSession();
        final Study study = studyService.getStudy(session.getStudyIdentifier());

        ConsentSignature sig = consentService.getConsentSignature(study, session.getUser());
        return ok(ConsentSignature.SIGNATURE_WRITER.writeValueAsString(sig));
    }

    @Deprecated
    public Result giveV1() throws Exception {
        return giveConsentForVersion(1);
    }

    public Result giveV2() throws Exception {
        return giveConsentForVersion(2);
    }

    public Result emailCopy() throws Exception {
        final UserSession session = getAuthenticatedAndConsentedSession();
        final Study study = studyService.getStudy(session.getStudyIdentifier());
        consentService.emailConsentAgreement(study, session.getUser());
        return okResult("Emailed consent.");
    }

    @Deprecated
    public Result suspendDataSharing() throws Exception {
        return changeSharingScope(SharingScope.NO_SHARING, 
                "Data sharing with the study researchers has been suspended.");
    }

    @Deprecated
    public Result resumeDataSharing() throws Exception {
        return changeSharingScope(SharingScope.SPONSORS_AND_PARTNERS,
                "Data sharing with the study researchers has been resumed.");
    }
    
    public Result changeSharingScope() throws Exception {
        SharingOption sharing = SharingOption.fromJson(requestToJSON(request()), 2);
        return changeSharingScope(sharing.getSharingScope(), "Data sharing has been changed.");
    }
    
    public Result withdrawConsent() throws Exception {
        final UserSession session = getAuthenticatedAndConsentedSession();
        final Withdrawal withdrawal = parseJson(request(), Withdrawal.class);
        final Study study = studyService.getStudy(session.getStudyIdentifier());
        final long withdrewOn = DateTime.now().getMillis();
        
        consentService.withdrawConsent(study, session.getUser(), withdrawal, withdrewOn);
        updateSessionUser(session, session.getUser());
        
        return okResult("User has been withdrawn from the study.");
    }

    Result changeSharingScope(SharingScope sharingScope, String message) {
        final UserSession session = getAuthenticatedAndConsentedSession();
        final User user = session.getUser();
        final Study study = studyService.getStudy(session.getStudyIdentifier());
        optionsService.setSharingScope(study, user.getHealthCode(), sharingScope);
        user.setSharingScope(sharingScope);
        updateSessionUser(session, user);
        consentService.emailConsentAgreement(study, user);
        return okResult(message);
    }

    private Result giveConsentForVersion(int version) throws Exception {
        final UserSession session = getAuthenticatedSession();
        final Study study = studyService.getStudy(session.getStudyIdentifier());

        final ConsentSignature consent = parseJson(request(), ConsentSignature.class);
        final SharingOption sharing = SharingOption.fromJson(requestToJSON(request()), version);
        
        final User user = consentService.consentToResearch(study, session.getUser(), consent,
                sharing.getSharingScope(), true);

        user.setSharingScope(sharing.getSharingScope());
        updateSessionUser(session, user);
        setSessionToken(session.getSessionToken());
        return createdResult("Consent to research has been recorded.");
    }
}
