package org.sagebionetworks.bridge.models.schedules;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.sagebionetworks.bridge.json.BridgeTypeName;

/**
 * This is a "soft" reference to a survey that does not need to include a createdOn timestamp. 
 * It can be used to create JSON for published versions of surveys as well as hard references 
 * to a specific version.
 */
@BridgeTypeName("GuidCreatedOnVersionHolder")
public class SurveyReference {

    private static final Pattern p = Pattern.compile("/surveys/(.*)/revisions/(.*)");
    private static final String SURVEY_PATH_FRAGMENT = "/surveys/";
    private static final String PUBLISHED_FRAGMENT = "published";
    
    public static final boolean isSurveyRef(String ref) {
        return (ref != null && ref.contains(SURVEY_PATH_FRAGMENT));
    }
    
    private final String guid;
    private final String createdOn;
    
    public SurveyReference(String ref) {
        Matcher m = p.matcher(ref);
        m.find();
        this.guid = m.group(1);
        this.createdOn = (PUBLISHED_FRAGMENT.equals(m.group(2))) ? null : m.group(2);
    }

    public String getGuid() {
        return guid;
    }

    public String getCreatedOn() {
        return createdOn;
    }
    
}
