package org.sagebionetworks.bridge.dynamodb;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.models.tasks.EventType;
import org.sagebionetworks.bridge.models.tasks.ObjectType;
import org.sagebionetworks.bridge.models.tasks.TaskEvent;
import org.sagebionetworks.bridge.validators.TaskEventValidator;
import org.sagebionetworks.bridge.validators.Validate;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;

@DynamoDBTable(tableName = "TaskEvent")
public class DynamoTaskEvent implements TaskEvent {

    private String healthCode;
    private Long timestamp;
    private String eventId;
    
    @DynamoDBHashKey
    @Override
    public String getHealthCode() {
        return healthCode;
    }
    @Override
    public void setHealthCode(String healthCode) {
        this.healthCode = healthCode;
    }
    @Override
    public Long getTimestamp() {
        return timestamp;
    }
    @Override
    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
    @DynamoDBRangeKey
    @Override
    public String getEventId() {
        return eventId;
    }
    @Override
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }
    
    public static class Builder {
        private String healthCode;
        private Long timestamp;
        private ObjectType type;
        private String objectId;
        private EventType eventType;
        private String value;
        
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
        public Builder withObjectType(ObjectType type) {
            this.type = type;
            return this;
        }
        public Builder withObjectId(String objectId) {
            this.objectId = objectId;
            return this;
        }
        public Builder withEventType(EventType type) {
            this.eventType = type;
            return this;
        }
        public Builder withValue(String value) {
            this.value = value;
            return this;
        }
        private String getEventId() {
            if (type == null) {
                return null;
            }
            String typeName = type.name().toLowerCase();
            if (objectId != null && eventType != null && value != null) {
                return String.format("%s:%s:%s=%s", typeName, objectId, eventType.name().toLowerCase(), value);
            } else if (objectId != null && eventType != null) {
                return String.format("%s:%s:%s", typeName, objectId, eventType.name().toLowerCase());
            } else if (objectId != null) {
                return String.format("%s:%s", typeName, objectId);
            }
            return typeName;
        }
        
        public DynamoTaskEvent build() {
            DynamoTaskEvent event = new DynamoTaskEvent();
            event.setHealthCode(healthCode);
            event.setTimestamp((timestamp == null) ? null : timestamp);
            event.setEventId(getEventId());
            
            Validate.entityThrowingException(TaskEventValidator.INSTANCE, event);
            
            return event;
        }
    }
}
