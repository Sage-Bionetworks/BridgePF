package org.sagebionetworks.bridge.models.schedules;


import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Activity {

    private final String label;
    private final ActivityType activityType;
    private final String ref;
    
    @JsonCreator
    public Activity(@JsonProperty("label") String label, @JsonProperty("ref") String ref) {
        this.label = label;
        this.ref = ref;
        if (ref == null) {
            this.activityType = null;
        } else {
            this.activityType = (ref.contains("/surveys/")) ? ActivityType.SURVEY : ActivityType.TASK;    
        }
    }
    
    public String getLabel() {
        return label;
    }

    public ActivityType getActivityType() {
        return activityType;
    }

    public String getRef() {
        return ref;
    }

    public SurveyReference getSurvey() {
        return ref.contains("/surveys/") ? new SurveyReference(ref) : null;
    }
    
    @JsonIgnore
    public GuidCreatedOnVersionHolder getGuidCreatedOnVersionHolder() {
        SurveyReference ref = getSurvey();
        if (ref != null && ref.getCreatedOn() != null) {
            return new GuidCreatedOnVersionHolderImpl(ref.getGuid(), DateUtils.convertToMillisFromEpoch(ref.getCreatedOn()));
        }
        return null;
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((activityType == null) ? 0 : activityType.hashCode());
        result = prime * result + ((label == null) ? 0 : label.hashCode());
        result = prime * result + ((ref == null) ? 0 : ref.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Activity other = (Activity) obj;
        if (activityType != other.activityType)
            return false;
        if (label == null) {
            if (other.label != null)
                return false;
        } else if (!label.equals(other.label))
            return false;
        if (ref == null) {
            if (other.ref != null)
                return false;
        } else if (!ref.equals(other.ref))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "Activity [label=" + label + ", activityType=" + activityType + ", ref=" + ref + "]";
    }

}
