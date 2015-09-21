package org.sagebionetworks.bridge.models.schedules;

import java.util.Comparator;
import java.util.Objects;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.sagebionetworks.bridge.dynamodb.DynamoTask;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class TaskWithZone implements Task {

    public static final Comparator<TaskWithZone> TASK_COMPARATOR = new Comparator<TaskWithZone>() {
        @Override 
        public int compare(TaskWithZone task1, TaskWithZone task2) {
            int result = task1.getScheduledOn().compareTo(task2.getScheduledOn());
            if (result == 0) {
                result = task1.getActivity().getLabel().compareTo(task2.getActivity().getLabel());
            }
            return result;
        }
    };
    
    private final DynamoTask task;
    private final DateTimeZone zone;

    public TaskWithZone(DynamoTask task, DateTimeZone zone) {
        this.task = task;
        this.zone = zone;
    }

    @Override
    public TaskStatus getStatus() {
        if (task.getFinishedOn() != null && task.getStartedOn() == null) {
            return TaskStatus.DELETED;
        } else if (task.getFinishedOn() != null && task.getStartedOn() != null) {
            return TaskStatus.FINISHED;
        } else if (task.getStartedOn() != null) {
            return TaskStatus.STARTED;
        }
        DateTime now = DateTime.now(zone);
        if (task.getLocalExpiresOn() != null && now.isAfter(getExpiresOn())) {
            return TaskStatus.EXPIRED;
        } else if (task.getLocalScheduledOn() != null && now.isBefore(getScheduledOn())) {
            return TaskStatus.SCHEDULED;
        }
        return TaskStatus.AVAILABLE;
    }

    @Override
    public String getGuid() {
        return task.getGuid();
    }

    @Override
    public void setGuid(String guid) {
        task.setGuid(guid);
    }

    @Override
    public Activity getActivity() {
        return task.getActivity();
    }

    @Override
    public void setActivity(Activity activity) {
        task.setActivity(activity);
    }

    @Override
    public DateTime getScheduledOn() {
        return task.getLocalScheduledOn().toDateTime(zone);
    }

    @Override
    public DateTime getExpiresOn() {
        return task.getLocalExpiresOn().toDateTime(zone);
    }

    @Override
    public Long getStartedOn() {
        return task.getStartedOn();
    }

    @Override
    public void setStartedOn(Long startedOn) {
        task.setStartedOn(startedOn);
    }

    @Override
    public Long getFinishedOn() {
        return task.getFinishedOn();
    }

    @Override
    public void setFinishedOn(Long finishedOn) {
        task.setFinishedOn(finishedOn);
    }

    @Override
    @JsonIgnore
    public Long getHidesOn() {
        return task.getHidesOn();
    }

    @Override
    public void setHidesOn(Long hidesOn) {
        task.setHidesOn(hidesOn);
    }

    @Override
    @JsonIgnore
    public String getRunKey() {
        return task.getRunKey();
    }

    @Override
    public void setRunKey(String runKey) {
        task.setRunKey(runKey);
    }

    @Override
    public boolean getPersistent() {
        return task.getPersistent();
    }

    @Override
    public void setPersistent(boolean persistent) {
        task.setPersistent(persistent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(task, zone);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        TaskWithZone other = (TaskWithZone) obj;
        return Objects.equals(task, other.task) && Objects.equals(zone, other.zone);
    }

    @Override
    public String toString() {
        return String.format("TaskWithZone [healthCode=%s, guid=%s, scheduledOn=%s, expiresOn=%s, startedOn=%s, finishedOn=%s, persistent=%s, activity=%s, timeZone=%s]",
            task.getHealthCode(), task.getGuid(), getScheduledOn(), getExpiresOn(), task.getStartedOn(), task.getFinishedOn(), task.getPersistent(), task.getActivity(), zone);
    }
}
