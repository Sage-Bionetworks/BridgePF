package org.sagebionetworks.bridge.dynamodb;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;

import org.sagebionetworks.bridge.json.DateTimeSerializer;
import org.sagebionetworks.bridge.models.reports.ReportData;
import org.sagebionetworks.bridge.models.reports.ReportDataKey;

import java.util.Set;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@DynamoDBTable(tableName = "ReportData")
public class DynamoReportData implements ReportData {

    private String key;
    private LocalDate localDate;
    // These values are held to make it easier to validate the report object
    private ReportDataKey reportDataKey;
    private Set<String> substudyIds;
    private DateTime dateTime;
    private JsonNode data;
    
    @JsonIgnore
    @DynamoDBIgnore
    public ReportDataKey getReportDataKey() {
        return reportDataKey;
    }
    public void setReportDataKey(ReportDataKey reportDataKey) {
        this.reportDataKey = reportDataKey;
    }
    @JsonIgnore
    @DynamoDBHashKey
    @Override
    public String getKey() {
        // Transfer to the property that is persisted when it is requested.
        if (reportDataKey != null) {
            key = reportDataKey.getKeyString();
        }
        return key;
    }
    @Override
    public void setKey(String key) {
        this.key = key;
    }
    
    @Override
    @DynamoDBIgnore
    public Set<String> getSubstudyIds() {
        return this.substudyIds;
    }
    
    @Override
    public void setSubstudyIds(Set<String> substudyIds) {
        this.substudyIds = substudyIds;
    }
    
    @DynamoDBRangeKey
    @Override
    public String getDate() {
        if (localDate != null) {
            return localDate.toString();
        } else if (dateTime != null) {
            return dateTime.toString();
        }
        return null;
    }
    @Override
    public void setDate(String date) {
        if (date != null) {
            if (date.contains("T")) {
                dateTime = DateTime.parse(date);
            } else {
                localDate = LocalDate.parse(date);
            }
        }
    }
    @DynamoDBTypeConverted(converter = JsonNodeMarshaller.class)
    @Override
    public JsonNode getData() {
        return data;
    }
    @Override
    public void setData(JsonNode data) {
        this.data = data;
    }
    @DynamoDBIgnore
    @Override
    public LocalDate getLocalDate() {
        return localDate;
    }
    @Override
    public void setLocalDate(LocalDate localDate) {
        this.localDate = localDate;
    }
    @DynamoDBIgnore
    @JsonSerialize(using = DateTimeSerializer.class)
    @Override
    public DateTime getDateTime() {
        return dateTime;
    }
    @Override
    public void setDateTime(DateTime dateTime) {
        this.dateTime = dateTime;
    }
}
