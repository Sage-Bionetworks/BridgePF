package org.sagebionetworks.bridge.dynamodb;

import org.joda.time.LocalDate;

import org.sagebionetworks.bridge.json.LocalDateToStringSerializer;
import org.sagebionetworks.bridge.models.reports.ReportData;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMarshalling;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.joda.deser.LocalDateDeserializer;

@DynamoDBTable(tableName = "ReportData")
public class DynamoReportData implements ReportData {

    private String key;
    private LocalDate date;
    private JsonNode data;
    
    @DynamoDBHashKey
    @Override
    public String getKey() {
        return key;
    }
    @Override
    public void setKey(String key) {
        this.key = key;
    }
    @DynamoDBMarshalling(marshallerClass = LocalDateMarshaller.class)
    @DynamoDBRangeKey
    @JsonSerialize(using = LocalDateToStringSerializer.class)
    @Override
    public LocalDate getDate() {
        return date;
    }
    @JsonDeserialize(using = LocalDateDeserializer.class)
    @Override
    public void setDate(LocalDate date) {
        this.date = date;
    }
    @DynamoDBMarshalling(marshallerClass = JsonNodeMarshaller.class)
    @Override
    public JsonNode getData() {
        return data;
    }
    @Override
    public void setData(JsonNode data) {
        this.data = data;
    }
}
