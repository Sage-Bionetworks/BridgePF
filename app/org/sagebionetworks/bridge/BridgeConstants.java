package org.sagebionetworks.bridge;

import java.util.Set;

import org.joda.time.DateTimeZone;
import org.jsoup.safety.Whitelist;

import com.google.common.collect.ImmutableSet;

public class BridgeConstants {

    /** A common string constraint we place on model identifiers. */
    public static final String BRIDGE_IDENTIFIER_PATTERN = "^[a-z0-9-]+$";
    
    /** A common string constraint Synapse places on model identifiers. */
    public static final String SYNAPSE_IDENTIFIER_PATTERN = "^[a-zA-Z0-9_-]+$";
    
    /** The pattern of a valid JavaScript variable/object property name. */
    public  static final String JS_IDENTIFIER_PATTERN = "^[a-zA-Z0-9_][a-zA-Z0-9_-]*$";
    
    public static final String BRIDGE_API_STATUS_HEADER = "Bridge-Api-Status";

    public static final String BRIDGE_DEPRECATED_STATUS = "you're calling a deprecated endpoint";

    public static final String WARN_NO_USER_AGENT =  "we can't parse your User-Agent header, cannot filter";

    public static final String WARN_NO_ACCEPT_LANGUAGE = "you haven't included an Accept-Language header, cannot filter";

    public static final String SESSION_TOKEN_HEADER = "Bridge-Session";

    public static final String BRIDGE_STUDY_HEADER = "Bridge-Study";

    public static final String BRIDGE_HOST_HEADER = "Bridge-Host";

    /** Used by Heroku to pass in the request ID */
    public static final String X_REQUEST_ID_HEADER = "X-Request-Id";

    public static final String X_FORWARDED_FOR_HEADER = "X-Forwarded-For";

    public static final String CUSTOM_DATA_HEALTH_CODE_SUFFIX = "_code";

    public static final String CUSTOM_DATA_CONSENT_SIGNATURE_SUFFIX = "_consent_signature";

    public static final String CUSTOM_DATA_VERSION = "version";
    
    public static final String STUDY_PROPERTY = "study";

    public static final DateTimeZone LOCAL_TIME_ZONE = DateTimeZone.forID("America/Los_Angeles");

    // 12 hrs after last activity
    public static final int BRIDGE_SESSION_EXPIRE_IN_SECONDS = 12 * 60 * 60;

    // 5 minutes
    public static final int BRIDGE_UPDATE_ATTEMPT_EXPIRE_IN_SECONDS = 5 * 60;
    
    // 5 hrs
    public static final int BRIDGE_VIEW_EXPIRE_IN_SECONDS = 5 * 60 * 60;
    
    // 1 minute
    public static final int BRIDGE_STUDY_EMAIL_STATUS_IN_SECONDS = 60;

    public static final String SCHEDULE_STRATEGY_PACKAGE = "org.sagebionetworks.bridge.models.schedules.";

    public static final String ASSETS_HOST = "assets.sagebridge.org";
    
    public static final String JSON_MIME_TYPE = "application/json; charset=UTF-8";

    /** Per-request metrics expires in the cache after 120 seconds. */
    public static final int METRICS_EXPIRE_SECONDS = 2 * 60;
    
    public static final int API_MINIMUM_PAGE_SIZE = 5;
    
    public static final int API_DEFAULT_PAGE_SIZE = 50;
    
    public static final int API_MAXIMUM_PAGE_SIZE = 100;
    
    public static final String STORMPATH_ACCOUNT_BASE_HREF = "https://enterprise.stormpath.io/v1/accounts/";
    
    public static final String STORMPATH_NAME_PLACEHOLDER_STRING = "<EMPTY>";
    
    public static final String TEST_USER_GROUP = "test_user";
    
    public static final Set<Roles> NO_CALLER_ROLES = ImmutableSet.of();
    
    /**
     * This whitelist adds a few additional tags and attributes that are used by the CKEDITOR options we have 
     * displayed in the UI.
     */
    public static final Whitelist CKEDITOR_WHITELIST = Whitelist.relaxed()
            .addTags("hr", "s", "caption")
            .addAttributes(":all", "style", "scope")
            .addAttributes("a", "target")
            .addAttributes("table", "align", "border", "cellpadding", "cellspacing", "summary");
    
}
