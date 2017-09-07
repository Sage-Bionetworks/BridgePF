package org.sagebionetworks.bridge.models.reports;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;

import org.sagebionetworks.bridge.dynamodb.DynamoReportData;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@BridgeTypeName("ReportData")
@JsonDeserialize(as=DynamoReportData.class)
public interface ReportData extends BridgeEntity {

    static TypeReference<ForwardCursorPagedResourceList<ReportData>> PAGED_REPORT_DATA = new TypeReference<ForwardCursorPagedResourceList<ReportData>>() {
    };

    static ReportData create() {
        return new DynamoReportData();
    }
    
    ReportDataKey getReportDataKey();
    void setReportDataKey(ReportDataKey key);
    
    String getKey();
    void setKey(String key);
    
    /** Will be either a local date or date time string value. */
    String getDate();
    void setDate(String date);
    
    JsonNode getData();
    void setData(JsonNode data);
    
    /** Local date for reports that use local dates as the range portion of their key. Either localDate 
     * or dateTime must be provided, but never both. */ 
    LocalDate getLocalDate();
    void setLocalDate(LocalDate localDate);
    
    /** DateTime for reports that use specific dates and times as the range portion of their key. Either 
     * localDate or dateTime must be provided, but never both. */ 
    DateTime getDateTime();
    void setDateTime(DateTime dateTime);
}
