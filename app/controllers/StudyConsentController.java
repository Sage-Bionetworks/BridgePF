package controllers;

import java.util.List;

import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.StudyConsent;
import org.sagebionetworks.bridge.models.StudyConsentSummary;
import org.sagebionetworks.bridge.models.User;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;

import play.libs.Json;
import play.mvc.Result;

public class StudyConsentController extends BaseController {

    private StudyConsentDao studyConsentDao;
    private StudyControllerService studyControllerService;

    public void setStudyConsentDao(StudyConsentDao studyConsentDao) {
        this.studyConsentDao = studyConsentDao;
    }

    public void setStudyControllerService(StudyControllerService studyControllerService) {
        this.studyControllerService = studyControllerService;
    }

    public Result getAllConsents() throws Exception {
        User user = getSession().getUser();
        if (!user.isInRole("admin")) {
            throw new BridgeServiceException("Must be admin to change consent document.", FORBIDDEN);
        }
        String studyKey = studyControllerService.getStudyByHostname(request()).getKey();
        List<StudyConsent> consents = studyConsentDao.getConsents(studyKey);
        
        List<StudyConsentSummary> summaries = Lists.newArrayList();
        for (StudyConsent consent : consents) {
            summaries.add(new StudyConsentSummary(consent));
        }
        return ok(Json.toJson(summaries));
    }

    public Result getActiveConsent() throws Exception {
        User user = getSession().getUser();
        if (!user.isInRole("admin")) {
            throw new BridgeServiceException("Must be admin to change consent document.", FORBIDDEN);
        }
        
        String studyKey = studyControllerService.getStudyByHostname(request()).getKey();
        StudyConsent consent = studyConsentDao.getConsent(studyKey);
        if (consent == null) {
            throw new BridgeServiceException("There is no active consent document.", BAD_REQUEST);
        }
        
        StudyConsentSummary summary = new StudyConsentSummary(consent);
        return ok(Json.toJson(summary));
    }

    public Result getConsent(long timestamp) throws Exception {
        User user = getSession().getUser();
        if (!user.isInRole("admin")) {
            throw new BridgeServiceException("Must be admin to change consent document.", FORBIDDEN);
        }

        String studyKey = studyControllerService.getStudyByHostname(request()).getKey();
        StudyConsent consent = studyConsentDao.getConsent(studyKey, timestamp);

        StudyConsentSummary summary = new StudyConsentSummary(consent);
        return ok(Json.toJson(summary));

    }
    
    public Result addConsent() throws Exception {
        User user = getSession().getUser();
        if (!user.isInRole("admin")) {
            throw new BridgeServiceException("Must be admin to add a consent document.", FORBIDDEN);
        }

        String studyKey = studyControllerService.getStudyByHostname(request()).getKey();
        StudyConsent studyConsent = studyConsentDao.getConsent(studyKey);
        if (studyConsent != null) {
            studyConsentDao.setActive(studyConsent, false);
        }
        
        JsonNode json = requestToJSON(request());
        fieldsValid(json);
        String path = json.get("path").asText();
        int minAge = json.get("minAge").asInt();
        
        studyConsent = studyConsentDao.addConsent(studyKey, path, minAge);
        studyConsentDao.setActive(studyConsent, true);

        StudyConsentSummary summary = new StudyConsentSummary(studyConsent);
        return ok(Json.toJson(summary));
    }

    public Result setActiveConsent(long timestamp) throws Exception {
        User user = getSession().getUser();
        if (!user.isInRole("admin")) {
            throw new BridgeServiceException("Must be admin to change consent document.", FORBIDDEN);
        }

        String studyKey = studyControllerService.getStudyByHostname(request()).getKey();
        StudyConsent studyConsent = studyConsentDao.getConsent(studyKey);
        if (studyConsent != null) {
            studyConsentDao.setActive(studyConsent, false);
        }
        
        studyConsent = studyConsentDao.getConsent(studyKey, timestamp);
        studyConsentDao.setActive(studyConsent, true);

        return okResult("Consent document set as active.");
    }

    public Result deleteConsent(long timestamp) throws Exception {
        User user = getSession().getUser();
        if (!user.isInRole("admin")) {
            throw new BridgeServiceException("Must be admin to change consent document.", FORBIDDEN);
        }
        
        String studyKey = studyControllerService.getStudyByHostname(request()).getKey();
        if(studyConsentDao.getConsent(studyKey, timestamp).getActive()) {
            throw new BridgeServiceException("Cannot delete active consent document.", BAD_REQUEST);
        }
        
        studyConsentDao.deleteConsent(studyKey, timestamp);
        
        return okResult("Consent document is deleted.");
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
