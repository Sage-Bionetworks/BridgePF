package org.sagebionetworks.bridge.models.schedules;

import org.sagebionetworks.bridge.dynamodb.DynamoTable;
import org.sagebionetworks.bridge.json.ActivityTypeDeserializer;
import org.sagebionetworks.bridge.json.LowercaseEnumJsonSerializer;
import org.sagebionetworks.bridge.json.PeriodJsonDeserializer;
import org.sagebionetworks.bridge.json.PeriodJsonSerializer;
import org.sagebionetworks.bridge.json.ScheduleTypeDeserializer;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.User;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@DynamoDBTable(tableName = "Schedule")
public class Schedule implements DynamoTable {
    
    public enum Type {
        DATE,
        CRON
    }

    public enum ActivityType {
        SURVEY,
        TASK
    }
    
    private String studyUserCompoundKey;
    private String schedulePlanGuid; 
    private String label;
    private ActivityType activityType;
    private String activityRef;
    private Schedule.Type scheduleType;
    private String schedule;
    private Long expires;
    
    public Schedule() {
    }
    
    public Schedule(Schedule schedule) {
        setStudyUserCompoundKey(schedule.getStudyUserCompoundKey());
        setSchedulePlanGuid(schedule.getSchedulePlanGuid());
        setLabel(schedule.getLabel());
        setActivityType(schedule.getActivityType());
        setActivityRef(schedule.getActivityRef());
        setScheduleType(schedule.getScheduleType());
        setSchedule(schedule.getSchedule());
        setExpires(schedule.getExpires());
    }
    
    @JsonIgnore
    @DynamoDBHashKey
    public String getStudyUserCompoundKey() {
        return studyUserCompoundKey;
    }
    public void setStudyUserCompoundKey(String studyUserCompoundKey) {
        this.studyUserCompoundKey = studyUserCompoundKey;
    }
    public void setStudyAndUser(Study study, User user) {
        setStudyUserCompoundKey(study.getKey()+":"+user.getId());
    }
    @JsonIgnore
    public void setSchedulePlanGuid(String schedulePlanGuid) {
        this.schedulePlanGuid = schedulePlanGuid;
    }
    @DynamoDBAttribute
    public String getSchedulePlanGuid() { 
        return this.schedulePlanGuid;
    }
    @DynamoDBAttribute
    public String getLabel() {
        return label;
    }
    public void setLabel(String label) {
        this.label = label;
    }
    @JsonSerialize(using = LowercaseEnumJsonSerializer.class)
    @DynamoDBAttribute
    public ActivityType getActivityType() {
        return activityType;
    }
    @JsonDeserialize(using = ActivityTypeDeserializer.class)
    public void setActivityType(ActivityType activityType) {
        this.activityType = activityType;
    }
    @DynamoDBAttribute
    public String getActivityRef() {
        return activityRef;
    }
    public void setActivityRef(String activityRef) {
        this.activityRef = activityRef;
    }
    @JsonSerialize(using = LowercaseEnumJsonSerializer.class)
    @DynamoDBAttribute
    public Schedule.Type getScheduleType() {
        return scheduleType;
    }
    @JsonDeserialize(using = ScheduleTypeDeserializer.class)
    public void setScheduleType(Schedule.Type scheduleType) {
        this.scheduleType = scheduleType;
    }
    @DynamoDBAttribute
    public String getSchedule() {
        return schedule;
    }
    public void setSchedule(String schedule) {
        this.schedule = schedule;
    }
    @JsonSerialize(using = PeriodJsonSerializer.class)
    @DynamoDBAttribute
    public Long getExpires() {
        return expires;
    }
    @JsonDeserialize(using = PeriodJsonDeserializer.class)
    public void setExpires(Long expires) {
        this.expires = expires;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((activityRef == null) ? 0 : activityRef.hashCode());
        result = prime * result + ((activityType == null) ? 0 : activityType.hashCode());
        result = prime * result + ((expires == null) ? 0 : expires.hashCode());
        result = prime * result + ((label == null) ? 0 : label.hashCode());
        result = prime * result + ((schedule == null) ? 0 : schedule.hashCode());
        result = prime * result + ((schedulePlanGuid == null) ? 0 : schedulePlanGuid.hashCode());
        result = prime * result + ((scheduleType == null) ? 0 : scheduleType.hashCode());
        result = prime * result + ((studyUserCompoundKey == null) ? 0 : studyUserCompoundKey.hashCode());
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
        Schedule other = (Schedule) obj;
        if (activityRef == null) {
            if (other.activityRef != null)
                return false;
        } else if (!activityRef.equals(other.activityRef))
            return false;
        if (activityType != other.activityType)
            return false;
        if (expires == null) {
            if (other.expires != null)
                return false;
        } else if (!expires.equals(other.expires))
            return false;
        if (label == null) {
            if (other.label != null)
                return false;
        } else if (!label.equals(other.label))
            return false;
        if (schedule == null) {
            if (other.schedule != null)
                return false;
        } else if (!schedule.equals(other.schedule))
            return false;
        if (schedulePlanGuid == null) {
            if (other.schedulePlanGuid != null)
                return false;
        } else if (!schedulePlanGuid.equals(other.schedulePlanGuid))
            return false;
        if (scheduleType != other.scheduleType)
            return false;
        if (studyUserCompoundKey == null) {
            if (other.studyUserCompoundKey != null)
                return false;
        } else if (!studyUserCompoundKey.equals(other.studyUserCompoundKey))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "Schedule [studyUserCompoundKey=" + studyUserCompoundKey + ", schedulePlanGuid=" + schedulePlanGuid
                + ", label=" + label + ", activityType=" + activityType + ", activityRef=" + activityRef
                + ", scheduleType=" + scheduleType + ", schedule=" + schedule + ", expires=" + expires + "]";
    }
    
}
