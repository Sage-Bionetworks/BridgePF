package org.sagebionetworks.bridge.models.schedules;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Objects;



import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Activity {

    private final String label;
    private final ActivityType activityType;
    private final String ref;
    
    @JsonCreator
    public Activity(@JsonProperty("label") String label, @JsonProperty("ref") String ref) {
        checkArgument(isNotBlank(label));
        checkNotNull(ref);
        
        this.label = label;
        this.ref = ref;
        this.activityType = SurveyReference.isSurveyRef(ref) ? ActivityType.SURVEY : ActivityType.TASK;
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
        return SurveyReference.isSurveyRef(ref) ? new SurveyReference(ref) : null;
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * Objects.hashCode(label);
        result = prime * Objects.hashCode(ref);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        Activity other = (Activity) obj;
        return (Objects.equals(label, other.label) && Objects.equals(ref, other.ref));
    }

    @Override
    public String toString() {
        return String.format("Activity [label=%s, activityType=%s, ref=%s]", 
            label, activityType.name().toLowerCase(), ref);
    }

}
