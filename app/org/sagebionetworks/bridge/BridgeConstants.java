package org.sagebionetworks.bridge;

import java.util.Set;

import org.joda.time.DateTimeZone;
import org.jsoup.safety.Whitelist;

import com.google.common.collect.ImmutableSet;

import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;

public class BridgeConstants {
    // Study ID for the test study, used in local tests and most integ tests.
    public static final String API_STUDY_ID_STRING = "api";
    public static final StudyIdentifier API_STUDY_ID = new StudyIdentifierImpl(API_STUDY_ID_STRING);

    /** A common string constraint we place on model identifiers. */
    public static final String BRIDGE_IDENTIFIER_PATTERN = "^[a-z0-9-]+$";

    // Study ID used for the Shared Module Library
    public static final String SHARED_STUDY_ID_STRING = "shared";
    public static final StudyIdentifier SHARED_STUDY_ID = new StudyIdentifierImpl(SHARED_STUDY_ID_STRING);

    /** A common string constraint Synapse places on model identifiers. */
    public static final String SYNAPSE_IDENTIFIER_PATTERN = "^[a-zA-Z0-9_-]+$";
    
    /** The pattern of a valid JavaScript variable/object property name. */
    public  static final String JS_IDENTIFIER_PATTERN = "^[a-zA-Z0-9_][a-zA-Z0-9_-]*$";
    
    public static final String BRIDGE_API_STATUS_HEADER = "Bridge-Api-Status";

    public static final String BRIDGE_DEPRECATED_STATUS = "you're calling a deprecated endpoint";

    public static final String WARN_NO_USER_AGENT =  "we can't parse your User-Agent header, cannot filter by application version";

    public static final String WARN_NO_ACCEPT_LANGUAGE = "you haven't included an Accept-Language header, cannot filter by language";

    public static final String SESSION_TOKEN_HEADER = "Bridge-Session";

    public static final String BRIDGE_STUDY_HEADER = "Bridge-Study";

    public static final String BRIDGE_HOST_HEADER = "Bridge-Host";

    /** Used by Heroku to pass in the request ID */
    public static final String X_REQUEST_ID_HEADER = "X-Request-Id";

    public static final String X_FORWARDED_FOR_HEADER = "X-Forwarded-For";

    public static final String CUSTOM_DATA_HEALTH_CODE_SUFFIX = "_code";

    public static final String CUSTOM_DATA_CONSENT_SIGNATURE_SUFFIX = "_consent_signature";

    public static final String CUSTOM_DATA_VERSION = "version";
    
    /** Limit the total length of JSON string that is submitted as client data for a scheduled activity. */
    public static final int CLIENT_DATA_MAX_BYTES = 2048;

    /** Used to cap the number of dupe records we fetch from DDB and the number of log messages we write. */
    public static final int DUPE_RECORDS_MAX_COUNT = 10;

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
