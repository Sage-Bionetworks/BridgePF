package org.sagebionetworks.bridge.play.controllers;

import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.VersionHolder;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.services.UserProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import play.libs.Json;
import play.mvc.Result;

@Controller
public class StudyController extends BaseController {

    private final Comparator<Study> STUDY_COMPARATOR = new Comparator<Study>() {
        public int compare(Study study1, Study study2) {
            return study1.getName().compareToIgnoreCase(study2.getName());
        }
    };
    
    private final Set<String> studyWhitelist = Collections.unmodifiableSet(new HashSet<>(BridgeConfigFactory
                    .getConfig().getPropertyAsList("study.whitelist")));

    private UserProfileService userProfileService;

    @Autowired
    public void setUserProfileService(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }
    
    public Result getStudyForDeveloperV3() throws Exception {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        Study study = studyService.getStudy(session.getStudyIdentifier());

        return ok(Study.STUDY_WRITER.writeValueAsString(study));
    }

    public Result sendStudyParticipantsRosterV3() throws Exception {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());

        userProfileService.sendStudyParticipantRoster(study);
        return okResult("A roster of study participants will be emailed to the study's consent notification contact.");
    }

    public Result updateStudyForDeveloperV3() throws Exception {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        StudyIdentifier studyId = session.getStudyIdentifier();

        Study studyUpdate = parseJson(request(), Study.class);
        studyUpdate.setIdentifier(studyId.getIdentifier());
        studyUpdate = studyService.updateStudy(studyUpdate, false);
        return okResult(new VersionHolder(studyUpdate.getVersion()));
    }

    public Result updateStudyV3(String identifier) throws Exception {
        getAuthenticatedSession(ADMIN);

        Study studyUpdate = parseJson(request(), Study.class);
        studyUpdate = studyService.updateStudy(studyUpdate, true);
        return okResult(new VersionHolder(studyUpdate.getVersion()));
    }

    public Result getStudyV3(String identifier) throws Exception {
        getAuthenticatedSession(ADMIN);

        Study study = studyService.getStudy(identifier);
        return ok(Study.STUDY_WRITER.writeValueAsString(study));
    }

    public Result getAllStudiesV3(String format) throws Exception {
        List<Study> studies = studyService.getStudies();
        if ("summary".equals(format)) {
            Collections.sort(studies, STUDY_COMPARATOR);
            return ok(Study.STUDY_LIST_WRITER.writeValueAsString(new ResourceList<Study>(studies)));
        }
        getAuthenticatedSession(ADMIN);

        return ok(Study.STUDY_WRITER.writeValueAsString(studies));
    }

    public Result createStudyV3() throws Exception {
        getAuthenticatedSession(ADMIN);

        Study study = parseJson(request(), Study.class);
        study = studyService.createStudy(study);
        return okResult(new VersionHolder(study.getVersion()));
    }

    public Result deleteStudyV3(String identifier) throws Exception {
        getAuthenticatedSession(ADMIN);
        if (studyWhitelist.contains(identifier)) {
            return forbidden(Json.toJson(identifier + " is protected by whitelist."));
        }
        studyService.deleteStudy(identifier);
        return okResult("Study deleted.");
    }

    @Deprecated
    public Result getStudyForDeveloperV1() throws Exception {
        return getStudyForDeveloperV3();
    }
    @Deprecated
    public Result sendStudyParticipantsRosterV1() throws Exception {
        return sendStudyParticipantsRosterV3();
    }
    @Deprecated
    public Result updateStudyForDeveloperV1() throws Exception {
        return updateStudyForDeveloperV3();
    }
    @Deprecated
    public Result updateStudyV1(String identifier) throws Exception {
        return updateStudyV3(identifier);
    }
    @Deprecated
    public Result getStudyV1(String identifier) throws Exception {
        return getStudyV3(identifier);
    }
    @Deprecated
    public Result getAllStudiesV1(String format) throws Exception {
        return getAllStudiesV3(format);
    }
    @Deprecated
    public Result createStudyV1() throws Exception {
        return createStudyV3();
    }
    @Deprecated
    public Result deleteStudyV1(String identifier) throws Exception {
        return deleteStudyV3(identifier);
    }
}
