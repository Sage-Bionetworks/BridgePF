package org.sagebionetworks.bridge.dynamodb;

import java.util.Objects;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.BridgeUtils;
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
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMarshalling;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.fasterxml.jackson.annotation.JsonIgnore;
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
    private String runKey;
    private Long hidesOn;
    
    public DynamoTask() {
        setHidesOn(new Long(Long.MAX_VALUE));
    }
    
    @DynamoDBIgnore
    public TaskStatus getStatus() {
        if (finishedOn != null && startedOn == null) {
            return TaskStatus.DELETED;
        } else if (finishedOn != null && startedOn != null) {
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
    @DynamoDBAttribute
    public Long getHidesOn() {
        return this.hidesOn;
    }
    public void setHidesOn(Long hidesOn) {
        this.hidesOn = hidesOn;
    }
    @JsonIgnore
    @DynamoDBAttribute
    @DynamoDBIndexRangeKey(localSecondaryIndexName = "hashKey-runKey-index")
    public String getRunKey() {
        return this.runKey;
    }
    public void setRunKey(String runKey) {
        this.runKey = runKey;
    }
    @JsonIgnore
    @DynamoDBHashKey
    @Override
    public String getHealthCode() {
        return healthCode;
    }
    @Override
    public void setHealthCode(String healthCode) {
        this.healthCode = healthCode;
    }
    @DynamoDBRangeKey
    @Override
    public String getGuid() {
        return guid;
    }
    @Override
    public void setGuid(String guid) {
        this.guid = guid;
    }
    @DynamoDBAttribute
    @Override
    public String getSchedulePlanGuid() {
        return schedulePlanGuid;
    }
    @Override
    public void setSchedulePlanGuid(String schedulePlanGuid) {
        this.schedulePlanGuid = schedulePlanGuid;
    }
    @DynamoDBIgnore
    @Override
    public Activity getActivity() {
        return activity;
    }
    @Override
    public void setActivity(Activity activity) {
        this.activity = activity;
    }
    @JsonSerialize(using = DateTimeJsonSerializer.class)
    @Override
    public Long getScheduledOn() {
        return scheduledOn;
    }
    @JsonDeserialize(using = DateTimeJsonDeserializer.class)
    @Override
    public void setScheduledOn(Long scheduledOn) {
        this.scheduledOn = scheduledOn;
    }
    @DynamoDBAttribute
    @JsonSerialize(using = DateTimeJsonSerializer.class)
    @Override
    public Long getExpiresOn() {
        return expiresOn;
    }
    @JsonDeserialize(using = DateTimeJsonDeserializer.class)
    @Override
    public void setExpiresOn(Long expiresOn) {
        this.expiresOn = expiresOn;
    }
    @DynamoDBAttribute
    @JsonSerialize(using = DateTimeJsonSerializer.class)
    @Override
    public Long getStartedOn() {
        return startedOn;
    }
    @JsonDeserialize(using = DateTimeJsonDeserializer.class)
    @Override
    public void setStartedOn(Long startedOn) {
        this.startedOn = startedOn;
    }
    @DynamoDBAttribute
    @JsonSerialize(using = DateTimeJsonSerializer.class)
    @Override
    public Long getFinishedOn() {
        return finishedOn;
    }
    @JsonDeserialize(using = DateTimeJsonDeserializer.class)
    @Override
    public void setFinishedOn(Long finishedOn) {
        this.finishedOn = finishedOn;
    }
    @DynamoDBMarshalling(marshallerClass = JsonNodeMarshaller.class)
    @JsonIgnore
    public ObjectNode getData() {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.putPOJO(ACTIVITY_PROPERTY, activity);
        return node;
    }
    public void setData(ObjectNode data) {
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
        result = prime * result + Objects.hashCode(runKey);
        result = prime * result + Objects.hashCode(hidesOn);
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
                Objects.equals(scheduledOn, other.scheduledOn) && Objects.equals(healthCode, other.healthCode) && 
                Objects.equals(hidesOn,  other.hidesOn) && Objects.equals(runKey, other.runKey));
    }
    @Override
    public String toString() {
        return String.format("DynamoTask [status=%s, healthCode=%s, guid=%s, schedulePlanGuid=%s, scheduledOn=%s, expiresOn=%s, startedOn=%s, finishedOn=%s, activity=%s]",
            getStatus().name(), healthCode, guid, schedulePlanGuid, BridgeUtils.toString(scheduledOn),
            BridgeUtils.toString(expiresOn), BridgeUtils.toString(startedOn),
            BridgeUtils.toString(finishedOn), activity);
    }

}
