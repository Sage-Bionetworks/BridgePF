package org.sagebionetworks.bridge.play.controllers;

import static org.sagebionetworks.bridge.Roles.ADMIN;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import play.mvc.Result;

import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.accounts.UserSessionInfo;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.UserAdminService;

@Controller
public class UserManagementController extends BaseController {

    private static final String CONSENT_FIELD = "consent";

    private UserAdminService userAdminService;

    @Autowired
    public void setUserAdminService(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    public Result createUser() throws Exception {
        UserSession session = getAuthenticatedSession(ADMIN);
        Study study = studyService.getStudy(session.getStudyIdentifier());

        JsonNode node = requestToJSON(request());
        StudyParticipant participant = parseJson(request(), StudyParticipant.class);

        boolean consent = JsonUtils.asBoolean(node, CONSENT_FIELD);
        
        UserSession userSession = userAdminService.createUser(study, participant, null, false, consent);

        return createdResult(UserSessionInfo.toJSON(userSession));
    }

    /**
     * admin api used to create consent/not-consent user for given study
     * nearly identical to createUser() one
     * @param studyId
     * @return
     * @throws Exception
     */
    public Result createUserWithStudyId(String studyId) throws Exception {
        getAuthenticatedSession(ADMIN);
        Study study = studyService.getStudy(studyId);

        JsonNode node = requestToJSON(request());
        StudyParticipant participant = parseJson(request(), StudyParticipant.class);

        boolean consent = JsonUtils.asBoolean(node, CONSENT_FIELD);

        userAdminService.createUser(study, participant, null, false, consent);

        return createdResult("User created.");
    }

    public Result deleteUser(String userId) throws Exception {
        UserSession session = getAuthenticatedSession(ADMIN);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        userAdminService.deleteUser(study, userId);
        
        return okResult("User deleted.");
    }
}
