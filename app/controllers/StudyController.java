package controllers;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.models.VersionHolder;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.services.UserProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import play.libs.Json;
import play.mvc.Result;

@Controller("studyController")
public class StudyController extends BaseController {

    private final Set<String> studyWhitelist = Collections.unmodifiableSet(new HashSet<>(
        BridgeConfigFactory.getConfig().getPropertyAsList("study.whitelist")));

    private UserProfileService userProfileService;

    @Autowired
    public void setUserProfileService(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    public Result getStudyForResearcher() throws Exception {
        UserSession session = getAuthenticatedResearcherSession();
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        return ok(Study.STUDY_WRITER.writeValueAsString(study));
    }
    
    public Result sendStudyParticipantsRoster() throws Exception {
        // Researchers only, administrators cannot get this list so easily
        UserSession session = getAuthenticatedResearcherSession();
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        userProfileService.sendStudyParticipantRoster(study);
        return okResult("A roster of study participants will be emailed to the study's consent notification contact.");
    }

    public Result updateStudyForResearcher() throws Exception {
        UserSession session = getAuthenticatedResearcherSession();
        StudyIdentifier studyId = session.getStudyIdentifier();

        Study studyUpdate = parseJson(request(), Study.class);
        studyUpdate.setIdentifier(studyId.getIdentifier());
        studyUpdate = studyService.updateStudy(studyUpdate);
        return okResult(new VersionHolder(studyUpdate.getVersion()));
    }

    public Result updateStudy(String identifier) throws Exception {
        getAuthenticatedAdminSession();

        Study studyUpdate = parseJson(request(), Study.class);
        studyUpdate = studyService.updateStudy(studyUpdate);
        return okResult(new VersionHolder(studyUpdate.getVersion()));
    }

    public Result getStudy(String identifier) throws Exception {
        getAuthenticatedAdminSession();

        Study study = studyService.getStudy(identifier);
        return ok(Study.STUDY_WRITER.writeValueAsString(study));
    }

    public Result getAllStudies() throws Exception {
        getAuthenticatedAdminSession();

        return ok(Study.STUDY_WRITER.writeValueAsString(studyService.getStudies()));
    }

    public Result createStudy() throws Exception {
        getAuthenticatedAdminSession();

        Study study = parseJson(request(), Study.class);
        study = studyService.createStudy(study);
        return okResult(new VersionHolder(study.getVersion()));
    }

    public Result deleteStudy(String identifier) throws Exception {
        getAuthenticatedAdminSession();
        if (studyWhitelist.contains(identifier)) {
            return forbidden(Json.toJson(identifier + " is protected by whitelist."));
        }
        studyService.deleteStudy(identifier);
        return okResult("Study deleted.");
    }
}
