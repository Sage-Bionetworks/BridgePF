package org.sagebionetworks.bridge.play.controllers;

import static org.sagebionetworks.bridge.dao.ParticipantOption.SHARING_SCOPE;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.models.accounts.SharingOption;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.accounts.Withdrawal;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
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
    final void setConsentService(ConsentService consentService) {
        this.consentService = consentService;
    }
    @Autowired
    final void setOptionsService(ParticipantOptionsService optionsService) {
        this.optionsService = optionsService;
    }

    @Deprecated
    public Result getConsentSignature() throws Exception {
        final UserSession session = getAuthenticatedAndConsentedSession();
        return getConsentSignatureV2(session.getStudyIdentifier().getIdentifier());
    }

    @Deprecated
    public Result giveV1() throws Exception {
        final UserSession session = getAuthenticatedSession();
        return giveConsentForVersion(1, SubpopulationGuid.create(session.getStudyIdentifier().getIdentifier()));
    }

    @Deprecated
    public Result giveV2() throws Exception {
        final UserSession session = getAuthenticatedSession();
        return giveConsentForVersion(2, SubpopulationGuid.create(session.getStudyIdentifier().getIdentifier()));
    }

    @Deprecated
    public Result emailCopy() throws Exception {
        final UserSession session = getAuthenticatedAndConsentedSession();
        
        return emailCopyV2(session.getStudyIdentifier().getIdentifier());
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
    
    @Deprecated
    public Result withdrawConsent() throws Exception {
        final UserSession session = getAuthenticatedAndConsentedSession();
        final Study study = studyService.getStudy(session.getStudyIdentifier());
        
        return withdrawConsentV2(study.getIdentifier());
    }

    // V2: consent to a specific subpopulation
    
    public Result getConsentSignatureV2(String guid) throws Exception {
        final UserSession session = getAuthenticatedAndConsentedSession();
        final Study study = studyService.getStudy(session.getStudyIdentifier());

        ConsentSignature sig = consentService.getConsentSignature(study, SubpopulationGuid.create(guid), session.getId());
        return ok(ConsentSignature.SIGNATURE_WRITER.writeValueAsString(sig));
    }
    
    public Result giveV3(String guid) throws Exception {
        return giveConsentForVersion(2, SubpopulationGuid.create(guid));
    }
    
    public Result withdrawConsentV2(String guid) throws Exception {
        final UserSession session = getAuthenticatedAndConsentedSession();
        final Withdrawal withdrawal = parseJson(request(), Withdrawal.class);
        final Study study = studyService.getStudy(session.getStudyIdentifier());
        final long withdrewOn = DateTime.now().getMillis();
        
        consentService.withdrawConsent(study, SubpopulationGuid.create(guid), session, withdrawal, withdrewOn);
        updateSession(session);
        
        return okResult("User has been withdrawn from the study.");
    }
    
    public Result emailCopyV2(String guid) {
        final UserSession session = getAuthenticatedAndConsentedSession();
        final Study study = studyService.getStudy(session.getStudyIdentifier());

        consentService.emailConsentAgreement(study, SubpopulationGuid.create(guid), session.getParticipant());
        return okResult("Emailed consent.");
    }
    
    Result changeSharingScope(SharingScope sharingScope, String message) {
        final UserSession session = getAuthenticatedAndConsentedSession();
        final Study study = studyService.getStudy(session.getStudyIdentifier());
        
        optionsService.setEnum(study, session.getHealthCode(), SHARING_SCOPE, sharingScope);
        
        StudyParticipant participant = new StudyParticipant.Builder()
                .copyOf(session.getParticipant())
                .withSharingScope(sharingScope).build();
        session.setParticipant(participant);
        updateSession(session);
        return okResult(message);
    }

    private Result giveConsentForVersion(int version, SubpopulationGuid subpopGuid) throws Exception {
        final UserSession session = getAuthenticatedSession();
        final Study study = studyService.getStudy(session.getStudyIdentifier());

        final ConsentSignature consent = parseJson(request(), ConsentSignature.class);
        final SharingOption sharing = SharingOption.fromJson(requestToJSON(request()), version);

        consentService.consentToResearch(study, subpopGuid, session, consent,
                sharing.getSharingScope(), true);
        
        StudyParticipant participant = new StudyParticipant.Builder().copyOf(session.getParticipant())
                .withSharingScope(sharing.getSharingScope()).build();
        session.setParticipant(participant);
        updateSession(session);
        return createdResult("Consent to research has been recorded.");
    }
}
