package org.sagebionetworks.bridge;

import org.sagebionetworks.bridge.config.BridgeConfigFactory;

public class TestConstants {
    public static final String DUMMY_IMAGE_DATA = "VGhpcyBpc24ndCBhIHJlYWwgaW1hZ2Uu";

    public static final String TEST_STUDY_IDENTIFIER = "api";
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
    public static final String SCHEDULES_API = API_URL + "/schedules";
    
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

    public static final String UPLOAD_BUCKET = BridgeConfigFactory.getConfig().getProperty("upload.bucket");
}
