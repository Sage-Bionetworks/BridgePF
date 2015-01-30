package org.sagebionetworks.bridge;

public class BridgeConstants {

    public static final String SESSION_TOKEN_HEADER = "Bridge-Session";

    public static final String CUSTOM_DATA_HEALTH_CODE_SUFFIX = "_code";

    public static final String CUSTOM_DATA_CONSENT_SIGNATURE_SUFFIX = "_consent_signature";

    public static final String CUSTOM_DATA_VERSION = "version";

    public static final String ADMIN_GROUP = "admin";

    public static final String TEST_USERS_GROUP = "test_users";

    // 24 hrs after last activity
    public static final int BRIDGE_SESSION_EXPIRE_IN_SECONDS = 24 * 60 * 60;

    public static final int BRIDGE_UPDATE_ATTEMPT_EXPIRE_IN_SECONDS = 5 * 60;

    public static final String SCHEDULE_STRATEGY_PACKAGE = "org.sagebionetworks.bridge.models.schedules.";

    public static final String PHONE_ATTRIBUTE = "phone";

}
