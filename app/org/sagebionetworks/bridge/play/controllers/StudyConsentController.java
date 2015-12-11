package org.sagebionetworks.bridge.play.controllers;

import static org.sagebionetworks.bridge.Roles.RESEARCHER;

import java.util.List;

import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsent;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsentForm;
import org.sagebionetworks.bridge.models.subpopulations.StudyConsentView;
import org.sagebionetworks.bridge.services.StudyConsentService;
import org.sagebionetworks.bridge.services.SubpopulationService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import play.mvc.Result;

@Controller
public class StudyConsentController extends BaseController {

    private StudyConsentService studyConsentService;
    
    private SubpopulationService subpopService;

    @Autowired
    final void setStudyConsentService(StudyConsentService studyConsentService) {
        this.studyConsentService = studyConsentService;
    }

    @Autowired
    final void setSubpopulationService(SubpopulationService subpopService) {
        this.subpopService = subpopService;
    }

    // V1 API: consents directly associated to a study
    
    @Deprecated
    public Result getAllConsents() throws Exception {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        StudyIdentifier studyId = session.getStudyIdentifier();
        List<StudyConsent> consents = studyConsentService.getAllConsents(studyId.getIdentifier());
        return okResult(consents);
    }

    @Deprecated
    public Result getActiveConsent() throws Exception {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        StudyIdentifier studyId = session.getStudyIdentifier();
        StudyConsentView consent = studyConsentService.getActiveConsent(studyId.getIdentifier());
        return okResult(consent);
    }
    
    @Deprecated
    public Result getMostRecentConsent() throws Exception {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        StudyIdentifier studyId = session.getStudyIdentifier();
        StudyConsentView consent = studyConsentService.getMostRecentConsent(studyId.getIdentifier());
        return okResult(consent);
    }

    @Deprecated
    public Result getConsent(String createdOn) throws Exception {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        StudyIdentifier studyId = session.getStudyIdentifier();
        long timestamp = DateUtils.convertToMillisFromEpoch(createdOn);
        StudyConsentView consent = studyConsentService.getConsent(studyId.getIdentifier(), timestamp);
        return okResult(consent);
    }
    
    @Deprecated
    public Result addConsent() throws Exception {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        StudyIdentifier studyId = session.getStudyIdentifier();
        StudyConsentForm form = parseJson(request(), StudyConsentForm.class);
        StudyConsentView studyConsent = studyConsentService.addConsent(studyId.getIdentifier(), form);
        return createdResult(studyConsent);
    }

    @Deprecated
    public Result publishConsent(String createdOn) throws Exception {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        long timestamp = DateUtils.convertToMillisFromEpoch(createdOn);
        studyConsentService.publishConsent(study, study.getIdentifier(), timestamp);
        return okResult("Consent document set as active.");
    }
    
    // V2: consents associated to a subpopulation
    
    public Result getAllConsentsV2(String guid) throws Exception {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        StudyIdentifier studyId = session.getStudyIdentifier();
        
        // Throws 404 exception if this subpopulation is not part of the caller's study
        subpopService.getSubpopulation(studyId, guid);
        
        List<StudyConsent> consents = studyConsentService.getAllConsents(guid);
        return okResult(consents);
    }

    public Result getActiveConsentV2(String guid) throws Exception {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        StudyIdentifier studyId = session.getStudyIdentifier();
        
        // Throws 404 exception if this subpopulation is not part of the caller's study
        subpopService.getSubpopulation(studyId, guid);
        
        StudyConsentView consent = studyConsentService.getActiveConsent(guid);
        return okResult(consent);
    }
    
    public Result getMostRecentConsentV2(String guid) throws Exception {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        StudyIdentifier studyId = session.getStudyIdentifier();
        
        // Throws 404 exception if this subpopulation is not part of the caller's study
        subpopService.getSubpopulation(studyId, guid);
        
        StudyConsentView consent = studyConsentService.getMostRecentConsent(guid);
        return okResult(consent);
    }

    public Result getConsentV2(String guid, String createdOn) throws Exception {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        StudyIdentifier studyId = session.getStudyIdentifier();
        
        // Throws 404 exception if this subpopulation is not part of the caller's study
        subpopService.getSubpopulation(studyId, guid);

        long timestamp = DateUtils.convertToMillisFromEpoch(createdOn);
        StudyConsentView consent = studyConsentService.getConsent(guid, timestamp);
        return okResult(consent);
    }
    
    public Result addConsentV2(String guid) throws Exception {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        StudyIdentifier studyId = session.getStudyIdentifier();
        
        // Throws 404 exception if this subpopulation is not part of the caller's study
        subpopService.getSubpopulation(studyId, guid);

        StudyConsentForm form = parseJson(request(), StudyConsentForm.class);
        StudyConsentView studyConsent = studyConsentService.addConsent(guid, form);
        return createdResult(studyConsent);
    }

    public Result publishConsentV2(String guid, String createdOn) throws Exception {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        // Throws 404 exception if this subpopulation is not part of the caller's study
        subpopService.getSubpopulation(study.getStudyIdentifier(), guid);

        long timestamp = DateUtils.convertToMillisFromEpoch(createdOn);
        studyConsentService.publishConsent(study, guid, timestamp);
        return okResult("Consent document set as active.");
    }
    
}
