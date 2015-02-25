package controllers;

import org.sagebionetworks.bridge.dao.ParticipantOption;
import org.sagebionetworks.bridge.models.SharingOption;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.models.studies.ConsentSignature;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.services.ConsentService;
import org.sagebionetworks.bridge.services.ParticipantOptionsService;

import com.fasterxml.jackson.databind.JsonNode;

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

    public Result getConsentSignature() throws Exception {
        final UserSession session = getAuthenticatedAndConsentedSession();
        final Study study = studyService.getStudy(session.getStudyIdentifier());

        ConsentSignature sig = consentService.getConsentSignature(study, session.getUser());
        return okResult(sig);
    }

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

    public Result suspendDataSharing() throws Exception {
        final UserSession session = getAuthenticatedAndConsentedSession();
        final StudyIdentifier studyId = session.getStudyIdentifier();
        final User user = session.getUser();
        
        optionsService.setOption(studyId, user.getHealthCode(), ParticipantOption.ScopeOfSharing.NO_SHARING);
        user.setDataSharing(ParticipantOption.ScopeOfSharing.NO_SHARING);
        updateSessionUser(session, user);
        return okResult("Data sharing with the study researchers has been suspended.");
    }

    public Result resumeDataSharing() throws Exception {
        final UserSession session = getAuthenticatedAndConsentedSession();
        final StudyIdentifier studyId = session.getStudyIdentifier();
        final User user = session.getUser();
        
        optionsService.setOption(studyId, user.getHealthCode(), ParticipantOption.ScopeOfSharing.SPONSORS_AND_PARTNERS);
        user.setDataSharing(ParticipantOption.ScopeOfSharing.SPONSORS_AND_PARTNERS);
        updateSessionUser(session, user);
        return okResult("Data sharing with the study researchers has been resumed.");
    }
    
    public Result changeDataSharing() throws Exception {
        final UserSession session = getAuthenticatedAndConsentedSession();
        final User user = session.getUser();
        
        SharingOption sharing = SharingOption.fromJson(requestToJSON(request()), 2);
        optionsService.setOption(session.getStudyIdentifier(), user.getHealthCode(), sharing.getScopeOfSharing());
        user.setDataSharing(sharing.getScopeOfSharing());
        updateSessionUser(session, user);
        return okResult("Data sharing has been changed.");
    }

    private Result giveConsentForVersion(int version) throws Exception {
        final UserSession session = getAuthenticatedSession();
        final Study study = studyService.getStudy(session.getStudyIdentifier());
        final JsonNode node = requestToJSON(request());
        
        final ConsentSignature consent = ConsentSignature.createFromJson(node);
        final User user = consentService.consentToResearch(study, session.getUser(), consent, true);
        
        SharingOption sharing = SharingOption.fromJson(node, version);
        optionsService.setOption(study, session.getUser().getHealthCode(), sharing.getScopeOfSharing());
        
        updateSessionUser(session, user);
        setSessionToken(session.getSessionToken());
        return createdResult("Consent to research has been recorded.");
    }
}
