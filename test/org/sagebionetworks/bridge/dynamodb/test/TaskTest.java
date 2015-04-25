package org.sagebionetworks.bridge.dynamodb.test;

import org.sagebionetworks.bridge.dynamodb.DynamoDBProjection;
import org.sagebionetworks.bridge.dynamodb.DynamoThroughput;
import org.sagebionetworks.bridge.dynamodb.JsonNodeMarshaller;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.schedules.Activity;
import org.sagebionetworks.bridge.models.schedules.TaskStatus;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMarshalling;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.model.ProjectionType;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

@DynamoDBTable(tableName = "Task")
@DynamoThroughput(writeCapacity=18L, readCapacity=20L)
public class TaskTest {

    private static final String ACTIVITY_PROPERTY = "activity";
    
    private String healthCode;
    private String guid;
    private String schedulePlanGuid;
    private Long scheduledOn;
    private Long expiresOn;
    private Long startedOn;
    private Long finishedOn;
    private Activity activity;
    
    @DynamoDBIgnore
    public TaskStatus getStatus() {
        return TaskStatus.FINISHED;
    }
    @DynamoDBHashKey
    @DynamoDBIndexHashKey(attributeName = "healthCode", globalSecondaryIndexName = "healthCode-scheduledOn-index")
    @DynamoDBProjection(projectionType=ProjectionType.ALL, globalSecondaryIndexName = "healthCode-scheduledOn-index")
    public String getHealthCode() {
        return healthCode;
    }
    public void setHealthCode(String healthCode) {
        this.healthCode = healthCode;
    }
    @DynamoDBRangeKey
    @DynamoDBIndexHashKey(attributeName = "guid", globalSecondaryIndexName ="guid-index")
    @DynamoDBProjection(projectionType=ProjectionType.INCLUDE, globalSecondaryIndexName = "guid-index")
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
    @DynamoDBIndexRangeKey(attributeName = "scheduledOn", globalSecondaryIndexName = "healthCode-scheduledOn-index")
    public Long getScheduledOn() {
        return scheduledOn;
    }
    public void setScheduledOn(Long scheduledOn) {
        this.scheduledOn = scheduledOn;
    }
    // Range only index
    @DynamoDBIndexRangeKey(attributeName = "expiresOn", globalSecondaryIndexName = "healthCode-expiresOn-index")
    @DynamoDBAttribute
    public Long getExpiresOn() {
        return expiresOn;
    }
    public void setExpiresOn(Long expiresOn) {
        this.expiresOn = expiresOn;
    }
    @DynamoDBAttribute
    public Long getStartedOn() {
        return startedOn;
    }
    public void setStartedOn(Long startedOn) {
        this.startedOn = startedOn;
    }
    @DynamoDBAttribute
    public Long getFinishedOn() {
        return finishedOn;
    }
    public void setFinishedOn(Long finishedOn) {
        this.finishedOn = finishedOn;
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

}
