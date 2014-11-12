package org.sagebionetworks.bridge.models.healthdata;

import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.databind.JsonNode;

@BridgeTypeName("HealthData")
public interface HealthDataRecord extends BridgeEntity {

    public String getGuid();
    public void setGuid(String guid);
    
    public Long getVersion();
    public void setVersion(Long version);
    
    public long getStartDate();
    public void setStartDate(long startDate);
    
    public long getEndDate();
    public void setEndDate(long endDate);
    
    public JsonNode getData();
    public void setData(JsonNode data);
    
}
