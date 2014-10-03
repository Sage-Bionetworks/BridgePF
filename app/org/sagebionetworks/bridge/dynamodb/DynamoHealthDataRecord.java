package org.sagebionetworks.bridge.dynamodb;

import org.sagebionetworks.bridge.json.DateTimeJsonDeserializer;
import org.sagebionetworks.bridge.json.DateTimeJsonSerializer;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMarshalling;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@DynamoDBTable(tableName = "HealthDataRecord2")
public class DynamoHealthDataRecord implements HealthDataRecord, DynamoTable {

    private static final String RECORD_ID_FIELD = "recordId";
    private static final String START_DATE_FIELD = "startDate";
    private static final String END_DATE_FIELD = "endDate";
    private static final String VERSION_FIELD = "version";
    private static final String DATA_FIELD = "data";
    
    private String key;
    private String recordId;
    private long startDate;
    private long endDate;
    private JsonNode data;
    private Long version;
    
    public static final DynamoHealthDataRecord fromJson(JsonNode node) {
        DynamoHealthDataRecord record = new DynamoHealthDataRecord();
        record.setRecordId(JsonUtils.asText(node, RECORD_ID_FIELD));
        record.setStartDate(JsonUtils.asMillisSinceEpoch(node, START_DATE_FIELD));
        record.setEndDate(JsonUtils.asMillisSinceEpoch(node, END_DATE_FIELD));
        record.setVersion(JsonUtils.asLongPrimitive(node, VERSION_FIELD));
        record.setData(JsonUtils.asJsonNode(node, DATA_FIELD));
        return record;
    }

    public DynamoHealthDataRecord() {
    }
    
    public DynamoHealthDataRecord(String key) {
        this.key = key;
    }
    
    public DynamoHealthDataRecord(String key, HealthDataRecord record) {
        this.key = key;
        this.recordId = record.getRecordId();
        this.startDate = record.getStartDate();
        this.endDate = record.getEndDate();
        this.data = record.getData();
        this.version = record.getVersion();
    }
    
    public DynamoHealthDataRecord(String key, String recordId, HealthDataRecord record) {
        this.key = key;
        this.recordId = recordId;
        this.startDate = record.getStartDate();
        this.endDate = record.getEndDate();
        this.data = record.getData();
        this.version = record.getVersion();
    }
    
    @DynamoDBHashKey
    @JsonIgnore
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
    @DynamoDBIndexRangeKey(attributeName="endDate", localSecondaryIndexName="endDate-index")
    @JsonSerialize(using = DateTimeJsonSerializer.class)
    public long getEndDate() { 
        return endDate; 
    }
    @Override
    @JsonDeserialize(using = DateTimeJsonDeserializer.class)
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
        result = prime * result + (int) (endDate ^ (endDate >>> 32));
        result = prime * result + ((key == null) ? 0 : key.hashCode());
        result = prime * result + ((recordId == null) ? 0 : recordId.hashCode());
        result = prime * result + (int) (startDate ^ (startDate >>> 32));
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
        DynamoHealthDataRecord other = (DynamoHealthDataRecord) obj;
        if (data == null) {
            if (other.data != null)
                return false;
        }
        if (endDate != other.endDate)
            return false;
        if (key == null) {
            if (other.key != null)
                return false;
        } else if (!key.equals(other.key))
            return false;
        if (recordId == null) {
            if (other.recordId != null)
                return false;
        } else if (!recordId.equals(other.recordId))
            return false;
        if (startDate != other.startDate)
            return false;
        if (version == null) {
            if (other.version != null)
                return false;
        } else if (!version.equals(other.version))
            return false;
        return true;
    }

}
