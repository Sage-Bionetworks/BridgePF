package org.sagebionetworks.bridge.models.schedules;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * This is a "soft" reference to a survey that does not need to include a createdOn timestamp. 
 * It can be used to create JSON for published versions of surveys as well as hard references 
 * to a specific version.
 */
@BridgeTypeName("GuidCreatedOnVersionHolder")
public final class SurveyReference {

    private static final String PUBLISHED_FRAGMENT = "published";
    private static final String REGEXP = "http[s]?\\://.*/surveys/([^/]*)/revisions/([^/]*)";
    private static final Pattern patttern = Pattern.compile(REGEXP);
    
    public static final boolean isSurveyRef(String ref) {
        return (ref != null && ref.matches(REGEXP));
    }
    
    private final String guid;
    private final DateTime createdOn;
    
    public SurveyReference(String ref) {
        checkNotNull(ref);
        Matcher matcher = patttern.matcher(ref);
        matcher.find();
        this.guid = matcher.group(1);
        String createdOnString = (PUBLISHED_FRAGMENT.equals(matcher.group(2))) ? null : matcher.group(2);
        this.createdOn = (createdOnString != null) ? DateTime.parse(createdOnString) : null;    
        checkNotNull(guid);
    }
    
    public String getGuid() {
        return guid;
    }

    public DateTime getCreatedOn() {
        return createdOn;
    }
    
    @JsonIgnore
    public GuidCreatedOnVersionHolder getGuidCreatedOnVersionHolder() {
        if (createdOn == null) {
            return null;
        }
        return new GuidCreatedOnVersionHolderImpl(guid, createdOn.getMillis());
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Objects.hashCode(guid);
        result = prime * result + Objects.hashCode(createdOn);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        SurveyReference other = (SurveyReference) obj;
        return Objects.equals(guid, other.guid) && Objects.equals(createdOn, other.createdOn);
    }

    @Override
    public String toString() {
        return String.format("SurveyReference [guid=%s, createdOn=%s]", guid, createdOn);
    }
    
}
