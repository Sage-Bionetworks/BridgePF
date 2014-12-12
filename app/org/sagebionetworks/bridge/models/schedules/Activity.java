package org.sagebionetworks.bridge.models.schedules;


import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Activity {

    private final String label;
    private final ActivityType activityType;
    private final String ref;
    private final GuidCreatedOnVersionHolder survey;
    
    @JsonCreator
    public Activity(@JsonProperty("label") String label, @JsonProperty("activityType") ActivityType activityType,
            @JsonProperty("ref") String ref, @JsonProperty("survey") GuidCreatedOnVersionHolder survey) {
        this.label = label;
        this.activityType = activityType;
        this.ref = ref;
        this.survey = survey;
    }
    
    public Activity(String label, ActivityType activityType, String ref) {
        this.label = label;
        this.activityType = activityType;
        this.ref = ref;
        this.survey = parseSurvey(ref);
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

    public GuidCreatedOnVersionHolder getSurvey() {
        return survey;
    }
    
    private GuidCreatedOnVersionHolder parseSurvey(String ref) {
        if (ref.contains("/surveys/")) {
            String[] parts = ref.split("/surveys/")[1].split("/");
            String guid = parts[0];
            long createdOn = DateUtils.convertToMillisFromEpoch(parts[1]);
            return new GuidCreatedOnVersionHolderImpl(guid, createdOn);
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
        result = prime * result + ((survey == null) ? 0 : survey.hashCode());
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
        if (survey == null) {
            if (other.survey != null)
                return false;
        } else if (!survey.equals(other.survey))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "Activity [label=" + label + ", activityType=" + activityType + ", ref=" + ref + ", survey=" + survey
                + "]";
    }

}
