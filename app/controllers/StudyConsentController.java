package controllers;

import java.util.List;

import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyConsent;
import org.sagebionetworks.bridge.models.studies.StudyConsentForm;
import org.sagebionetworks.bridge.services.StudyConsentService;

import play.mvc.Result;

public class StudyConsentController extends BaseController {

    private StudyConsentService studyConsentService;

    public void setStudyConsentService(StudyConsentService studyConsentService) {
        this.studyConsentService = studyConsentService;
    }

    public Result getAllConsents() throws Exception {
        Study study = studyService.getStudyByHostname(getHostname());
        getAuthenticatedResearcherOrAdminSession(study);
        List<StudyConsent> consents = studyConsentService.getAllConsents(study.getKey());
        return okResult(consents);
    }

    public Result getActiveConsent() throws Exception {
        Study study = studyService.getStudyByHostname(getHostname());
        getAuthenticatedResearcherOrAdminSession(study);
        StudyConsent consent = studyConsentService.getActiveConsent(study.getKey());
        return okResult(consent);
    }

    public Result getConsent(String createdOn) throws Exception {
        Study study = studyService.getStudyByHostname(getHostname());
        getAuthenticatedResearcherOrAdminSession(study);
        long timestamp = DateUtils.convertToMillisFromEpoch(createdOn);
        StudyConsent consent = studyConsentService.getConsent(study.getKey(), timestamp);
        return okResult(consent);
    }

    public Result addConsent() throws Exception {
        Study study = studyService.getStudyByHostname(getHostname());
        getAuthenticatedResearcherOrAdminSession(study);
        StudyConsentForm form = StudyConsentForm.fromJson(requestToJSON(request()));
        StudyConsent studyConsent = studyConsentService.addConsent(study.getKey(), form);
        return createdResult(studyConsent);
    }

    public Result setActiveConsent(String createdOn) throws Exception {
        Study study = studyService.getStudyByHostname(getHostname());
        getAuthenticatedResearcherOrAdminSession(study);
        long timestamp = DateUtils.convertToMillisFromEpoch(createdOn);
        studyConsentService.activateConsent(study.getKey(), timestamp);
        return okResult("Consent document set as active.");
    }
}
