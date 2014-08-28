package controllers;

import java.util.List;

import org.sagebionetworks.bridge.models.Date;
import org.sagebionetworks.bridge.models.StudyConsent;
import org.sagebionetworks.bridge.models.StudyConsentForm;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.services.StudyConsentService;

import play.mvc.Result;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

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
        return ok(constructJSON(consents));
    }

    public Result getActiveConsent() throws Exception {
        User user = getSession().getUser();
        String studyKey = studyControllerService.getStudyByHostname(request()).getKey();

        StudyConsent consent = studyConsentService.getActiveConsent(user, studyKey);
        JsonNode json = addTimestamp(consent);
        return ok(json);
    }

    public Result getConsent(long timestamp) throws Exception {
        User user = getSession().getUser();
        String studyKey = studyControllerService.getStudyByHostname(request()).getKey();
        
        Date d = new Date(timestamp);
        
        StudyConsent consent = studyConsentService.getConsent(user, studyKey, d.getMillisFromEpoch());
        JsonNode json = addTimestamp(consent);
        return ok(json);
    }
    
    public Result addConsent() throws Exception {
        User user = getSession().getUser();
        String studyKey = studyControllerService.getStudyByHostname(request()).getKey();
        StudyConsentForm form = StudyConsentForm.fromJson(requestToJSON(request()));
        
        StudyConsent consent = studyConsentService.addConsent(user, studyKey, form);
        JsonNode json = addTimestamp(consent);
        return ok(json);
    }

    public Result setActiveConsent(long timestamp) throws Exception {
        User user = getSession().getUser();
        String studyKey = studyControllerService.getStudyByHostname(request()).getKey();
        
        Date d = new Date(timestamp);
        
        studyConsentService.activateConsent(user, studyKey, d.getMillisFromEpoch());

        return okResult("Consent document set as active.");
    }
    
    private JsonNode addTimestamp(StudyConsent consent) {
        String timestamp = new Date(consent.getCreatedOn()).getISODateTime();
        ObjectNode node = (ObjectNode) constructJSON(consent);
        node.put("timestamp", timestamp);
        return (JsonNode) node;
    }

}
