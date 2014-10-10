package controllers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.SignUp;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.UserSession;
import org.sagebionetworks.bridge.services.UserAdminService;
import org.sagebionetworks.bridge.stormpath.StormpathFactory;

import play.mvc.Result;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stormpath.sdk.account.Account;
import com.stormpath.sdk.account.AccountList;
import com.stormpath.sdk.application.Application;
import com.stormpath.sdk.client.Client;

public class AdminUserMgmtController extends AdminController {

    private static final ObjectMapper mapper = new ObjectMapper();

    private static final String EMAIL_FIELD = "email";
    private static final String USERNAME_FIELD = "username";
    private static final String PASSWORD_FIELD = "password";
    private static final String ROLES_FIELD = "roles";
    private static final String CONSENT_FIELD = "consent";

    private Client stormpathClient;
    private UserAdminService userAdminService;

    public void setStormpathClient(Client stormpathClient) {
        this.stormpathClient = stormpathClient;
    }

    public void setUserAdminService(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    public Result createUser() throws Exception {
        getAuthenticatedAdminSession();

        JsonNode node = requestToJSON(request());
        String email = JsonUtils.asText(node, EMAIL_FIELD);
        String username = JsonUtils.asText(node, USERNAME_FIELD);
        String password = JsonUtils.asText(node, PASSWORD_FIELD);
        List<String> roles = JsonUtils.asStringList(node, ROLES_FIELD);
        boolean consent = JsonUtils.asBoolean(node, CONSENT_FIELD);

        Study userStudy = studyService.getStudyByHostname(getHostname());
        boolean signUserIn = false; // can't sign user in because admin is signed in.

        UserSession session = userAdminService.createUser(new SignUp(email, username, password), roles, userStudy,
                signUserIn, consent);

        return okResult(mapper.writeValueAsString(session));
    }

    public Result deleteUser(String email) throws Exception {
        getAuthenticatedAdminSession();


        User user = getUser(email);
        userAdminService.deleteUser(user);

        return okResult("Deleted user successfully.");
    }

    public Result revokeAllConsentRecords() throws Exception {
        getAuthenticatedAdminSession();

        Study study = studyService.getStudyByHostname(getHostname());
        JsonNode node = requestToJSON(request());
        String email = JsonUtils.asText(node, EMAIL_FIELD);

        User user = getUser(email);
        userAdminService.revokeAllConsentRecords(user, study);

        return okResult("Revoked all consent records successfully.");
    }

    private User getUser(String email) {
        Application app = StormpathFactory.createStormpathApplication(stormpathClient);
        Map<String, Object> queryParams = new HashMap<String, Object>();
        queryParams.put("email", email);
        AccountList accounts = app.getAccounts(queryParams);

        User user = null;
        for (Account account : accounts) {
            user = new User(account);
            break; // should only create one user.
        }

        return user;
    }
}
