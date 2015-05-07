package org.sagebionetworks.bridge.dynamodb;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.models.tasks.TaskEvent;
import org.sagebionetworks.bridge.models.tasks.TaskEventAction;
import org.sagebionetworks.bridge.models.tasks.TaskEventType;
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
        private TaskEventType type;
        private String id;
        private TaskEventAction action;
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
        public Builder withType(TaskEventType type) {
            this.type = type;
            return this;
        }
        public Builder withId(String id) {
            this.id = id;
            return this;
        }
        public Builder withAction(TaskEventAction action) {
            this.action = action;
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
            if (id != null && action != null && value != null) {
                return String.format("%s:%s:%s=%s", typeName, id, action.name().toLowerCase(), value);
            } else if (id != null && action != null) {
                return String.format("%s:%s:%s", typeName, id, action.name().toLowerCase());
            } else if (id != null) {
                return String.format("%s:%s", typeName, id);
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
