package org.sagebionetworks.bridge.dynamodb;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.json.DateTimeToLongDeserializer;
import org.sagebionetworks.bridge.json.DateTimeToLongSerializer;
import org.sagebionetworks.bridge.models.activities.ActivityEvent;
import org.sagebionetworks.bridge.models.activities.ActivityEventObjectType;
import org.sagebionetworks.bridge.models.activities.ActivityEventType;
import org.sagebionetworks.bridge.validators.ActivityEventValidator;
import org.sagebionetworks.bridge.validators.Validate;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

// We must preserve the table name until any migration occurs.
@DynamoDBTable(tableName = "TaskEvent")
@BridgeTypeName("ActivityEvent")
public class DynamoActivityEvent implements ActivityEvent {

    private String healthCode;
    private String answerValue;
    private Long timestamp;
    private String eventId;
    
    @DynamoDBHashKey
    @Override
    @JsonIgnore
    public String getHealthCode() {
        return healthCode;
    }
    public void setHealthCode(String healthCode) {
        this.healthCode = healthCode;
    }
    @Override
    public String getAnswerValue() {
        return answerValue;
    }
    public void setAnswerValue(String answerValue) {
        this.answerValue = answerValue;
    }
    @Override
    @JsonSerialize(using = DateTimeToLongSerializer.class)
    public Long getTimestamp() {
        return timestamp;
    }
    @JsonDeserialize(using = DateTimeToLongDeserializer.class)
    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
    @DynamoDBRangeKey
    @Override
    public String getEventId() {
        return eventId;
    }
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }
    
    public static class Builder {
        private String healthCode;
        private Long timestamp;
        private ActivityEventObjectType type;
        private String objectId;
        private ActivityEventType eventType;
        private String answerValue;
        
        public Builder withHealthCode(String healthCode) {
            this.healthCode = healthCode;
            return this;
        }
        public Builder withTimestamp(Long timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        public Builder withTimestamp(DateTime timestamp) {
            this.timestamp = (timestamp == null) ? null : timestamp.getMillis();
            return this;
        }
        public Builder withObjectType(ActivityEventObjectType type) {
            this.type = type;
            return this;
        }
        public Builder withObjectId(String objectId) {
            this.objectId = objectId;
            return this;
        }
        public Builder withEventType(ActivityEventType type) {
            this.eventType = type;
            return this;
        }
        public Builder withAnswerValue(String answerValue) {
            this.answerValue = answerValue;
            return this;
        }
        private String getEventId() {
            if (type == null) {
                return null;
            }
            if (type == ActivityEventObjectType.ENROLLMENT) {
                return type.name().toLowerCase();
            }
            String typeName = type.name().toLowerCase();
            if (objectId != null && eventType != null) {
                return String.format("%s:%s:%s", typeName, objectId, eventType.name().toLowerCase());
            } else if (objectId != null) {
                return String.format("%s:%s", typeName, objectId);
            }
            return typeName;
        }
        
        public DynamoActivityEvent build() {
            DynamoActivityEvent event = new DynamoActivityEvent();
            event.setHealthCode(healthCode);
            event.setTimestamp((timestamp == null) ? null : timestamp);
            event.setEventId(getEventId());
            event.setAnswerValue(answerValue);

            Validate.entityThrowingException(ActivityEventValidator.INSTANCE, event);
            
            return event;
        }
    }
}
