package org.sagebionetworks.bridge;

public class BridgeConstants {

    public static final String SESSION_TOKEN_HEADER = "Bridge-Session";

    public static final String CUSTOM_DATA_CONSENT_SUFFIX = "_consent";

    public static final String CUSTOM_DATA_HEALTH_CODE_SUFFIX = "_code";

    public static final String CUSTOM_DATA_VERSION = "version";

    public static final String ADMIN_GROUP = "admin";

    public static final int BRIDGE_SESSION_EXPIRE_IN_SECONDS = 20 * 60;

    public static final int BRIDGE_UPDATE_ATTEMPT_EXPIRE_IN_SECONDS = 5 * 60;

    public static final String CONSENT_REQUIRED_MESSAGE = "Before you may continue, we require you to consent to participate in this study.";
}
