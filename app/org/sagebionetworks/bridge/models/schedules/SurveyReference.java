package org.sagebionetworks.bridge.models.schedules;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;

import com.fasterxml.jackson.annotation.JsonIgnore;

import com.google.common.base.Preconditions;

/**
 * This is a "soft" reference to a survey that does not need to include a createdOn timestamp. 
 * It can be used to create JSON for published versions of surveys as well as hard references 
 * to a specific version.
 */
@BridgeTypeName("GuidCreatedOnVersionHolder")
public class SurveyReference {

    private static final String PUBLISHED_FRAGMENT = "published";
    private static final String REGEXP = "http[s]?\\://.*/surveys/([^/]*)/revisions/([^/]*)";
    private static final Pattern patttern = Pattern.compile(REGEXP);
    
    public static final boolean isSurveyRef(String ref) {
        return (ref != null && ref.matches(REGEXP));
    }
    
    private final String guid;
    private final String createdOn;
    
    public SurveyReference(String ref) {
        Matcher matcher = patttern.matcher(ref);
        matcher.find();
        this.guid = matcher.group(1);
        this.createdOn = (PUBLISHED_FRAGMENT.equals(matcher.group(2))) ? null : matcher.group(2);
        Preconditions.checkNotNull(guid);
    }
    
    public String getGuid() {
        return guid;
    }

    public String getCreatedOn() {
        return createdOn;
    }
    
    @JsonIgnore
    public DateTime getCreatedOnTimestamp() {
        return (createdOn == null) ? null : DateTime.parse(createdOn);
    }
    
    @JsonIgnore
    public GuidCreatedOnVersionHolder getGuidCreatedOnVersionHolder() {
        if (createdOn == null) {
            return null;
        }
        return new GuidCreatedOnVersionHolderImpl(guid, getCreatedOnTimestamp().getMillis());
    }
}
