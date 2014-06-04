package org.sagebionetworks.bridge.dynamodb.test;

import org.sagebionetworks.bridge.dynamodb.DynamoTable;
import org.sagebionetworks.bridge.dynamodb.JsonNodeMarshaller;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordImpl;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMarshalling;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.fasterxml.jackson.databind.JsonNode;

@DynamoDBTable(tableName = "HealthDataRecord")
public class HealthDataRecordTest implements HealthDataRecord, DynamoTable {

    private String key;
    private String recordId;
    private long startDate;
    private long endDate;
    private JsonNode data;

    public HealthDataRecordTest() {
    }
    
    public HealthDataRecordTest(String key) {
        this.key = key;
    }
    
    public HealthDataRecordTest(String key, HealthDataRecord record) {
        this.key = key;
        this.recordId = record.getRecordId();
        this.startDate = record.getStartDate();
        this.endDate = record.getEndDate();
        this.data = record.getData();
    }
    
    public HealthDataRecordTest(String key, String recordId, HealthDataRecord record) {
        this.key = key;
        this.recordId = recordId;
        this.startDate = record.getStartDate();
        this.endDate = record.getEndDate();
        this.data = record.getData();
    }
    
    public HealthDataRecord toHealthDataRecord() {
        return new HealthDataRecordImpl(recordId, startDate, endDate, data);
    }
    
    @DynamoDBHashKey
    public String getKey() { 
        return key; 
    }
    public void setKey(String key) { 
        this.key = key; 
    }
    
    @Override 
    @DynamoDBRangeKey
    public String getRecordId() { 
        return recordId; 
    }
    @Override
    public void setRecordId(String recordId) { 
        this.recordId = recordId;
    }
    
    @Override 
    @DynamoDBAttribute
    @DynamoDBIndexRangeKey(attributeName="startDate", localSecondaryIndexName="startDate-index")
    public long getStartDate() { 
        return startDate; 
    }
    @Override
    public void setStartDate(long startDate) { 
        this.startDate = startDate; 
    }
    
    @Override 
    @DynamoDBAttribute
    @DynamoDBIndexRangeKey(attributeName="endDate", localSecondaryIndexName="endDate-index")
    public long getEndDate() { 
        return endDate; 
    }
    @Override
    public void setEndDate(long endDate) { 
        this.endDate = endDate; 
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
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int) (endDate ^ (endDate >>> 32));
        result = prime * result + ((recordId == null) ? 0 : recordId.hashCode());
        result = prime * result + ((key == null) ? 0 : key.hashCode());
        result = prime * result + (int) (startDate ^ (startDate >>> 32));
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
        HealthDataRecordTest other = (HealthDataRecordTest)obj;
        if (endDate != other.endDate)
            return false;
        if (recordId == null) {
            if (other.recordId != null)
                return false;
        } else if (!recordId.equals(other.recordId))
            return false;
        if (key == null) {
            if (other.key != null)
                return false;
        } else if (!key.equals(other.key))
            return false;
        if (startDate != other.startDate)
            return false;
        return true;
    }


}
