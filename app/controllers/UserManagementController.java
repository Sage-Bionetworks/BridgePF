package controllers;

import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.SignUp;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.UserAdminService;

import play.mvc.Result;

import com.fasterxml.jackson.databind.JsonNode;

public class UserManagementController extends BaseController {

    private static final String CONSENT_FIELD = "consent";

    private UserAdminService userAdminService;

    public void setUserAdminService(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    public Result createUser() throws Exception {
        UserSession session = getAuthenticatedAdminSession();
        Study study = studyService.getStudy(session.getStudyIdentifier());

        JsonNode node = requestToJSON(request());
        SignUp signUp = SignUp.fromJson(node, true);

        boolean consent = JsonUtils.asBoolean(node, CONSENT_FIELD);

        userAdminService.createUser(signUp, study, false, consent);

        return createdResult("User created.");
    }

    public Result deleteUser(String email) throws Exception {
        getAuthenticatedAdminSession();
        userAdminService.deleteUser(email);

        return okResult("User deleted.");
    }

}
