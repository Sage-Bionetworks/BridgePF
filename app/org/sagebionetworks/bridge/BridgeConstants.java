package org.sagebionetworks.bridge;

import org.joda.time.DateTimeZone;

public class BridgeConstants {

    public static final String BRIDGE_DEFAULT_CONSENT_DOCUMENT = "<html><head><title>Consent Agreement</title></head><body><p>This is a placeholder for your consent document.</p></body></html>";
    
    public static final String BRIDGE_API_STATUS_HEADER = "Bridge-Api-Status";

    public static final String BRIDGE_DEPRECATED_STATUS = "deprecated";

    public static final String SESSION_TOKEN_HEADER = "Bridge-Session";

    public static final String BRIDGE_STUDY_HEADER = "Bridge-Study";

    public static final String BRIDGE_HOST_HEADER = "Bridge-Host";

    /** Used by Heroku to pass in the request ID */
    public static final String X_REQUEST_ID_HEADER = "X-Request-Id";

    public static final String X_FORWARDED_FOR_HEADER = "X-Forwarded-For";

    public static final String CUSTOM_DATA_HEALTH_CODE_SUFFIX = "_code";

    public static final String CUSTOM_DATA_CONSENT_SIGNATURE_SUFFIX = "_consent_signature";

    public static final String CUSTOM_DATA_VERSION = "version";

    public static final String ADMIN_GROUP = "admin";

    public static final String TEST_USERS_GROUP = "test_users";
    
    public static final String STUDY_PROPERTY = "study";

    public static final DateTimeZone LOCAL_TIME_ZONE = DateTimeZone.forID("America/Los_Angeles");

    // 24 hrs after last activity
    public static final int BRIDGE_SESSION_EXPIRE_IN_SECONDS = 24 * 60 * 60;

    public static final int BRIDGE_UPDATE_ATTEMPT_EXPIRE_IN_SECONDS = 5 * 60;
    
    public static final int BRIDGE_VIEW_EXPIRE_IN_SECONDS = 5 * 60 * 60;

    public static final String SCHEDULE_STRATEGY_PACKAGE = "org.sagebionetworks.bridge.models.schedules.";

    public static final String ASSETS_HOST = "assets.sagebridge.org";
    
    public static final String JSON_MIME_TYPE = "application/json; charset=UTF-8";

    /** Per-request metrics expires in the cache after 120 seconds. */
    public static final int METRICS_EXPIRE_SECONDS = 2 * 60;
}
