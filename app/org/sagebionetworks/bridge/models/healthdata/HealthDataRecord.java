package org.sagebionetworks.bridge.models.healthdata;

import com.fasterxml.jackson.databind.JsonNode;

public interface HealthDataRecord {

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
