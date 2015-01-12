package org.sagebionetworks.bridge.dynamodb.test;

import org.sagebionetworks.bridge.dynamodb.DynamoTable;
import org.sagebionetworks.bridge.dynamodb.JsonNodeMarshaller;
import org.sagebionetworks.bridge.json.DateTimeJsonDeserializer;
import org.sagebionetworks.bridge.json.DateTimeJsonSerializer;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;

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

@DynamoDBTable(tableName = "HealthDataRecord")
public class HealthDataRecordTest implements HealthDataRecord, DynamoTable {

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

    public HealthDataRecordTest(String key, HealthDataRecord record) {
        this.key = key;
        this.guid = record.getGuid();
        this.startDate = record.getStartDate();
        this.endDate = record.getEndDate();
        this.version = record.getVersion();
        this.data = record.getData();
    }

    public HealthDataRecordTest(String key, String guid, HealthDataRecord record) {
        this.key = key;
        this.guid = guid;
        this.startDate = record.getStartDate();
        this.endDate = record.getEndDate();
        this.version = record.getVersion();
        this.data = record.getData();
    }

    @DynamoDBHashKey
    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    @Override
    @DynamoDBRangeKey(attributeName="recordId")
    public String getGuid() {
        return guid;
    }

    @Override
    public void setGuid(String guid) {
        this.guid = guid;
    }

    @Override
    @DynamoDBAttribute
    @DynamoDBIndexRangeKey(attributeName = "startDate", localSecondaryIndexName = "startDate-index")
    @JsonSerialize(using = DateTimeJsonSerializer.class)
    public long getStartDate() {
        return startDate;
    }

    @Override
    @JsonDeserialize(using = DateTimeJsonDeserializer.class)
    public void setStartDate(long startDate) {
        this.startDate = startDate;
    }

    @Override
    @DynamoDBAttribute
    @DynamoDBIndexRangeKey(attributeName = "endDate", localSecondaryIndexName = "endDate-index")
    @JsonSerialize(using = DateTimeJsonSerializer.class)
    public long getEndDate() {
        return endDate;
    }

    @Override
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

    @Override
    @DynamoDBAttribute
    @DynamoDBMarshalling(marshallerClass = JsonNodeMarshaller.class)
    public JsonNode getData() {
        return data;
    }

    @Override
    public void setData(JsonNode payload) {
        this.data = payload;
    }

    @Override
    @DynamoDBVersionAttribute
    public Long getVersion() {
        return version;
    }
    
    @Override
    public void setVersion(Long version) {
        this.version = version;
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((guid == null) ? 0 : guid.hashCode());
        result = prime * result + ((version == null) ? 0 : version.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        HealthDataRecordTest other = (HealthDataRecordTest) obj;
        if (guid == null) {
            if (other.guid != null)
                return false;
        } else if (!guid.equals(other.guid))
            return false;
        if (version == null) {
            if (other.version != null)
                return false;
        } else if (!version.equals(other.version))
            return false;
        return true;
    }
}
