package org.sagebionetworks.bridge.play.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.accounts.DataGroups;
import org.sagebionetworks.bridge.models.accounts.SignUp;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.UserAdminService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import play.mvc.Result;

import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;

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
        SignUp signUp = parseJson(request(), SignUp.class);

        boolean consent = JsonUtils.asBoolean(node, CONSENT_FIELD);
        
        userAdminService.createUser(signUp, study, null, false, consent);

        return createdResult("User created.");
    }

    public Result invalidateUserSession(String email) throws Exception {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());

        userAdminService.invalidateUserSession(study, email);

        return okResult("User session invalidated.");
    }

    public Result deleteUser(String email) throws Exception {
        UserSession session = getAuthenticatedSession(ADMIN);
        Study study = studyService.getStudy(session.getStudyIdentifier());
        
        userAdminService.deleteUser(study, email);
        
        return okResult("User deleted.");
    }

    /** Researcher API to set data groups for the given user. Note that this overwrites data groups, not adds. */
    public Result updateDataGroupForUser(String email) {
        UserSession session = getAuthenticatedSession(RESEARCHER);
        Study study = studyService.getStudy(session.getStudyIdentifier());

        DataGroups dataGroups = parseJson(request(), DataGroups.class);
        userAdminService.updateDataGroupForUser(study, email, dataGroups);

        return okResult("Data groups updated.");
    }
}
