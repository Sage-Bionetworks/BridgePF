package org.sagebionetworks.bridge.models.schedules;

import org.joda.time.DateTime;

import static com.google.common.base.Preconditions.checkNotNull;

public class TaskBuilder {
    
    private String guid;
    private Activity activity;
    private String activityId;
    private DateTime startsOn;
    private DateTime endsOn;

    public Task build() {
        checkNotNull(guid);
        checkNotNull(activity);
        if (activity.getActivityType() == ActivityType.TASK) {
            activityId = "task:" + activity.getRef();
        } else {
            activityId = "survey:" + activity.getSurvey().getGuid();
        }
        return new Task(guid, activity, activityId, startsOn, endsOn);
    }
    
    public TaskBuilder withGuid(String guid) {
        this.guid = guid;
        return this;
    }
    public TaskBuilder withActivity(Activity activity) {
        this.activity = activity;
        return this;
    }
    public TaskBuilder withStartsOn(DateTime startsOn) {
        this.startsOn = startsOn;
        return this;
    }
    public TaskBuilder withEndsOn(DateTime endsOn) {
        this.endsOn = endsOn;
        return this;
    }
    
}
