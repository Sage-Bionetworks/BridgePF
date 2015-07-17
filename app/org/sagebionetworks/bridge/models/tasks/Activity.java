package org.sagebionetworks.bridge.models.tasks;

import static org.sagebionetworks.bridge.models.tasks.ActivityType.TASK;
import static org.sagebionetworks.bridge.models.tasks.ActivityType.SURVEY;

import java.util.Objects;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.validators.ActivityValidator;
import org.sagebionetworks.bridge.validators.Validate;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * An activity we wish a study participant to do. The two main types of activities are 
 * tasks in the application (such as taking a tapping test), and surveys to be filled 
 * out and returned to the sever.
 * 
 * In schedules, activities are set with surveys. In tasks, these are "resolved" to point 
 * to a specific survey, and a survey response object is also added to the activity. This includes 
 * an endpoint where answers to the survey can be submitted.
 */
public final class Activity implements BridgeEntity {
    
    private static final ActivityValidator VALIDATOR = new ActivityValidator();
    
    private String label;
    private String labelDetail;
    private TaskReference task;
    private SurveyReference survey;
    private SurveyResponseReference response;
    private ActivityType activityType;

    @JsonCreator
    private Activity(@JsonProperty("label") String label, @JsonProperty("labelDetail") String labelDetail, 
        @JsonProperty("task") TaskReference task, @JsonProperty("survey") SurveyReference survey, 
        @JsonProperty("surveyResponse") SurveyResponseReference response) {
        this.label = label;
        this.labelDetail = labelDetail;
        this.survey = survey;
        this.task = task;
        this.survey = survey;
        this.response = response;
        this.activityType = (task != null) ? TASK : SURVEY;
    }
    
    public String getLabel() { 
        return label;
    }
    public String getLabelDetail() {
        return labelDetail;
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
    public SurveyResponseReference getSurveyResponse() {
        return response;
    }
    public String getRef() { 
        if (task != null) {
            return task.getIdentifier();
        } else if (survey != null) {
            return survey.getHref();
        }
        return null;
    }

    public static class Builder {
        private String label;
        private String labelDetail;
        private TaskReference task;
        private SurveyReference survey;
        private SurveyResponseReference response;
        
        public Builder withLabel(String label) {
            this.label = label;
            return this;
        }
        public Builder withLabelDetail(String labelDetail) {
            this.labelDetail = labelDetail;
            return this;
        }
        public Builder withTask(String taskId) {
            this.task = new TaskReference(taskId);
            return this;
        }
        public Builder withPublishedSurvey(String identifier, String guid) {
            this.survey = new SurveyReference(identifier, guid, (String)null);
            return this;
        }
        public Builder withSurvey(String identifier, String guid, String createdOn) {
            this.survey = new SurveyReference(identifier, guid, createdOn);
            return this;
        }
        public Builder withSurvey(String identifier, String guid, DateTime createdOn) {
            this.survey = new SurveyReference(identifier, guid, createdOn);
            return this;
        }
        public Builder withSurveyResponse(String guid) {
            this.response = new SurveyResponseReference(guid);
            return this;
        }
        public Activity build() {
            Activity activity = new Activity(label, labelDetail, task, survey, response);
            Validate.entityThrowingException(VALIDATOR, activity);
            return activity;
        }
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Objects.hashCode(activityType);
        result = prime * result + Objects.hashCode(label);
        result = prime * result + Objects.hashCode(labelDetail);
        result = prime * result + Objects.hashCode(response);
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
            Objects.equals(response, other.response) && Objects.equals(survey, other.survey) &&
            Objects.equals(task, other.task));
                        
    }

    @Override
    public String toString() {
        return String.format("Activity [label=%s, labelDetail=%s, ref=%s, task=%s, survey=%s, response=%s, activityType=%s]",
            label, labelDetail, getRef(), task, survey, response, activityType);
    }
}
