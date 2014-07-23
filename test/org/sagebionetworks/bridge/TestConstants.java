package org.sagebionetworks.bridge;

import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.phantomjs.PhantomJSDriver;
import org.sagebionetworks.bridge.models.Study;

public class TestConstants {

    // These were the original test accounts and their states. On Stormpath in
    // the development
    // application, The state of the consent acceptance has been mirrored for
    // the tests.
    // "test1", "test1@sagebase.org", tou false, not admin
    // "test2", "test2@sagebase.org", tou true, not admin
    // "test3", "test3@sagebase.org", tou true, admin
    // "test4", "test4@sagebase.org", tou false, admin

    public static class UserCredentials {
        public final String USERNAME;
        public final String PASSWORD;
        public final String EMAIL;
        public final String FIRSTNAME;
        public final String LASTNAME;

        public UserCredentials(String username, String password, String email, String firstname, String lastname) {
            this.USERNAME = username;
            this.PASSWORD = password;
            this.EMAIL = email;
            this.FIRSTNAME = firstname;
            this.LASTNAME = lastname;
        }
    }

    public static final UserCredentials TEST1 = new UserCredentials("test1",
            "P4ssword", "test1@sagebase.org", "test1", "test1");
    public static final UserCredentials TEST2 = new UserCredentials("test2",
            "P4ssword", "test2@sagebase.org", "test2", "test2");
    public static final UserCredentials TEST3 = new UserCredentials("test3",
            "P4ssword", "test3@sagebase.org", "test3", "test3");
    public static final UserCredentials TEST4 = new UserCredentials("test4",
            "P4ssword", "test4@sagebase.org", "test4", "test4");
    public static final Study TEST_STUDY = new Study(
            "Neuro-Degenerative Diseases Study", "neurod", null, null);

    public static final int TIMEOUT = 10000;
    public static final String TEST_URL     = "http://localhost:3333";
    public static final String SIGN_OUT_URL = "/api/auth/signOut";
    public static final String SIGN_IN_URL  = "/api/auth/signIn";

    public static final String TRACKER_URL  = "/api/healthdata/2";
    public static final String RECORD_URL   = "/api/healthdata/2/record/";

    public static final String PROFILE_URL  = "/api/users/profile";
    
    public static final String APPLICATION_JSON = "application/json";
    public static final String USERNAME = "username";
    public static final String PASSWORD = "password";
    public static final String SESSION_TOKEN = "sessionToken";
    public static final String PAYLOAD = "payload";
    public static final String RECORD_ID = "recordId";

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
