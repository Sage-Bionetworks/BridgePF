package org.sagebionetworks.bridge.healthdata;

import com.fasterxml.jackson.databind.JsonNode;

public interface HealthDataEntry {

    public String getId();
    public void setId(String id);
    
    public long getStartDate();
    public void setStartDate(long startDate);
    
    public long getEndDate();
    public void setEndDate(long endDate);
    
    public JsonNode getData();
    public void setData(JsonNode data);
    
}
