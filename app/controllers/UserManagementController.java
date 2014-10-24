package controllers;

import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.SignUp;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.services.UserAdminService;

import play.mvc.Result;

import com.fasterxml.jackson.databind.JsonNode;

public class UserManagementController extends AdminController {

    private static final String EMAIL_FIELD = "email";
    private static final String CONSENT_FIELD = "consent";

    private UserAdminService userAdminService;

    public void setUserAdminService(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    public Result createUser() throws Exception {
        getAuthenticatedAdminSession();

        Study study = studyService.getStudyByHostname(getHostname());

        JsonNode node = requestToJSON(request());
        SignUp signUp = SignUp.fromJson(node, true);

        boolean consent = JsonUtils.asBoolean(node, CONSENT_FIELD);

        // If you don't sign the user in, there will certainly be no session
        UserSession session = userAdminService.createUser(signUp, study, true, consent);
        session.getUser().setHealthDataCode(null);

        return createdResult(session);
    }

    public Result deleteUser(String email) throws Exception {
        getAuthenticatedAdminSession();
        Study study = studyService.getStudyByHostname(getHostname());

        User user = authenticationService.getUser(study, email);
        userAdminService.deleteUser(user);

        return okResult("Deleted user successfully.");
    }

    public Result revokeAllConsentRecords(String email) throws Exception {
        getAuthenticatedAdminSession();
        Study study = studyService.getStudyByHostname(getHostname());

        User user = authenticationService.getUser(study, email);
        userAdminService.revokeAllConsentRecords(user, study);

        return okResult("Revoked all consent records successfully.");
    }
}
