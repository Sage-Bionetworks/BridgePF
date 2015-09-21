package org.sagebionetworks.bridge.dynamodb;

import java.util.Objects;

import org.joda.time.DateTime;
import org.joda.time.LocalDateTime;
import org.sagebionetworks.bridge.json.DateTimeToLongDeserializer;
import org.sagebionetworks.bridge.json.DateTimeToLongSerializer;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.BridgeEntity;
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

@DynamoDBTable(tableName = "Task")
public final class DynamoTask implements BridgeEntity, Task {

    private static final String ACTIVITY_PROPERTY = "activity";

    private String healthCode;
    private String guid;
    private Long startedOn;
    private Long finishedOn;
    private LocalDateTime localScheduledOn;
    private LocalDateTime localExpiresOn;
    private Activity activity;
    private String runKey;
    private Long hidesOn;
    private boolean persistent;

    public DynamoTask() {
        setHidesOn(new Long(Long.MAX_VALUE));
    }
    
    public DynamoTask(Task task) {
        setLocalScheduledOn(task.getScheduledOn().toLocalDateTime());
        setLocalExpiresOn(task.getExpiresOn().toLocalDateTime());
        setHidesOn(task.getHidesOn());
        setRunKey(task.getRunKey());
        setHealthCode(healthCode);
        setGuid(task.getGuid());
        setStartedOn(task.getStartedOn());
        setFinishedOn(task.getFinishedOn());
        setActivity(task.getActivity());
        setPersistent(task.getPersistent());
    }

    @Override
    @DynamoDBIgnore
    @JsonIgnore
    public TaskStatus getStatus() {
        throw new UnsupportedOperationException("DynamoTask not implemented with a known time zone.");
    }
    
    @Override
    @DynamoDBIgnore
    @JsonIgnore
    public DateTime getScheduledOn() {
        throw new UnsupportedOperationException("DynamoTask not implemented with a known time zone.");
    }
    
    @Override
    @DynamoDBIgnore
    @JsonIgnore
    public DateTime getExpiresOn() {
        throw new UnsupportedOperationException("DynamoTask not implemented with a known time zone.");
    }

    /**
     * The scheduled time without a time zone. This value is stored, but not returned in the JSON of the API. It is
     * localized using the caller's time zone.
     */
    @DynamoDBAttribute
    @DynamoDBMarshalling(marshallerClass = LocalDateTimeMarshaller.class)
    public LocalDateTime getLocalScheduledOn() {
        return localScheduledOn;
    }

    public void setLocalScheduledOn(LocalDateTime localScheduledOn) {
        this.localScheduledOn = localScheduledOn;
    }

    /**
     * The expiration time without a time zone. This value is stored, but not returned in the JSON of the API. It is
     * localized using the caller's time zone.
     */
    @DynamoDBAttribute
    @DynamoDBMarshalling(marshallerClass = LocalDateTimeMarshaller.class)
    public LocalDateTime getLocalExpiresOn() {
        return localExpiresOn;
    }

    public void setLocalExpiresOn(LocalDateTime localExpiresOn) {
        this.localExpiresOn = localExpiresOn;
    }

    @DynamoDBAttribute
    @Override
    public Long getHidesOn() {
        return this.hidesOn;
    }

    @Override
    public void setHidesOn(Long hidesOn) {
        this.hidesOn = hidesOn;
    }

    @DynamoDBAttribute
    @DynamoDBIndexRangeKey(localSecondaryIndexName = "hashKey-runKey-index")
    public String getRunKey() {
        return this.runKey;
    }

    public void setRunKey(String runKey) {
        this.runKey = runKey;
    }

    @DynamoDBHashKey
    public String getHealthCode() {
        return healthCode;
    }

    public void setHealthCode(String healthCode) {
        this.healthCode = healthCode;
    }

    @DynamoDBRangeKey
    public String getGuid() {
        return guid;
    }

    public void setGuid(String guid) {
        this.guid = guid;
    }

    @DynamoDBAttribute
    @JsonSerialize(using = DateTimeToLongSerializer.class)
    public Long getStartedOn() {
        return startedOn;
    }

    @JsonDeserialize(using = DateTimeToLongDeserializer.class)
    public void setStartedOn(Long startedOn) {
        this.startedOn = startedOn;
    }

    @DynamoDBAttribute
    @JsonSerialize(using = DateTimeToLongSerializer.class)
    public Long getFinishedOn() {
        return finishedOn;
    }

    @JsonDeserialize(using = DateTimeToLongDeserializer.class)
    public void setFinishedOn(Long finishedOn) {
        this.finishedOn = finishedOn;
    }

    @DynamoDBIgnore
    public Activity getActivity() {
        return activity;
    }

    public void setActivity(Activity activity) {
        this.activity = activity;
    }

    @DynamoDBMarshalling(marshallerClass = JsonNodeMarshaller.class)
    public ObjectNode getData() {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.putPOJO(ACTIVITY_PROPERTY, activity);
        return node;
    }

    public void setData(ObjectNode data) {
        this.activity = JsonUtils.asEntity(data, ACTIVITY_PROPERTY, Activity.class);
    }

    @DynamoDBAttribute
    public boolean getPersistent() {
        return persistent;
    }

    public void setPersistent(boolean persistent) {
        this.persistent = persistent;
    }

    @Override
    public int hashCode() {
        return Objects.hash(activity, guid, localScheduledOn, localExpiresOn, startedOn, finishedOn, healthCode,
                        runKey, hidesOn, persistent);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        DynamoTask other = (DynamoTask) obj;
        return (Objects.equals(activity, other.activity) && Objects.equals(localExpiresOn, other.localExpiresOn)
                        && Objects.equals(localScheduledOn, other.localScheduledOn) && Objects.equals(guid, other.guid)
                        && Objects.equals(startedOn, other.startedOn) && Objects.equals(finishedOn, other.finishedOn)
                        && Objects.equals(healthCode, other.healthCode) && Objects.equals(hidesOn, other.hidesOn)
                        && Objects.equals(runKey, other.runKey) && Objects.equals(persistent, other.persistent));
    }

    @Override
    public String toString() {
        return String.format("DynamoTask [healthCode=%s, guid=%s, localScheduledOn=%s, localExpiresOn=%s, startedOn=%s, finishedOn=%s, persistent=%s, activity=%s]",
            healthCode, guid, localScheduledOn, localExpiresOn, startedOn, finishedOn, persistent, activity);
    }

}
