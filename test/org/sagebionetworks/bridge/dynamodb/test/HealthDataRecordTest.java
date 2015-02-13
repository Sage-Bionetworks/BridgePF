package org.sagebionetworks.bridge.dynamodb.test;

import org.sagebionetworks.bridge.dynamodb.DynamoTable;
import org.sagebionetworks.bridge.dynamodb.JsonNodeMarshaller;
import org.sagebionetworks.bridge.json.DateTimeJsonDeserializer;
import org.sagebionetworks.bridge.json.DateTimeJsonSerializer;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMarshalling;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

// This class is based on an older version of HealthDataRecord, but is really just a dummy table used to test
// DynamoInitializer.
@DynamoDBTable(tableName = "HealthDataRecord")
public class HealthDataRecordTest implements DynamoTable {

    private String key;
    private String guid;
    private long startDate;
    private long endDate;
    private String secondaryKey;
    private Long version;
    private JsonNode data;

    public HealthDataRecordTest() {
    }

    public HealthDataRecordTest(String key) {
        this.key = key;
    }

    @DynamoDBHashKey
    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    @DynamoDBRangeKey(attributeName="recordId")
    public String getGuid() {
        return guid;
    }

    public void setGuid(String guid) {
        this.guid = guid;
    }

    @DynamoDBAttribute
    @DynamoDBIndexRangeKey(attributeName = "startDate", localSecondaryIndexName = "startDate-index")
    @JsonSerialize(using = DateTimeJsonSerializer.class)
    public long getStartDate() {
        return startDate;
    }

    @JsonDeserialize(using = DateTimeJsonDeserializer.class)
    public void setStartDate(long startDate) {
        this.startDate = startDate;
    }

    @DynamoDBAttribute
    @DynamoDBIndexRangeKey(attributeName = "endDate", localSecondaryIndexName = "endDate-index")
    @JsonSerialize(using = DateTimeJsonSerializer.class)
    public long getEndDate() {
        return endDate;
    }

    @JsonDeserialize(using = DateTimeJsonDeserializer.class)
    public void setEndDate(long endDate) {
        this.endDate = endDate;
    }

    @DynamoDBIndexHashKey(attributeName = "secondaryKey", globalSecondaryIndexName = "secondary-index")
    public String getSecondaryKey() {
        return secondaryKey;
    }

    public void setSecondaryKey(String secondaryKey) {
        this.secondaryKey = secondaryKey;
    }

    @DynamoDBAttribute
    @DynamoDBMarshalling(marshallerClass = JsonNodeMarshaller.class)
    public JsonNode getData() {
        return data;
    }

    public void setData(JsonNode payload) {
        this.data = payload;
    }

    @DynamoDBVersionAttribute
    public Long getVersion() {
        return version;
    }
    
    public void setVersion(Long version) {
        this.version = version;
    }
}
