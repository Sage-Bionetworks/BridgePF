package org.sagebionetworks.bridge.models.reports;

import org.joda.time.LocalDate;

import org.sagebionetworks.bridge.dynamodb.DynamoReportData;
import org.sagebionetworks.bridge.json.BridgeTypeName;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@BridgeTypeName("ReportData")
@JsonDeserialize(as=DynamoReportData.class)
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
