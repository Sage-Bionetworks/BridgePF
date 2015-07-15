package org.sagebionetworks.bridge.play.controllers;

import static org.sagebionetworks.bridge.Roles.RESEARCHER;

import java.util.List;

import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.StudyConsent;
import org.sagebionetworks.bridge.models.studies.StudyConsentForm;
import org.sagebionetworks.bridge.models.studies.StudyConsentView;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.services.StudyConsentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import play.mvc.Result;

@Controller
public class StudyConsentController extends BaseController {

    private StudyConsentService studyConsentService;

    @Autowired
    public void setStudyConsentService(StudyConsentService studyConsentService) {
        this.studyConsentService = studyConsentService;
    }
    
    public Result getAllConsentsV3() throws Exception {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        StudyIdentifier studyId = session.getStudyIdentifier();
        List<StudyConsent> consents = studyConsentService.getAllConsents(studyId);
        return okResult(consents);
    }

    public Result getActiveConsentV3() throws Exception {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        StudyIdentifier studyId = session.getStudyIdentifier();
        StudyConsentView consent = studyConsentService.getActiveConsent(studyId);
        return okResult(consent);
    }
    
    public Result getMostRecentConsentV3() throws Exception {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        StudyIdentifier studyId = session.getStudyIdentifier();
        StudyConsentView consent = studyConsentService.getMostRecentConsent(studyId);
        return okResult(consent);
    }

    public Result getConsentV3(String createdOn) throws Exception {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        StudyIdentifier studyId = session.getStudyIdentifier();
        long timestamp = DateUtils.convertToMillisFromEpoch(createdOn);
        StudyConsentView consent = studyConsentService.getConsent(studyId, timestamp);
        return okResult(consent);
    }
    
    public Result addConsentV3() throws Exception {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        StudyIdentifier studyId = session.getStudyIdentifier();
        StudyConsentForm form = parseJson(request(), StudyConsentForm.class);
        StudyConsentView studyConsent = studyConsentService.addConsent(studyId, form);
        return createdResult(studyConsent);
    }

    public Result setActiveConsentV3(String createdOn) throws Exception {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        StudyIdentifier studyId = session.getStudyIdentifier();
        long timestamp = DateUtils.convertToMillisFromEpoch(createdOn);
        studyConsentService.activateConsent(studyId, timestamp);
        return okResult("Consent document set as active.");
    }

    @Deprecated
    public Result getAllConsentsV1() throws Exception {
        return getAllConsentsV3();
    }
    @Deprecated
    public Result getActiveConsentV1() throws Exception {
        return getActiveConsentV3();
    }
    @Deprecated
    public Result getMostRecentConsentV1() throws Exception {
        return getMostRecentConsentV3();
    }
    @Deprecated
    public Result getConsentV1(String createdOn) throws Exception {
        return getConsentV3(createdOn);
    }
    @Deprecated
    public Result addConsentV1() throws Exception {
        return addConsentV3();
    }
    @Deprecated
    public Result setActiveConsentV1(String createdOn) throws Exception {
        return setActiveConsentV3(createdOn);
    }
}
