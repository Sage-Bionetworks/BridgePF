package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;

import org.sagebionetworks.bridge.json.DateTimeJsonDeserializer;
import org.sagebionetworks.bridge.json.DateTimeJsonSerializer;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.json.LowercaseEnumJsonSerializer;
import org.sagebionetworks.bridge.json.PeriodJsonDeserializer;
import org.sagebionetworks.bridge.json.PeriodJsonSerializer;
import org.sagebionetworks.bridge.json.ScheduleTypeDeserializer;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.User;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.ActivityType;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.ScheduleType;
import org.sagebionetworks.bridge.models.studies.Study;

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
import com.google.common.collect.Lists;

@DynamoDBTable(tableName = "Schedule")
public class DynamoSchedule implements DynamoTable, Schedule {

    private String guid;
    private String studyUserCompoundKey;
    private String schedulePlanGuid;
    private String label;
    private ScheduleType scheduleType;
    private String cronTrigger;
    private Long startsOn;
    private Long endsOn;
    private Long expires;
    private List<Activity> activities = Lists.newArrayList();
    /*
    private ActivityType activityType;
    private String activityRef;
    */
    
    public Schedule copy() {
        Schedule schedule = new DynamoSchedule();
        schedule.setStudyUserCompoundKey(getStudyUserCompoundKey());
        schedule.setSchedulePlanGuid(getSchedulePlanGuid());
        schedule.setLabel(getLabel());
        //schedule.setActivityType(getActivityType());
        //schedule.setActivityRef(getActivityRef());
        schedule.setActivities(getActivities());
        schedule.setScheduleType(getScheduleType());
        schedule.setCronTrigger(getCronTrigger());
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
        setStudyUserCompoundKey(study.getIdentifier()+":"+user.getId());
    }
    @DynamoDBRangeKey
    @JsonIgnore
    // This is only needed for DynamoDB to differentiate the records: consumers
    // don't actually need this information, they need the IDs in the cronTrigger 
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
        JsonUtils.write(data, "activities", activities);
        JsonUtils.write(data, "scheduleType", scheduleType);
        JsonUtils.write(data, "cronTrigger", cronTrigger);
        JsonUtils.write(data, "startsOn", startsOn);
        JsonUtils.write(data, "endsOn", endsOn);
        JsonUtils.write(data, "expires", expires);
        return data;
    }
    public void setData(JsonNode data) {
        this.label = JsonUtils.asText(data, "label");
        JsonUtils.addToActivityList(data, activities, "activities");
        
        // V1 data structure, convert to V2.
        ActivityType activityType = JsonUtils.asActivityType(data, "activityType");
        String activityRef = JsonUtils.asText(data, "activityRef");
        if (activityType != null && activityRef != null) {
            this.activities.add(new Activity(activityType, activityRef));
        }
        //this.activityType = JsonUtils.asActivityType(data, "activityType");
        //this.activityRef = JsonUtils.asText(data, "activityRef");
        
        this.scheduleType = JsonUtils.asScheduleType(data, "scheduleType");
        this.cronTrigger = JsonUtils.asText(data, "cronTrigger");
        this.startsOn = JsonUtils.asLong(data, "startsOn");
        this.endsOn = JsonUtils.asLong(data, "endsOn");
        this.expires = JsonUtils.asLong(data, "expires");
    }
    
    @DynamoDBIgnore
    public List<Activity> getActivities() {
        return activities;
    }
    public void setActivities(List<Activity> activities) {
        this.activities = activities;
    }
    public void addActivity(Activity activity) {
        checkNotNull(activity);
        this.activities.add(activity);
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
        return (activities == null || activities.isEmpty()) ? null : activities.get(0).getType();
    }
    /*
    @JsonDeserialize(using = ActivityTypeDeserializer.class)
    public void setActivityType(ActivityType activityType) {
        this.activityType = activityType;
    }
    */
    @DynamoDBIgnore
    public String getActivityRef() {
        return (activities == null || activities.isEmpty()) ? null : activities.get(0).getRef();
    }
    /*
    public void setActivityRef(String activityRef) {
        this.activityRef = activityRef;
    }
    */
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
    public String getCronTrigger() {
        return cronTrigger;
    }
    public void setCronTrigger(String cronTrigger) {
        this.cronTrigger = cronTrigger;
    }
    @JsonSerialize(using = DateTimeJsonSerializer.class)
    @DynamoDBIgnore
    public Long getStartsOn() {
        return startsOn;
    }
    @JsonDeserialize(using = DateTimeJsonDeserializer.class)
    public void setStartsOn(Long startsOn) {
        this.startsOn = startsOn;
    }
    @JsonSerialize(using = DateTimeJsonSerializer.class)
    @DynamoDBIgnore
    public Long getEndsOn() {
        return endsOn;
    }
    @JsonDeserialize(using = DateTimeJsonDeserializer.class)
    public void setEndsOn(Long endsOn) {
        this.endsOn = endsOn;
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
    @JsonIgnore
    @DynamoDBIgnore
    public boolean isScheduleFor(GuidCreatedOnVersionHolder keys) {
        for (Activity activity : activities) {
            if (activity.getSurvey() != null && keys.equals(activity.getSurvey())) {
                return true;
            }
        }
        return false;
    }
    
}
