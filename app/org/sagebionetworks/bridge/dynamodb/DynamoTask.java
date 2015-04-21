package org.sagebionetworks.bridge.dynamodb;

import java.util.Objects;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.json.DateTimeJsonDeserializer;
import org.sagebionetworks.bridge.json.DateTimeJsonSerializer;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.Task;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;

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
    
    private String studyHealthCodeKey;
    private String guid;
    private String schedulePlanGuid;
    private Long scheduledOn;
    private Long expiresOn;
    private Activity activity;
    
    @JsonIgnore
    @DynamoDBIgnore
    public String getNaturalKey() {
        return String.format("%s:%s:%s", schedulePlanGuid, scheduledOn, activity.getRef());
    }
    
    @JsonIgnore
    @DynamoDBHashKey
    public String getStudyHealthCodeKey() {
        return studyHealthCodeKey;
    }
    public void setStudyHealthCodeKey(String studyHealthCodeKey) {
        this.studyHealthCodeKey = studyHealthCodeKey;
    }
    public void setStudyHealthCodeKey(StudyIdentifier identifier, String healthCode) {
        this.studyHealthCodeKey = identifier.getIdentifier() + ":" + healthCode;
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
    @JsonIgnore
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
    @JsonIgnore
    @DynamoDBIgnore
    public void setExpiresOn(DateTime expiresOn) {
        if (expiresOn != null) {
            this.expiresOn = expiresOn.getMillis();    
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
        result = prime * result + Objects.hashCode(studyHealthCodeKey);
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
                Objects.equals(scheduledOn, other.scheduledOn) && Objects.equals(studyHealthCodeKey, other.studyHealthCodeKey));
    }
    @Override
    public String toString() {
        return String.format("DynamoTask [studyHealthCodeKey=%s, guid=%s, schedulePlanGuid=%s, scheduledOn=%s, expiresOn=%s, activity=%s]",
            studyHealthCodeKey, guid, schedulePlanGuid, new DateTime(scheduledOn).toString(), new DateTime(expiresOn).toString(), activity);
    }

}
