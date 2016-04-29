package org.sagebionetworks.bridge.models.schedules;

import static org.sagebionetworks.bridge.models.schedules.ActivityType.SURVEY;
import static org.sagebionetworks.bridge.models.schedules.ActivityType.TASK;

import java.util.Objects;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * An activity we wish a study participant to do. The two main types of activities are 
 * tasks in the application (such as taking a tapping test), and surveys to be filled 
 * out and returned to the sever.
 * 
 * In schedules, activities are set with surveys. In tasks, these are "resolved" to point 
 * to a specific survey, and a survey response object is also added to the activity. This includes 
 * an endpoint where answers to the survey can be submitted.
 */
@JsonDeserialize(builder=Activity.Builder.class)
public final class Activity implements BridgeEntity {
    
    private String label;
    private String labelDetail;
    private String guid;
    private TaskReference task;
    private SurveyReference survey;
    private ActivityType activityType;

    private Activity(String label, String labelDetail, String guid, 
        TaskReference task, SurveyReference survey) {
        this.label = label;
        this.labelDetail = labelDetail;
        this.guid = guid;
        this.survey = survey;
        this.task = task;
        this.activityType = (task != null) ? TASK : SURVEY;
    }
    
    public String getLabel() { 
        return label;
    }
    public String getLabelDetail() {
        return labelDetail;
    }
    public String getGuid() {
        return guid;
    }
    public ActivityType getActivityType() {
        return activityType;
    }
    public TaskReference getTask() {
        return task;
    }
    public SurveyReference getSurvey() {
        return survey;
    }
    public boolean isPersistentlyRescheduledBy(Schedule schedule) {
        return schedule.schedulesImmediatelyAfterEvent() && getActivityFinishedEventId(schedule);
    }
    private boolean getActivityFinishedEventId(Schedule schedule) {
        String activityFinishedEventId = "activity:"+getGuid()+":finished";
        return schedule.getEventId().contains(getSelfFinishedEventId()) ||
               schedule.getEventId().contains(activityFinishedEventId);
    }
    private String getSelfFinishedEventId() {
        return (getActivityType() == ActivityType.SURVEY) ?
            ("survey:"+getSurvey().getGuid()+":finished") :
            ("task:"+getTask().getIdentifier()+":finished");
    }
    public static class Builder {
        private String label;
        private String labelDetail;
        private String guid;
        private TaskReference task;
        private SurveyReference survey;
        
        public Builder withActivity(Activity activity) {
            this.label = activity.label;
            this.labelDetail = activity.labelDetail;
            this.guid = activity.guid;
            this.task = activity.task;
            this.survey = activity.survey;
            return this;
        }
        public Builder withLabel(String label) {
            this.label = label;
            return this;
        }
        public Builder withLabelDetail(String labelDetail) {
            this.labelDetail = labelDetail;
            return this;
        }
        public Builder withGuid(String guid) {
            this.guid = guid;
            return this;
        }
        @JsonSetter
        public Builder withTask(TaskReference reference) {
            this.task = reference;
            return this;
        }
        public Builder withTask(String taskId) {
            this.task = new TaskReference(taskId);
            return this;
        }
        public Builder withPublishedSurvey(String identifier, String guid) {
            this.survey = new SurveyReference(identifier, guid, null);
            return this;
        }
        @JsonSetter
        public Builder withSurvey(SurveyReference reference) {
            this.survey = reference;
            return this;
        }
        public Builder withSurvey(String identifier, String guid, DateTime createdOn) {
            this.survey = new SurveyReference(identifier, guid, createdOn);
            return this;
        }
        public Activity build() {
            if (guid == null) {
                guid = BridgeUtils.generateGuid();
            }
            return new Activity(label, labelDetail, guid, task, survey);
        }
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Objects.hashCode(activityType);
        result = prime * result + Objects.hashCode(label);
        result = prime * result + Objects.hashCode(labelDetail);
        result = prime * result + Objects.hashCode(guid);
        result = prime * result + Objects.hashCode(survey);
        result = prime * result + Objects.hashCode(task);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        Activity other = (Activity) obj;
        return (Objects.equals(activityType, other.activityType) && 
            Objects.equals(label, other.label) && Objects.equals(labelDetail, other.labelDetail) &&
            Objects.equals(guid,  other.guid) && Objects.equals(survey, other.survey) && Objects.equals(task, other.task));
    }

    @Override
    public String toString() {
        return String.format("Activity [label=%s, labelDetail=%s, guid=%s, task=%s, survey=%s, activityType=%s]",
            label, labelDetail, guid, task, survey, activityType);
    }
}
