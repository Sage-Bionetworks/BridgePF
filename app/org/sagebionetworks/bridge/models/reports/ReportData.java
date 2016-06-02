package org.sagebionetworks.bridge.models.reports;

import org.joda.time.LocalDate;

import org.sagebionetworks.bridge.dynamodb.DynamoReportData;
import org.sagebionetworks.bridge.json.BridgeTypeName;

import com.fasterxml.jackson.databind.JsonNode;

@BridgeTypeName("ReportData")
public interface ReportData {

    public static ReportData create() {
        return new DynamoReportData();
    }
    
    String getKey();
    void setKey(String key);
    
    LocalDate getDate();
    void setDate(LocalDate date);
    
    JsonNode getData();
    void setData(JsonNode data);
    
}
