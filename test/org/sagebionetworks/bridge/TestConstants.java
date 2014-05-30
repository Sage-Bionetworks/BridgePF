package org.sagebionetworks.bridge;

import org.openqa.selenium.phantomjs.PhantomJSDriver;

public class TestConstants {

    public static final int TIMEOUT = 10000;
	public static final String TEST_URL = "http://localhost:3333";
    public static final String SIGN_OUT_URL = "/api/auth/signOut";
    public static final String SIGN_IN_URL = "/api/auth/signIn";
    
    public static final String TRACKER_URL = "/api/healthdata/2";
    public static final String RECORD_URL = "/api/healthdata/2/record/";
    
    public static final String APPLICATION_JSON = "application/json";
    public static final String PASSWORD = "password";
    public static final String USERNAME = "username";
    public static final String SESSION_TOKEN = "sessionToken";
    public static final String TEST_USER = "test2";
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
    public static final String PASSWORD_INPUT = "input[ng-model='credentials.password']";
    public static final String USERNAME_INPUT = "input[ng-model='credentials.username']";
    public static final String EMAIL_INPUT = "input[ng-model='credentials.email']";

    public static final String SEND_ACTION = "#sendAct";
    public static final String CANCEL_ACTION = "#cancelAct";

    public static final Class<PhantomJSDriver> PHANTOMJS = org.openqa.selenium.phantomjs.PhantomJSDriver.class;
}
