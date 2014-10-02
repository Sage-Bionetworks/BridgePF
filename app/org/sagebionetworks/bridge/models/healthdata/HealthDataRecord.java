package org.sagebionetworks.bridge.models.healthdata;

import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.databind.JsonNode;

@BridgeTypeName("HealthData")
public interface HealthDataRecord extends BridgeEntity {

    public String getRecordId();
    public void setRecordId(String recordId);
    
    public Long getVersion();
    public void setVersion(Long version);
    
    public long getStartDate();
    public void setStartDate(long startDate);
    
    public long getEndDate();
    public void setEndDate(long endDate);
    
    public JsonNode getData();
    public void setData(JsonNode data);
    
}
