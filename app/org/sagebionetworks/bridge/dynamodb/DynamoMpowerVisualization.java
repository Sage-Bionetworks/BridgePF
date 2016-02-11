package org.sagebionetworks.bridge.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMarshalling;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.fasterxml.jackson.databind.JsonNode;
import org.joda.time.LocalDate;

/**
 * DDB implementation of mPower visualization. Individual data points are stored as raw JSON in the "json" field for
 * rapid development. When we generalize this, we can restructure this class as needed.
 */
// Provisioned throughput set to 1/1, as it seems similar to schedules.
@DynamoThroughput(readCapacity=1, writeCapacity=1)
@DynamoDBTable(tableName = "MpowerVisualization")
public class DynamoMpowerVisualization {
    private LocalDate date;
    private String healthCode;
    private JsonNode visualization;

    /** Date for this data point. */
    @DynamoDBMarshalling(marshallerClass = LocalDateMarshaller.class)
    @DynamoDBRangeKey
    public LocalDate getDate() {
        return date;
    }

    /** @see #getDate */
    public void setDate(LocalDate date) {
        this.date = date;
    }

    /** Health code of user for this data point. */
    @DynamoDBHashKey
    public String getHealthCode() {
        return healthCode;
    }

    /** @see #getHealthCode */
    public void setHealthCode(String healthCode) {
        this.healthCode = healthCode;
    }

    /** Raw JSON data of this data point. */
    @DynamoDBMarshalling(marshallerClass = JsonNodeMarshaller.class)
    public JsonNode getVisualization() {
        return visualization;
    }

    /** @see #getVisualization */
    public void setVisualization(JsonNode visualization) {
        this.visualization = visualization;
    }
}
