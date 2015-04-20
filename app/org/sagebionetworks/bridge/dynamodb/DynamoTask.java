package org.sagebionetworks.bridge.dynamodb;

import org.joda.time.DateTime;
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
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

@DynamoDBTable(tableName = "Task")
public class DynamoTask implements Task {

    private static final String ACTIVITY_PROPERTY = "activity";
    
    private String studyHealthCodeKey;
    private String guid;
    private String schedulePlanGuid;
    private DateTime scheduledOn;
    private DateTime expiresOn;
    private Activity activity;
    
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
    @DynamoDBAttribute
    @DynamoDBRangeKey
    public DateTime getScheduledOn() {
        return scheduledOn;
    }
    public void setScheduledOn(DateTime scheduledOn) {
        this.scheduledOn = scheduledOn;
    }
    @DynamoDBAttribute
    public DateTime getExpiresOn() {
        return expiresOn;
    }
    public void setExpiresOn(DateTime expiresOn) {
        this.expiresOn = expiresOn;
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

}
