package org.sagebionetworks.bridge.dynamodb;

import org.sagebionetworks.bridge.json.ActivityTypeDeserializer;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.json.LowercaseEnumJsonSerializer;
import org.sagebionetworks.bridge.json.PeriodJsonDeserializer;
import org.sagebionetworks.bridge.json.PeriodJsonSerializer;
import org.sagebionetworks.bridge.json.ScheduleTypeDeserializer;
import org.sagebionetworks.bridge.models.Study;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.schedules.ActivityType;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.ScheduleType;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMarshalling;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

@DynamoDBTable(tableName = "Schedule")
public class DynamoSchedule implements DynamoTable, Schedule {

    private String guid;
    private String studyUserCompoundKey;
    private String schedulePlanGuid;
    private String label;
    private ActivityType activityType;
    private String activityRef;
    private ScheduleType scheduleType;
    private String schedule;
    private Long expires;
    
    public Schedule copy() {
        DynamoSchedule schedule = new DynamoSchedule();
        schedule.setStudyUserCompoundKey(getStudyUserCompoundKey());
        schedule.setSchedulePlanGuid(getSchedulePlanGuid());
        schedule.setLabel(getLabel());
        schedule.setActivityType(getActivityType());
        schedule.setActivityRef(getActivityRef());
        schedule.setScheduleType(getScheduleType());
        schedule.setSchedule(getSchedule());
        schedule.setExpires(getExpires());
        return schedule;
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
    @DynamoDBRangeKey
    @JsonIgnore
    // This is only needed for DynamoDB to differentiate the records: consumers
    // don't actually need this information, they need the IDs in the schedule 
    // for the tasks they are to perform.
    public String getGuid() {
        return guid;
    }
    public void setGuid(String guid) {
        this.guid = guid;
    }
    @DynamoDBAttribute
    @JsonIgnore
    public String getSchedulePlanGuid() { 
        return this.schedulePlanGuid;
    }
    public void setSchedulePlanGuid(String schedulePlanGuid) {
        this.schedulePlanGuid = schedulePlanGuid;
    }
    @JsonIgnore
    @DynamoDBAttribute
    @DynamoDBMarshalling(marshallerClass = JsonNodeMarshaller.class)
    public JsonNode getData() {
        ObjectNode data = JsonNodeFactory.instance.objectNode();
        JsonUtils.write(data, "label", label);
        JsonUtils.write(data, "activityType", activityType);
        JsonUtils.write(data, "activityRef", activityRef);
        JsonUtils.write(data, "scheduleType", scheduleType);
        JsonUtils.write(data, "schedule", schedule);
        JsonUtils.write(data, "expires", expires);
        return data;
    }
    public void setData(JsonNode data) {
        this.label = JsonUtils.asText(data, "label");
        this.activityType = JsonUtils.asActivityType(data, "activityType");
        this.activityRef = JsonUtils.asText(data, "activityRef");
        this.scheduleType = JsonUtils.asScheduleType(data, "scheduleType");
        this.schedule = JsonUtils.asText(data, "schedule");
        this.expires = JsonUtils.asLong(data, "expires");
    }
    @DynamoDBIgnore
    public String getLabel() {
        return label;
    }
    public void setLabel(String label) {
        this.label = label;
    }
    @JsonSerialize(using = LowercaseEnumJsonSerializer.class)
    @DynamoDBIgnore
    public ActivityType getActivityType() {
        return activityType;
    }
    @JsonDeserialize(using = ActivityTypeDeserializer.class)
    public void setActivityType(ActivityType activityType) {
        this.activityType = activityType;
    }
    @DynamoDBIgnore
    public String getActivityRef() {
        return activityRef;
    }
    public void setActivityRef(String activityRef) {
        this.activityRef = activityRef;
    }
    @JsonSerialize(using = LowercaseEnumJsonSerializer.class)
    @DynamoDBIgnore
    public ScheduleType getScheduleType() {
        return scheduleType;
    }
    @JsonDeserialize(using = ScheduleTypeDeserializer.class)
    public void setScheduleType(ScheduleType scheduleType) {
        this.scheduleType = scheduleType;
    }
    @DynamoDBIgnore
    public String getSchedule() {
        return schedule;
    }
    public void setSchedule(String schedule) {
        this.schedule = schedule;
    }
    @JsonSerialize(using = PeriodJsonSerializer.class)
    @DynamoDBIgnore
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
        DynamoSchedule other = (DynamoSchedule) obj;
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
        return "DynamoSchedule [studyUserCompoundKey=" + studyUserCompoundKey + ", schedulePlanGuid="
                + schedulePlanGuid + ", label=" + label + ", activityType=" + activityType + ", activityRef="
                + activityRef + ", scheduleType=" + scheduleType + ", schedule=" + schedule + ", expires=" + expires
                + "]";
    }
    
}
