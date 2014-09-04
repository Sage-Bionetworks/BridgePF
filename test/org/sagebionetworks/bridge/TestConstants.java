package org.sagebionetworks.bridge;

import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.phantomjs.PhantomJSDriver;
import org.sagebionetworks.bridge.models.SignIn;
import org.sagebionetworks.bridge.models.SignUp;
import org.sagebionetworks.bridge.models.Study;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

public class TestConstants {
    
    public static class TestUser {
        private final String username;
        private final String email;
        private final String password;
        
        public TestUser(String username, String email, String password) {
            this.username = username;
            this.email = email;
            this.password = password;
        }
        public SignUp getSignUp() {
            return new SignUp(username, email, password);
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

    public static final Study STUDY = new Study("Neuro-Degenerative Diseases Study", "neurod", 17, null, null, null, null);
    
    public static final Resource secondStudyConsent = new FileSystemResource("test/conf/secondstudy-consent.html");
    public static final Study SECOND_STUDY = new Study("Second Study", "secondstudy", 17,
            "https://api.stormpath.com/v1/directories/5RfWcEwOK0l7goGe4ZX9cz", null, null, secondStudyConsent);
    
    public static final int TIMEOUT = 10000;
    public static final String TEST_BASE_URL = "http://localhost:3333";
    public static final String API_URL = "/api/v1";
    public static final String ADMIN_URL = "/admin/v1";
    public static final String CONSENT_TEST_URL = "/consent/asdf";
    public static final String SIGN_OUT_URL = API_URL + "/auth/signOut";
    public static final String SIGN_IN_URL = API_URL + "/auth/signIn";

    public static final String TRACKER_URL = API_URL + "/healthdata/2";
    public static final String RECORD_URL = API_URL + "/healthdata/2/record/";

    public static final String PROFILE_URL = API_URL + "/users/profile";
    
    public static final String STUDYCONSENT_URL = ADMIN_URL + "/consents";
    public static final String STUDYCONSENT_ACTIVE_URL = STUDYCONSENT_URL + "/active";
    
    public static final String CONSENT_URL = API_URL + "/users/consent";
    public static final String SUSPEND_URL = CONSENT_URL + "/dataSharing/suspend";
    public static final String RESUME_URL = CONSENT_URL + "/dataSharing/resume";
    
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
