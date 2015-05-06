package controllers;

import java.util.List;

import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.models.studies.StudyConsent;
import org.sagebionetworks.bridge.models.studies.StudyConsentForm;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.services.StudyConsentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import play.mvc.Result;

@Controller("studyConsentController")
public class StudyConsentController extends BaseController {

    private StudyConsentService studyConsentService;

    @Autowired
    public void setStudyConsentService(StudyConsentService studyConsentService) {
        this.studyConsentService = studyConsentService;
    }

    public Result getAllConsents() throws Exception {
        UserSession session = getAuthenticatedResearcherSession();
        StudyIdentifier studyId = session.getStudyIdentifier();
        List<StudyConsent> consents = studyConsentService.getAllConsents(studyId);
        return okResult(consents);
    }

    public Result getActiveConsent() throws Exception {
        UserSession session = getAuthenticatedResearcherSession();
        StudyIdentifier studyId = session.getStudyIdentifier();
        StudyConsent consent = studyConsentService.getActiveConsent(studyId);
        return okResult(consent);
    }

    public Result getConsent(String createdOn) throws Exception {
        UserSession session = getAuthenticatedResearcherSession();
        StudyIdentifier studyId = session.getStudyIdentifier();
        long timestamp = DateUtils.convertToMillisFromEpoch(createdOn);
        StudyConsent consent = studyConsentService.getConsent(studyId, timestamp);
        return okResult(consent);
    }

    public Result addConsent() throws Exception {
        UserSession session = getAuthenticatedResearcherSession();
        StudyIdentifier studyId = session.getStudyIdentifier();
        StudyConsentForm form = parseJson(request(), StudyConsentForm.class);
        StudyConsent studyConsent = studyConsentService.addConsent(studyId, form);
        return createdResult(studyConsent);
    }

    public Result setActiveConsent(String createdOn) throws Exception {
        UserSession session = getAuthenticatedResearcherSession();
        StudyIdentifier studyId = session.getStudyIdentifier();
        long timestamp = DateUtils.convertToMillisFromEpoch(createdOn);
        studyConsentService.activateConsent(studyId, timestamp);
        return okResult("Consent document set as active.");
    }
}
