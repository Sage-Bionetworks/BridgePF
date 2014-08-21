package controllers;

import java.util.Collection;

import org.apache.http.HttpStatus;
import org.sagebionetworks.bridge.dao.StudyConsentDao;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.StudyConsent;
import org.sagebionetworks.bridge.models.User;

import com.fasterxml.jackson.databind.JsonNode;

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

    public Result getConsents() throws Exception {
        User user = getSession().getUser();
        if (!user.isInRole("admin")) {
            throw new BridgeServiceException("Must be admin to change consent document.", HttpStatus.SC_FORBIDDEN);
        }
        String studyKey = studyControllerService.getStudyByHostname(request()).getKey();
        Collection<StudyConsent> consents = studyConsentDao.getConsents(studyKey);

        return ok(Json.toJson(consents));
    }

    public Result getActiveConsent() throws Exception {
        User user = getSession().getUser();
        if (!user.isInRole("admin")) {
            throw new BridgeServiceException("Must be admin to change consent document.", HttpStatus.SC_FORBIDDEN);
        }
        String studyKey = studyControllerService.getStudyByHostname(request()).getKey();
        StudyConsent consent = studyConsentDao.getConsent(studyKey);

        return ok(Json.toJson(consent));
    }

    public Result getConsent(long timestamp) throws Exception {
        User user = getSession().getUser();
        if (!user.isInRole("admin")) {
            throw new BridgeServiceException("Must be admin to change consent document.", HttpStatus.SC_FORBIDDEN);
        }

        String studyKey = studyControllerService.getStudyByHostname(request()).getKey();
        StudyConsent consent = studyConsentDao.getConsent(studyKey, timestamp);

        return ok(Json.toJson(consent));

    }

    public Result addConsent() throws Exception {
        User user = getSession().getUser();
        if (!user.isInRole("admin")) {
            throw new BridgeServiceException("Must be admin to add a consent document.", HttpStatus.SC_FORBIDDEN);
        }

        String studyKey = studyControllerService.getStudyByHostname(request()).getKey();

        JsonNode json = request().body().asJson();
        String path = json.get("path").asText();
        int minAge = json.get("minAge").asInt();

        studyConsentDao.addConsent(studyKey, path, minAge);

        return okResult("Consent document added.");
    }

    public Result setActiveConsent(long timestamp) throws Exception {
        User user = getSession().getUser();
        if (!user.isInRole("admin")) {
            throw new BridgeServiceException("Must be admin to change consent document.", HttpStatus.SC_FORBIDDEN);
        }

        String studyKey = studyControllerService.getStudyByHostname(request()).getKey();
        StudyConsent studyConsent = studyConsentDao.getConsent(studyKey);
        studyConsentDao.setActive(studyConsent);

        return okResult("Consent document set as active.");
    }

}
