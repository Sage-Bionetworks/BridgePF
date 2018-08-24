package org.sagebionetworks.bridge.play.controllers;

import java.util.Map;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.SharingOption;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.accounts.UserSessionInfo;
import org.sagebionetworks.bridge.models.accounts.Withdrawal;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.ConsentService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import play.mvc.BodyParser;
import play.mvc.Result;

@Controller
public class ConsentController extends BaseController {

    private ConsentService consentService;

    @Autowired
    final void setConsentService(ConsentService consentService) {
        this.consentService = consentService;
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
        
        return resendConsentAgreement(session.getStudyIdentifier().getIdentifier());
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
        final UserSession session = getAuthenticatedSession();
        final Study study = studyService.getStudy(session.getStudyIdentifier());
        
        return withdrawConsentV2(study.getIdentifier());
    }

    // V2: consent to a specific subpopulation
    
    public Result getConsentSignatureV2(String guid) throws Exception {
        final UserSession session = getAuthenticatedAndConsentedSession();
        final Study study = studyService.getStudy(session.getStudyIdentifier());

        ConsentSignature sig = consentService.getConsentSignature(study, SubpopulationGuid.create(guid), session.getId());
        return okResult(ConsentSignature.SIGNATURE_WRITER, sig);
    }
    
    public Result giveV3(String guid) throws Exception {
        return giveConsentForVersion(2, SubpopulationGuid.create(guid));
    }
    
    public Result withdrawConsentV2(String guid) throws Exception {
        final UserSession session = getAuthenticatedSession();
        final Withdrawal withdrawal = parseJson(request(), Withdrawal.class);
        final Study study = studyService.getStudy(session.getStudyIdentifier());
        final long withdrewOn = DateTime.now().getMillis();
        final SubpopulationGuid subpopGuid = SubpopulationGuid.create(guid);
        
        CriteriaContext context = getCriteriaContext(session);

        Map<SubpopulationGuid, ConsentStatus> statuses = consentService.withdrawConsent(study, subpopGuid,
                session.getParticipant(), context, withdrawal, withdrewOn);

        sessionUpdateService.updateConsentStatus(session, statuses, session.getParticipant().getSharingScope(), true);

        return okResult(UserSessionInfo.toJSON(session));
    }
    
    public Result withdrawFromAllConsents() {
        final UserSession session = getAuthenticatedSession();
        final Withdrawal withdrawal = parseJson(request(), Withdrawal.class);
        final Study study = studyService.getStudy(session.getStudyIdentifier());
        final long withdrewOn = DateTime.now().getMillis();
        
        CriteriaContext context = getCriteriaContext(session);
        
        Map<SubpopulationGuid, ConsentStatus> statuses = consentService.withdrawAllConsents(study, session.getParticipant(),
                context, withdrawal, withdrewOn);
        
        sessionUpdateService.updateConsentStatus(session, statuses, SharingScope.NO_SHARING, true);
        
        return okResult(UserSessionInfo.toJSON(session)); 
    }
    
    @BodyParser.Of(BodyParser.Empty.class)
    public Result resendConsentAgreement(String guid) {
        final UserSession session = getAuthenticatedAndConsentedSession();
        final Study study = studyService.getStudy(session.getStudyIdentifier());

        consentService.resendConsentAgreement(study, SubpopulationGuid.create(guid), session.getParticipant());
        return okResult("Signed consent agreement resent.");
    }
    
    Result changeSharingScope(SharingScope sharingScope, String message) {
        final UserSession session = getAuthenticatedAndConsentedSession();
        
        accountDao.editAccount(session.getStudyIdentifier(), session.getHealthCode(),
                account -> account.setSharingScope(sharingScope));

        sessionUpdateService.updateSharingScope(session, sharingScope);
        
        return okResult(UserSessionInfo.toJSON(session));
    }
    
    private Result giveConsentForVersion(int version, SubpopulationGuid subpopGuid) {
        final UserSession session = getAuthenticatedSession();
        final Study study = studyService.getStudy(session.getStudyIdentifier());

        final ConsentSignature consentSignature = ConsentSignature.fromJSON(requestToJSON(request()));
        final SharingOption sharing = SharingOption.fromJson(requestToJSON(request()), version);

        Map<SubpopulationGuid,ConsentStatus> consentStatuses = session.getConsentStatuses();
        
        ConsentStatus status = consentStatuses.get(subpopGuid);
        if (status == null) {
            throw new EntityNotFoundException(Subpopulation.class);
        }
        
        consentService.consentToResearch(study, subpopGuid, session.getParticipant(), consentSignature,
                sharing.getSharingScope(), true);
        
        CriteriaContext context = getCriteriaContext(session);
        Map<SubpopulationGuid,ConsentStatus> statuses = consentService.getConsentStatuses(context);
        
        sessionUpdateService.updateConsentStatus(session, statuses, sharing.getSharingScope(), false);

        return createdResult(UserSessionInfo.toJSON(session));
    }
}
