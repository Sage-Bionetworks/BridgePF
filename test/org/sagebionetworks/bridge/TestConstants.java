package org.sagebionetworks.bridge;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;

public class TestConstants {
    public static final String DUMMY_IMAGE_DATA = "VGhpcyBpc24ndCBhIHJlYWwgaW1hZ2Uu";

    public static final String TEST_STUDY_IDENTIFIER = "api";
    public static final StudyIdentifier TEST_STUDY = new StudyIdentifierImpl(TEST_STUDY_IDENTIFIER);
    public static final int TIMEOUT = 10000;
    public static final String TEST_BASE_URL = "http://localhost:3333";
    public static final String API_URL = "/v3";
    public static final String SIGN_OUT_URL = API_URL + "/auth/signOut";
    public static final String SIGN_IN_URL = API_URL + "/auth/signIn";
    public static final String SCHEDULES_API = API_URL + "/schedules";

    public static final String APPLICATION_JSON = "application/json";
    public static final String USERNAME = "username";
    public static final String PASSWORD = "password";
    public static final String SESSION_TOKEN = "sessionToken";

    public static final String ATTACHMENT_BUCKET = BridgeConfigFactory.getConfig().getProperty("attachment.bucket");
    public static final String UPLOAD_BUCKET = BridgeConfigFactory.getConfig().getProperty("upload.bucket");

    public static final String ACTIVITY_1 = "task:task3";
    public static final String ACTIVITY_2 = "http://webservices.sagebridge.org/v3/surveys/AAA/revisions/2015-04-12T14:20:56.123-07:00";
    public static final String ACTIVITY_3 = "http://webservices.sagebridge.org/v3/surveys/AAA/revisions/published";
    public static final DateTime ENROLLMENT = DateTime.parse("2015-04-10T10:40:34.000-07:00");
}
