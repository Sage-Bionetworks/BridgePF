package controllers;

import java.util.List;

import org.sagebionetworks.bridge.models.DateConverter;
import org.sagebionetworks.bridge.models.StudyConsent;
import org.sagebionetworks.bridge.models.StudyConsentForm;
import org.sagebionetworks.bridge.services.StudyConsentService;

import play.mvc.Result;

public class StudyConsentController extends AdminController {

    private StudyConsentService studyConsentService;
    private StudyControllerService studyControllerService;

    public void setStudyConsentService(StudyConsentService studyConsentService) {
        this.studyConsentService = studyConsentService;
    }

    public void setStudyControllerService(StudyControllerService studyControllerService) {
        this.studyControllerService = studyControllerService;
    }

    public Result getAllConsents() throws Exception {
        checkForAdmin();
        String studyKey = studyControllerService.getStudyByHostname(request()).getKey();
        List<StudyConsent> consents = studyConsentService.getAllConsents(studyKey);
        return ok(Json.toJson(consents));
    }

    public Result getActiveConsent() throws Exception {
        checkForAdmin();
        String studyKey = studyControllerService.getStudyByHostname(request()).getKey();
        StudyConsent consent = studyConsentService.getActiveConsent(studyKey);
        return ok(Json.toJson(consent));
    }

    public Result getConsent(String createdOn) throws Exception {
        checkForAdmin();
        String studyKey = studyControllerService.getStudyByHostname(request()).getKey();
	long timestamp = DateConverter.convertMillisFromEpoch(createdOn);
        StudyConsent consent = studyConsentService.getConsent(studyKey, timestamp);
        return ok(Json.toJson(consent));
    }

    public Result addConsent() throws Exception {
        checkForAdmin();
        String studyKey = studyControllerService.getStudyByHostname(request()).getKey();
        StudyConsentForm form = StudyConsentForm.fromJson(requestToJSON(request()));
        StudyConsent studyConsent = studyConsentService.addConsent(studyKey, form);
        return ok(Json.toJson(studyConsent));
    }

    public Result setActiveConsent(String createdOn) throws Exception {
        checkForAdmin();
        String studyKey = studyControllerService.getStudyByHostname(request()).getKey();
	long timestamp = DateConverter.convertMillisFromEpoch(createdOn);
        studyConsentService.activateConsent(studyKey, timestamp);
        return okResult("Consent document set as active.");
    }
}
