package org.sagebionetworks.bridge;

import java.util.List;

import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.phantomjs.PhantomJSDriver;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.models.SignIn;
import org.sagebionetworks.bridge.models.SignUp;

import com.google.common.collect.Lists;

public class TestConstants {

    public static class TestUser {
        private final String username;
        private final String email;
        private final String password;
        private final String[] roles;

        /*
        public TestUser(String username, String email, String password) {
            String prefix = BridgeConfigFactory.getConfig().getUser() + "-";
            this.username = prefix + username;
            this.email = prefix + email;
            this.password = password;
            this.roles = null;
        }
        public TestUser(String username, String email, String password, String... roles) {
            String prefix = BridgeConfigFactory.getConfig().getUser() + "-";
            this.username = prefix + username;
            this.email = prefix + email;
            this.password = password;
            this.roles = roles;
        }
        */
        public TestUser(String tag) {
            this(tag, Lists.<String>newArrayList());
        }
        public TestUser(String tag, List<String> roleList) {
            String prefix = BridgeConfigFactory.getConfig().getUser() + "-";
            this.username = prefix + tag;
            this.email = prefix + tag + "@sagebridge.org";
            this.password = "P4ssword";
            this.roles = (roleList == null) ? null : roleList.toArray(new String[roleList.size()]);
        }
        public SignUp getSignUp() {
            return new SignUp(username, email, password, roles);
        }
        public SignIn getSignIn() {
            return new SignIn(username, password);
        }
        public String getUsername() {
            return username;
        }
        public String getEmail() {
            return email;
        }
        public String getPassword() {
            return password;
        }
    }

    public static final String TEST_STUDY_KEY = "teststudy";
    public static final int TIMEOUT = 10000;
    public static final String TEST_BASE_URL = "http://localhost:3333";
    public static final String API_URL = "/api/v1";
    public static final String ADMIN_URL = "/admin/v1";
    public static final String RESEARCHERS_URL = "/researchers/v1";
    public static final String CONSENT_TEST_URL = "/consent/asdf";
    public static final String SIGN_OUT_URL = API_URL + "/auth/signOut";
    public static final String SIGN_IN_URL = API_URL + "/auth/signIn";

    public static final String TRACKERS_URL = API_URL + "/trackers";
    public static final String TRACKER_URL = API_URL + "/healthdata/2";
    public static final String RECORD_URL = API_URL + "/healthdata/2/record/";

    public static final String PROFILE_URL = API_URL + "/profile";

    public static final String STUDYCONSENT_URL = ADMIN_URL + "/consents";
    public static final String STUDYCONSENT_ACTIVE_URL = STUDYCONSENT_URL + "/active";

    public static final String CONSENT_URL = API_URL + "/consent";
    public static final String SUSPEND_URL = CONSENT_URL + "/dataSharing/suspend";
    public static final String RESUME_URL = CONSENT_URL + "/dataSharing/resume";

    public static final String SURVEYS_URL = RESEARCHERS_URL + "/surveys";
    public static final String USER_SURVEY_URL = API_URL + "/surveys/%s/%s";
    public static final String GET_SURVEY_URL = RESEARCHERS_URL + "/surveys/%s/%s";
    public static final String GET_VERSIONS_OF_SURVEY_URL = RESEARCHERS_URL + "/surveys/%s/versions";
    public static final String VERSION_SURVEY_URL = GET_SURVEY_URL + "/version";
    public static final String PUBLISH_SURVEY_URL = GET_SURVEY_URL + "/publish";
    public static final String RECENT_SURVEYS_URL = RESEARCHERS_URL + "/surveys/recent";
    public static final String RECENT_PUBLISHED_SURVEYS_URL = RESEARCHERS_URL + "/surveys/published";

    public static final String NEW_SURVEY_RESPONSE = API_URL + "/surveys/%s/%s";
    public static final String SURVEY_RESPONSE_URL = API_URL + "/surveys/response/%s";
    
    public static final String USER_SCHEDULES_URL = API_URL + "/schedules";

    public static final String SCHEDULE_PLANS_URL = RESEARCHERS_URL + "/scheduleplans";
    public static final String SCHEDULE_PLAN_URL = RESEARCHERS_URL + "/scheduleplans/%s";
    
    public static final String USER_URL = ADMIN_URL + "/users";
    public static final String REVOKE_CONSENT_URL = USER_URL + "/consent";

    public static final String APPLICATION_JSON = "application/json";
    public static final String USERNAME = "username";
    public static final String PASSWORD = "password";
    public static final String SESSION_TOKEN = "sessionToken";
    public static final String RECORD_ID = "recordId";

    public static final String MENU_LINK = "#usermenu>.dropdown-toggle";
    public static final String RESET_PASSWORD_LINK = "#resetPasswordLink";
    public static final String RESET_PASSWORD_DIALOG = "#resetPasswordDialog";
    public static final String SIGN_OUT_LINK = "#signOutLink";
    public static final String SIGN_IN_DIALOG = "#signInDialog";
    public static final String SIGN_IN_LINK = "#signInLink";
    public static final String SIGN_IN_ACT = "#signInAct";
    public static final String SIGN_IN_MESSAGE = "#signInMessage";
    public static final String USERNAME_LABEL = "span[ng-bind='session.username']";

    public static final String JOIN_LINK = "#joinLink";
    public static final String JOIN_PAGE = "#joinPage";
    public static final String JOIN_MESSAGE = "#joinMessage";
    public static final String JOIN_ACT = "#joinAct";

    public static final String USERNAME_INPUT = "input[name='username']";
    public static final String EMAIL_INPUT = "input[name='email']";
    public static final String PASSWORD_INPUT = "input[name='password']";
    public static final String PASSWORD_CONFIRM_INPUT = "input[name='passwordConfirm']";

    public static final String SEND_ACTION = "#sendAct";
    public static final String CANCEL_ACTION = "#cancelAct";
    public static final String CLOSE_ACTION = ".close";
    public static final String TOAST_DIALOG = ".humane";

    public static final Class<PhantomJSDriver> PHANTOMJS_DRIVER = org.openqa.selenium.phantomjs.PhantomJSDriver.class;
    public static final Class<FirefoxDriver> FIREFOX_DRIVER = FirefoxDriver.class;
}
