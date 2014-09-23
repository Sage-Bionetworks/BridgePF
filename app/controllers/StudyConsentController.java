package controllers;

import java.util.List;

import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.StudyConsent;
import org.sagebionetworks.bridge.models.StudyConsentForm;
import org.sagebionetworks.bridge.services.StudyConsentService;

import play.mvc.Result;

public class StudyConsentController extends AdminController {

    private StudyConsentService studyConsentService;

    public void setStudyConsentService(StudyConsentService studyConsentService) {
        this.studyConsentService = studyConsentService;
    }

    public Result getAllConsents() throws Exception {
        getAuthenticatedAdminSession();
        String studyKey = studyService.getStudyByHostname(getHostname()).getKey();
        List<StudyConsent> consents = studyConsentService.getAllConsents(studyKey);
        return ok(constructJSON(consents));
    }

    public Result getActiveConsent() throws Exception {
        getAuthenticatedAdminSession();
        String studyKey = studyService.getStudyByHostname(getHostname()).getKey();
        StudyConsent consent = studyConsentService.getActiveConsent(studyKey);
        return ok(constructJSON(consent));
    }

    public Result getConsent(String createdOn) throws Exception {
        getAuthenticatedAdminSession();
        String studyKey = studyService.getStudyByHostname(getHostname()).getKey();
        long timestamp = DateUtils.convertToMillisFromEpoch(createdOn);
        StudyConsent consent = studyConsentService.getConsent(studyKey, timestamp);
        return ok(constructJSON(consent));
    }

    public Result addConsent() throws Exception {
        getAuthenticatedAdminSession();
        String studyKey = studyService.getStudyByHostname(getHostname()).getKey();
        StudyConsentForm form = StudyConsentForm.fromJson(requestToJSON(request()));
        StudyConsent studyConsent = studyConsentService.addConsent(studyKey, form);
        return ok(constructJSON(studyConsent));
    }

    public Result setActiveConsent(String createdOn) throws Exception {
        getAuthenticatedAdminSession();
        String studyKey = studyService.getStudyByHostname(getHostname()).getKey();
        long timestamp = DateUtils.convertToMillisFromEpoch(createdOn);
        studyConsentService.activateConsent(studyKey, timestamp);
        return okResult("Consent document set as active.");
    }
}
