package org.sagebionetworks.bridge.dynamodb;

import java.util.Objects;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDateTime;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.json.DateTimeSerializer;
import org.sagebionetworks.bridge.json.DateTimeToLongDeserializer;
import org.sagebionetworks.bridge.json.DateTimeToLongSerializer;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivity;
import org.sagebionetworks.bridge.models.schedules.ScheduledActivityStatus;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMarshalling;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.model.ProjectionType;
import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

@BridgeTypeName("ScheduledActivity")
@DynamoDBTable(tableName = "Task")
@JsonFilter("filter")
public final class DynamoScheduledActivity implements ScheduledActivity, BridgeEntity {
    
    private static final String ACTIVITY_PROPERTY = "activity";

    private String healthCode;
    private String guid;
    private String schedulePlanGuid;
    private Long startedOn;
    private Long finishedOn;
    private LocalDateTime localScheduledOn;
    private LocalDateTime localExpiresOn;
    private Activity activity;
    private boolean persistent;
    private DateTimeZone timeZone;

    @Override
    @DynamoDBIgnore
    public ScheduledActivityStatus getStatus() {
        if (finishedOn != null && startedOn == null) {
            return ScheduledActivityStatus.DELETED;
        } else if (finishedOn != null && startedOn != null) {
            return ScheduledActivityStatus.FINISHED;
        } else if (startedOn != null) {
            return ScheduledActivityStatus.STARTED;
        }
        if (timeZone != null) {
            DateTime now = DateTime.now(timeZone);
            DateTime expiresOn = getExpiresOn();
            DateTime scheduledOn = getScheduledOn();
            if (expiresOn != null && now.isAfter(expiresOn)) {
                return ScheduledActivityStatus.EXPIRED;
            } else if (scheduledOn != null && now.isBefore(scheduledOn)) {
                return ScheduledActivityStatus.SCHEDULED;
            }
        }
        return ScheduledActivityStatus.AVAILABLE;
    }

    @DynamoDBIgnore
    @JsonIgnore
    @Override
    public DateTimeZone getTimeZone() {
        return timeZone;
    }

    @Override
    public void setTimeZone(DateTimeZone zone) {
        this.timeZone = zone;
    }

    @Override
    @DynamoDBIgnore
    @JsonSerialize(using = DateTimeSerializer.class)
    public DateTime getScheduledOn() {
        return getInstant(getLocalScheduledOn());
    }

    @Override
    public void setScheduledOn(DateTime scheduledOn) {
        this.localScheduledOn = (scheduledOn == null) ? null : scheduledOn.toLocalDateTime();
    }

    @Override
    @DynamoDBIgnore
    @JsonSerialize(using = DateTimeSerializer.class)
    public DateTime getExpiresOn() {
        return getInstant(getLocalExpiresOn());
    }

    @Override
    public void setExpiresOn(DateTime expiresOn) {
        this.localExpiresOn = (expiresOn == null) ? null : expiresOn.toLocalDateTime();
    }

    private DateTime getInstant(LocalDateTime localDateTime) {
        return (localDateTime == null || timeZone == null) ? null : localDateTime.toDateTime(timeZone);
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

    @DynamoDBIndexHashKey(attributeName="schedulePlanGuid", globalSecondaryIndexName = "schedulePlanGuid-index")
    @DynamoProjection(projectionType=ProjectionType.KEYS_ONLY, globalSecondaryIndexName = "schedulePlanGuid-index")
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
        return Objects.hash(activity, guid, localScheduledOn, localExpiresOn, startedOn, finishedOn, healthCode,
                persistent, timeZone, schedulePlanGuid);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        DynamoScheduledActivity other = (DynamoScheduledActivity) obj;
        return (Objects.equals(activity, other.activity) && Objects.equals(localExpiresOn, other.localExpiresOn)
                && Objects.equals(localScheduledOn, other.localScheduledOn) && Objects.equals(guid, other.guid)
                && Objects.equals(startedOn, other.startedOn) && Objects.equals(finishedOn, other.finishedOn)
                && Objects.equals(healthCode, other.healthCode) && Objects.equals(persistent, other.persistent)
                && Objects.equals(timeZone, other.timeZone) && Objects.equals(schedulePlanGuid, other.schedulePlanGuid));
    }

    @Override
    public String toString() {
        return String.format(
                "DynamoScheduledActivity [healthCode=%s, guid=%s, localScheduledOn=%s, localExpiresOn=%s, startedOn=%s, finishedOn=%s, persistent=%s, timeZone=%s, activity=%s, schedulePlanGuid=%s]",
                healthCode, guid, localScheduledOn, localExpiresOn, startedOn, finishedOn, persistent, timeZone,
                activity, schedulePlanGuid);
    }
}
