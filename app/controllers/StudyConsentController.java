package controllers;

import java.util.List;

import org.sagebionetworks.bridge.models.Date;
import org.sagebionetworks.bridge.models.StudyConsent;
import org.sagebionetworks.bridge.models.StudyConsentForm;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.services.StudyConsentService;

import play.libs.Json;
import play.mvc.Result;

public class StudyConsentController extends BaseController {

    private StudyConsentService studyConsentService;
    private StudyControllerService studyControllerService;

    public void setStudyConsentService(StudyConsentService studyConsentService) {
        this.studyConsentService = studyConsentService;
    }
    public void setStudyControllerService(StudyControllerService studyControllerService) {
        this.studyControllerService = studyControllerService;
    }

    public Result getAllConsents() throws Exception {
        User user = getSession().getUser();
        String studyKey = studyControllerService.getStudyByHostname(request()).getKey();

        List<StudyConsent> consents = studyConsentService.getAllConsents(user, studyKey);
        return ok(Json.toJson(consents));
    }

    public Result getActiveConsent() throws Exception {
        User user = getSession().getUser();
        String studyKey = studyControllerService.getStudyByHostname(request()).getKey();

        StudyConsent consent = studyConsentService.getActiveConsent(user, studyKey);
        return ok(Json.toJson(consent));
    }

    public Result getConsent(String timestamp) throws Exception {
        User user = getSession().getUser();
        String studyKey = studyControllerService.getStudyByHostname(request()).getKey();
        
        Date d = new Date(timestamp);
        
        StudyConsent consent = studyConsentService.getConsent(user, studyKey, d.getMillisFromEpoch());
        return ok(Json.toJson(consent));

    }
    
    public Result addConsent() throws Exception {
        User user = getSession().getUser();
        String studyKey = studyControllerService.getStudyByHostname(request()).getKey();
        StudyConsentForm form = StudyConsentForm.fromJson(requestToJSON(request()));
        
        StudyConsent studyConsent = studyConsentService.addConsent(user, studyKey, form);
        return ok(Json.toJson(studyConsent));
    }

    public Result setActiveConsent(String timestamp) throws Exception {
        User user = getSession().getUser();
        String studyKey = studyControllerService.getStudyByHostname(request()).getKey();
        
        Date d = new Date(timestamp);
        
        studyConsentService.activateConsent(user, studyKey, d.getMillisFromEpoch());

        return okResult("Consent document set as active.");
    }

}
