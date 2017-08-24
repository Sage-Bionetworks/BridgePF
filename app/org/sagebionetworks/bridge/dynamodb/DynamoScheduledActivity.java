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

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import com.amazonaws.services.dynamodbv2.model.ProjectionType;
import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
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
    private JsonNode clientData;
    private String referentGuid;

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
    @DynamoDBIgnore
    @JsonSerialize(using = DateTimeSerializer.class)
    public DateTime getExpiresOn() {
        return getInstant(getLocalExpiresOn());
    }
    
    private DateTime getInstant(LocalDateTime localDateTime) {
        return (localDateTime == null || timeZone == null) ? null : localDateTime.toDateTime(timeZone);
    }

    /**
     * The scheduled time without a time zone. This value is stored, but not returned in the JSON of the API. It is
     * localized using the caller's time zone.
     */
    @DynamoDBAttribute
    @DynamoDBTypeConverted(converter = LocalDateTimeMarshaller.class)
    @JsonIgnore
    public LocalDateTime getLocalScheduledOn() {
        return localScheduledOn;
    }

    @Override
    public void setLocalScheduledOn(LocalDateTime localScheduledOn) {
        this.localScheduledOn = localScheduledOn;
    }

    /**
     * The expiration time without a time zone. This value is stored, but not returned in the JSON of the API. It is
     * localized using the caller's time zone.
     */
    @DynamoDBAttribute
    @DynamoDBTypeConverted(converter = LocalDateTimeMarshaller.class)
    @JsonIgnore
    public LocalDateTime getLocalExpiresOn() {
        return localExpiresOn;
    }

    @Override
    public void setLocalExpiresOn(LocalDateTime localExpiresOn) {
        this.localExpiresOn = localExpiresOn;
    }

    @DynamoDBHashKey
    @DynamoDBIndexHashKey(attributeName="healthCode", globalSecondaryIndexName="healthCode-referentGuid-index")
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
    
    @DynamoDBIndexHashKey(attributeName = "schedulePlanGuid", globalSecondaryIndexName = "schedulePlanGuid-index")
    @DynamoProjection(projectionType = ProjectionType.KEYS_ONLY, globalSecondaryIndexName = "schedulePlanGuid-index")
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

    @DynamoDBTypeConverted(converter = JsonNodeMarshaller.class)
    @JsonIgnore
    public ObjectNode getData() {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.putPOJO(ACTIVITY_PROPERTY, activity);
        return node;
    }

    public void setData(ObjectNode data) {
        this.activity = JsonUtils.asEntity(data, ACTIVITY_PROPERTY, Activity.class);
    }

    @DynamoDBTypeConverted(converter = JsonNodeMarshaller.class)
    @DynamoDBAttribute
    public JsonNode getClientData() {
        return clientData;
    }
    
    @DynamoDBTypeConverted(converter = JsonNodeMarshaller.class)
    public void setClientData(JsonNode clientData) {
        this.clientData = clientData;
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

    @DynamoDBAttribute
    @DynamoDBIndexRangeKey(attributeName = "referentGuid", globalSecondaryIndexName = "healthCode-referentGuid-index")
    @Override
    @JsonIgnore
    public String getReferentGuid() {
        return referentGuid;
    }

    @Override
    public void setReferentGuid(String referentGuid) {
        this.referentGuid = referentGuid;
    }


    @Override
    public int hashCode() {
        return Objects.hash(activity, guid, localScheduledOn, localExpiresOn, startedOn, finishedOn,
                healthCode, persistent, timeZone, schedulePlanGuid, clientData, referentGuid);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        DynamoScheduledActivity other = (DynamoScheduledActivity) obj;
        return (Objects.equals(activity, other.activity) 
                && Objects.equals(localExpiresOn, other.localExpiresOn)
                && Objects.equals(localScheduledOn, other.localScheduledOn)
                && Objects.equals(guid, other.guid)
                && Objects.equals(startedOn, other.startedOn) 
                && Objects.equals(finishedOn, other.finishedOn)
                && Objects.equals(healthCode, other.healthCode) && Objects.equals(persistent, other.persistent)
                && Objects.equals(timeZone, other.timeZone)
                && Objects.equals(schedulePlanGuid, other.schedulePlanGuid)
                && Objects.equals(clientData, other.clientData)
                && Objects.equals(referentGuid, other.referentGuid));
    }

    @Override
    public String toString() {
        return String.format(
                "DynamoScheduledActivity [healthCode=%s, guid=%s, localScheduledOn=%s, localExpiresOn=%s, startedOn=%s, finishedOn=%s, persistent=%s, timeZone=%s, activity=%s, schedulePlanGuid=%s, referentGuid=%s, clientData=%s]",
                healthCode, guid, localScheduledOn, localExpiresOn, startedOn, finishedOn, persistent, timeZone,
                activity, schedulePlanGuid, referentGuid, clientData);
    }
}
