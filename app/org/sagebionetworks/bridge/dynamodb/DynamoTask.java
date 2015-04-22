package org.sagebionetworks.bridge.dynamodb;

import java.util.Objects;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.json.DateTimeJsonDeserializer;
import org.sagebionetworks.bridge.json.DateTimeJsonSerializer;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.Task;
import org.sagebionetworks.bridge.models.schedules.TaskStatus;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

@BridgeTypeName("Task")
@DynamoDBTable(tableName = "Task")
public final class DynamoTask implements Task {

    private static final String ACTIVITY_PROPERTY = "activity";
    
    private String healthCode;
    private String guid;
    private String schedulePlanGuid;
    private Long scheduledOn;
    private Long expiresOn;
    private Long startedOn;
    private Long finishedOn;
    private Activity activity;
    
    @JsonIgnore
    @DynamoDBIgnore
    public String getNaturalKey() {
        return String.format("%s:%s:%s", schedulePlanGuid, scheduledOn, activity.getRef());
    }
    
    @DynamoDBIgnore
    public TaskStatus getStatus() {
        if (finishedOn != null) {
            return TaskStatus.FINISHED;
        } else if (startedOn != null) {
            return TaskStatus.STARTED;
        } else if (expiresOn != null && DateTime.now().isAfter(expiresOn)) {
            return TaskStatus.EXPIRED;
        } else if (scheduledOn != null && DateTime.now().isBefore(scheduledOn)) {
            return TaskStatus.SCHEDULED;
        }
        return TaskStatus.AVAILABLE;
    }
    
    @JsonIgnore
    @DynamoDBHashKey
    public String getHealthCode() {
        return healthCode;
    }
    public void setHealthCode(String healthCode) {
        this.healthCode = healthCode;
    }
    @DynamoDBAttribute
    public String getGuid() {
        return guid;
    }
    public void setGuid(String guid) {
        this.guid = guid;
    }
    @DynamoDBAttribute    
    public String getSchedulePlanGuid() {
        return schedulePlanGuid;
    }
    public void setSchedulePlanGuid(String schedulePlanGuid) {
        this.schedulePlanGuid = schedulePlanGuid;
    }
    @DynamoDBIgnore
    public Activity getActivity() {
        return activity;
    }
    public void setActivity(Activity activity) {
        this.activity = activity;
    }
    @DynamoDBRangeKey
    @JsonSerialize(using = DateTimeJsonSerializer.class)
    public Long getScheduledOn() {
        return scheduledOn;
    }
    @JsonDeserialize(using = DateTimeJsonDeserializer.class)
    public void setScheduledOn(Long scheduledOn) {
        this.scheduledOn = scheduledOn;
    }
    @DynamoDBIgnore
    public void setScheduledOn(DateTime scheduledOn) {
        if (scheduledOn != null) {
            this.scheduledOn = scheduledOn.getMillis();    
        }
    }
    @DynamoDBAttribute
    @JsonSerialize(using = DateTimeJsonSerializer.class)
    public Long getExpiresOn() {
        return expiresOn;
    }
    @JsonDeserialize(using = DateTimeJsonDeserializer.class)
    public void setExpiresOn(Long expiresOn) {
        this.expiresOn = expiresOn;
    }
    @DynamoDBIgnore
    public void setExpiresOn(DateTime expiresOn) {
        if (expiresOn != null) {
            this.expiresOn = expiresOn.getMillis();    
        }
    }
    @DynamoDBAttribute
    @JsonSerialize(using = DateTimeJsonSerializer.class)
    public Long getStartedOn() {
        return startedOn;
    }
    @JsonDeserialize(using = DateTimeJsonDeserializer.class)
    public void setStartedOn(Long startedOn) {
        this.startedOn = startedOn;
    }
    @DynamoDBIgnore
    public void setStartedOn(DateTime startedOn) {
        if (startedOn != null) {
            this.startedOn = startedOn.getMillis();
        }
    }
    @DynamoDBAttribute
    @JsonSerialize(using = DateTimeJsonSerializer.class)
    public Long getFinishedOn() {
        return finishedOn;
    }
    @JsonDeserialize(using = DateTimeJsonDeserializer.class)
    public void setFinishedOn(Long finishedOn) {
        this.finishedOn = finishedOn;
    }
    @DynamoDBIgnore
    public void setFinishedOn(DateTime finishedOn) {
        if (finishedOn != null) {
            this.finishedOn = finishedOn.getMillis();
        }
    }
    @JsonIgnore
    public JsonNode getData() {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.putPOJO(ACTIVITY_PROPERTY, activity);
        return node;
    }
    public void setData(JsonNode data) {
        this.activity = JsonUtils.asEntity(data, ACTIVITY_PROPERTY, Activity.class);
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Objects.hashCode(activity);
        result = prime * result + Objects.hashCode(expiresOn);
        result = prime * result + Objects.hashCode(guid);
        result = prime * result + Objects.hashCode(schedulePlanGuid);
        result = prime * result + Objects.hashCode(scheduledOn);
        result = prime * result + Objects.hashCode(startedOn);
        result = prime * result + Objects.hashCode(finishedOn);
        result = prime * result + Objects.hashCode(healthCode);
        return result;
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        DynamoTask other = (DynamoTask) obj;
        return (Objects.equals(activity, other.activity) && Objects.equals(expiresOn, other.expiresOn) && 
                Objects.equals(guid, other.guid) && Objects.equals(schedulePlanGuid, other.schedulePlanGuid) &&
                Objects.equals(startedOn, other.startedOn) && Objects.equals(finishedOn, other.finishedOn) && 
                Objects.equals(scheduledOn, other.scheduledOn) && Objects.equals(healthCode, other.healthCode));
    }
    @Override
    public String toString() {
        return String.format("DynamoTask [status=%s, healthCode=%s, guid=%s, schedulePlanGuid=%s, scheduledOn=%s, expiresOn=%s, startedOn=%s, finishedOn=%s, activity=%s]",
            getStatus().name(), healthCode, guid, schedulePlanGuid, dt(scheduledOn), dt(expiresOn), dt(startedOn), dt(finishedOn), activity);
    }
    
    private String dt(Long dt) {
        return (dt == null) ? null : new DateTime(dt).toString();
    }

}
