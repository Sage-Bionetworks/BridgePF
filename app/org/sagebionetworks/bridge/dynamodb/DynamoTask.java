package org.sagebionetworks.bridge.dynamodb;

import java.util.Objects;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDateTime;
import org.sagebionetworks.bridge.json.BridgeTypeName;
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

@BridgeTypeName("Task")
@DynamoDBTable(tableName = "Task")
public final class DynamoTask implements Task, BridgeEntity {

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
    private DateTimeZone timeZone;

    public DynamoTask() {
        setHidesOn(new Long(Long.MAX_VALUE));
    }
    
    @DynamoDBIgnore
    @JsonIgnore
    public DateTimeZone getTimeZone() {
        return timeZone;
    }
    
    public void setTimeZone(DateTimeZone timeZone) {
        this.timeZone = timeZone;
    }

    @Override
    @DynamoDBIgnore
    public TaskStatus getStatus() {
        if (getFinishedOn() != null && getStartedOn() == null) {
            return TaskStatus.DELETED;
        } else if (getFinishedOn() != null && getStartedOn() != null) {
            return TaskStatus.FINISHED;
        } else if (getStartedOn() != null) {
            return TaskStatus.STARTED;
        }
        if (timeZone != null) {
            DateTime now = DateTime.now(timeZone);
            if (getLocalExpiresOn() != null && now.isAfter(getExpiresOn())) {
                return TaskStatus.EXPIRED;
            } else if (getLocalScheduledOn() != null && now.isBefore(getScheduledOn())) {
                return TaskStatus.SCHEDULED;
            }
        }
        return TaskStatus.AVAILABLE;
    }
    
    @Override
    @DynamoDBIgnore
    public DateTime getScheduledOn() {
        return getInstant(getLocalScheduledOn());
    }
    
    @Override
    @DynamoDBIgnore
    public DateTime getExpiresOn() {
        return getInstant(getLocalExpiresOn());
    }
    
    private DateTime getInstant(LocalDateTime localDateTime) {
        return (localDateTime == null) ? null : localDateTime.toDateTime(timeZone);
    }

    /**
     * The scheduled time without a time zone. This value is stored, but not returned in the JSON of the API. It is
     * localized using the caller's time zone.
     */
    @DynamoDBAttribute
    @DynamoDBMarshalling(marshallerClass = LocalDateTimeMarshaller.class)
    @JsonIgnore
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
    @JsonIgnore
    public LocalDateTime getLocalExpiresOn() {
        return localExpiresOn;
    }

    public void setLocalExpiresOn(LocalDateTime localExpiresOn) {
        this.localExpiresOn = localExpiresOn;
    }

    @DynamoDBAttribute
    @Override
    @JsonIgnore
    public Long getHidesOn() {
        return this.hidesOn;
    }

    @Override
    public void setHidesOn(Long hidesOn) {
        this.hidesOn = hidesOn;
    }

    @DynamoDBAttribute
    @Override
    @DynamoDBIndexRangeKey(localSecondaryIndexName = "hashKey-runKey-index")
    public String getRunKey() {
        return this.runKey;
    }

    @Override
    public void setRunKey(String runKey) {
        this.runKey = runKey;
    }

    @DynamoDBHashKey
    @JsonIgnore
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
    @JsonSerialize(using = DateTimeToLongSerializer.class)
    @Override
    public Long getStartedOn() {
        return startedOn;
    }

    @JsonDeserialize(using = DateTimeToLongDeserializer.class)
    @Override
    public void setStartedOn(Long startedOn) {
        this.startedOn = startedOn;
    }

    @DynamoDBAttribute
    @JsonSerialize(using = DateTimeToLongSerializer.class)
    @Override
    public Long getFinishedOn() {
        return finishedOn;
    }

    @JsonDeserialize(using = DateTimeToLongDeserializer.class)
    @Override
    public void setFinishedOn(Long finishedOn) {
        this.finishedOn = finishedOn;
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

    @DynamoDBAttribute
    @Override
    public boolean getPersistent() {
        return persistent;
    }

    @Override
    public void setPersistent(boolean persistent) {
        this.persistent = persistent;
    }

    @Override
    public int hashCode() {
        return Objects.hash(activity, guid, localScheduledOn, localExpiresOn, startedOn, 
            finishedOn, healthCode, runKey, hidesOn, persistent, timeZone);
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
            && Objects.equals(runKey, other.runKey) && Objects.equals(persistent, other.persistent)
            && Objects.equals(timeZone, this.timeZone));
    }

    @Override
    public String toString() {
        return String.format("DynamoTask [healthCode=%s, guid=%s, localScheduledOn=%s, localExpiresOn=%s, startedOn=%s, finishedOn=%s, persistent=%s, timeZone=%s, activity=%s]",
            healthCode, guid, localScheduledOn, localExpiresOn, startedOn, finishedOn, persistent, timeZone, activity);
    }
}
