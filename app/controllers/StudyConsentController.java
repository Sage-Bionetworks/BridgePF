package controllers;

import java.util.List;

import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.StudyConsent;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.services.StudyConsentService;

import com.fasterxml.jackson.databind.JsonNode;

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

    public Result getConsent(long timestamp) throws Exception {
        User user = getSession().getUser();
        String studyKey = studyControllerService.getStudyByHostname(request()).getKey();
        
        StudyConsent consent = studyConsentService.getConsent(user, studyKey, timestamp);
        return ok(Json.toJson(consent));

    }
    
    public Result addConsent() throws Exception {
        User user = getSession().getUser();
        String studyKey = studyControllerService.getStudyByHostname(request()).getKey();
        
        JsonNode json = requestToJSON(request());
        fieldsValid(json);
        String path = json.get("path").asText();
        int minAge = json.get("minAge").asInt();
        
        StudyConsent studyConsent = studyConsentService.addConsent(user, studyKey, path, minAge);
        return ok(Json.toJson(studyConsent));
    }

    public Result setActiveConsent(long timestamp) throws Exception {
        User user = getSession().getUser();
        String studyKey = studyControllerService.getStudyByHostname(request()).getKey();
        
        studyConsentService.activateConsent(user, studyKey, timestamp);

        return okResult("Consent document set as active.");
    }

    private void fieldsValid(JsonNode json) {
        if (json.get("path") == null) {
            throw new BridgeServiceException("Path field is null.", BAD_REQUEST);
        } else if (json.get("path").asText().isEmpty()) {
            throw new BridgeServiceException("Path field is empty.", BAD_REQUEST);
        } else if (json.get("minAge") == null) {
            throw new BridgeServiceException("minAge field is null.", BAD_REQUEST);
        } else if (json.get("minAge").asText().isEmpty()) {
            throw new BridgeServiceException("minAge field is empty.", BAD_REQUEST);
        }
    }

}
