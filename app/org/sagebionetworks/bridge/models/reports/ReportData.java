package org.sagebionetworks.bridge.models.reports;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;

import org.sagebionetworks.bridge.dynamodb.DynamoReportData;
import org.sagebionetworks.bridge.json.BridgeTypeName;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@BridgeTypeName("ReportData")
@JsonDeserialize(as=DynamoReportData.class)
public interface ReportData {

    static ReportData create() {
        return new DynamoReportData();
    }
    
    String getKey();
    void setKey(String key);
    
    /** Will be either a local date or date time string value. */
    String getDate();
    void setDate(String date);
    
    JsonNode getData();
    void setData(JsonNode data);
    
    /** Local date for reports that use local dates as the range portion of their key. */ 
    LocalDate getLocalDate();
    void setLocalDate(LocalDate localDate);
    
    /** DateTime for reports that use local dates as the range portion of their key. */ 
    DateTime getDateTime();
    void setDateTime(DateTime dateTime);
}
