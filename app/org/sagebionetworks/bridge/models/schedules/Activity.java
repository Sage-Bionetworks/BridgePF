package org.sagebionetworks.bridge.models.schedules;

import static org.sagebionetworks.bridge.models.schedules.ActivityType.COMPOUND;
import static org.sagebionetworks.bridge.models.schedules.ActivityType.SURVEY;
import static org.sagebionetworks.bridge.models.schedules.ActivityType.TASK;

import java.util.Objects;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
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
    private final String label;
    private final String labelDetail;
    private final String guid;
    private final CompoundActivity compoundActivity;
    private final TaskReference task;
    private final SurveyReference survey;
    private final ActivityType activityType;

    private Activity(String label, String labelDetail, String guid, CompoundActivity compoundActivity,
            TaskReference task, SurveyReference survey, ActivityType activityType) {
        this.label = label;
        this.labelDetail = labelDetail;
        this.guid = guid;
        this.compoundActivity = compoundActivity;
        this.survey = survey;
        this.task = task;
        this.activityType = activityType;
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

    /**
     * Returns the compound activity (if there is one), which contains a task identifier and a list of schemas
     * references and survey references.
     */
    public CompoundActivity getCompoundActivity() {
        return compoundActivity;
    }

    public TaskReference getTask() {
        return task;
    }
    public SurveyReference getSurvey() {
        return survey;
    }
    public boolean isPersistentlyRescheduledBy(Schedule schedule) {
        return (schedule.getScheduleType() == ScheduleType.PERSISTENT || 
               (schedule.schedulesImmediatelyAfterEvent() && getActivityFinishedEventId(schedule)));
    }
    private boolean getActivityFinishedEventId(Schedule schedule) {
        String activityFinishedEventId = "activity:"+getGuid()+":finished";
        return schedule.getEventId().contains(getSelfFinishedEventId()) ||
               schedule.getEventId().contains(activityFinishedEventId);
    }

    // Package-scoped to facilitate unit tests
    String getSelfFinishedEventId() {
        switch (activityType) {
            case COMPOUND:
                return "compound:" + getCompoundActivity().getTaskIdentifier() + ":finished";
            case SURVEY:
                return "survey:" + getSurvey().getGuid() + ":finished";
            case TASK:
                return "task:" + getTask().getIdentifier() + ":finished";
            default:
                throw new BridgeServiceException("Impossible code path in Activity.getSelfFinishedEventId()");
        }
    }

    public static class Builder {
        private String label;
        private String labelDetail;
        private String guid;
        private CompoundActivity compoundActivity;
        private TaskReference task;
        private SurveyReference survey;
        
        public Builder withActivity(Activity activity) {
            this.label = activity.label;
            this.labelDetail = activity.labelDetail;
            this.guid = activity.guid;
            this.compoundActivity = activity.compoundActivity;
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

        /** @see #getCompoundActivity */
        public Builder withCompoundActivity(CompoundActivity compoundActivity) {
            this.compoundActivity = compoundActivity;
            return this;
        }

        @JsonSetter
        public Builder withTask(TaskReference reference) {
            this.task = reference;
            return this;
        }
        public Builder withTask(String taskId) {
            this.task = new TaskReference(taskId, null);
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
            ActivityType activityType = null;
            if (compoundActivity != null) {
                activityType = COMPOUND;
            }
            if (survey != null) {
                activityType = SURVEY;
            }
            if (task != null) {
                activityType = TASK;
            }

            return new Activity(label, labelDetail, guid, compoundActivity, task, survey, activityType);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(label, labelDetail, guid, compoundActivity, task, survey, activityType);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        Activity other = (Activity) obj;
        return Objects.equals(label, other.label) &&
                Objects.equals(labelDetail, other.labelDetail) &&
                Objects.equals(guid, other.guid) &&
                Objects.equals(compoundActivity, other.compoundActivity) &&
                Objects.equals(task, other.task) &&
                Objects.equals(survey, other.survey) &&
                Objects.equals(activityType, other.activityType);
    }

    @Override
    public String toString() {
        return String.format("Activity [label=%s, labelDetail=%s, guid=%s, compoundActivity=%s, task=%s, survey=%s, " +
                "activityType=%s]", label, labelDetail, guid, compoundActivity, task, survey, activityType);
    }
}
