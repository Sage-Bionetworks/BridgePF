package org.sagebionetworks.bridge.dynamodb;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;

import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.reports.ReportData;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;

@DynamoDBTable(tableName = "ReportData")
public class DynamoReportData implements ReportData {

    private String key;
    private String date;
    private JsonNode data;
    
    @JsonIgnore
    @DynamoDBHashKey
    @Override
    public String getKey() {
        return key;
    }
    @Override
    public void setKey(String key) {
        this.key = key;
    }
    @DynamoDBRangeKey
    @Override
    public String getDate() {
        return date;
    }
    @Override
    public void setDate(String date) {
        this.date = date;
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
        return (date.contains("T")) ? null : DateUtils.parseCalendarDate(date);
    }
    @Override
    public void setLocalDate(LocalDate localDate) {
        this.date = localDate.toString();
    }
    @DynamoDBIgnore
    @Override
    public DateTime getDateTime() {
        return (date.contains("T")) ? DateUtils.parseISODateTime(date) : null;
    }
    @Override
    public void setDateTime(DateTime dateTime) {
        this.date = dateTime.toString();
    }
}
