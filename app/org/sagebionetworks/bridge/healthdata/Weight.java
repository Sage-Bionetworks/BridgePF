package org.sagebionetworks.bridge.healthdata;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class Weight extends HealthDataEntryImpl {

    public Weight() {
        data = new ObjectMapper().createObjectNode();
    }
    public Weight(HealthDataEntry entry) {
        super(entry.getId(), entry.getStartDate(), entry.getData());
    }

    @DynamoDBAttribute
    public int getWeight() {
        JsonNode node = data.get("weight");
        return (node == null) ? 0 : node.asInt();
    }
    public void setWeight(int weight) {
        ((ObjectNode)data).put("weight", weight);
    }
    @Override
    public String toString() {
        return "Weight [getWeight()=" + getWeight() + ", getId()=" + getId() + ", getStartDate()=" + getStartDate()
                + ", getEndDate()=" + getEndDate() + "]";
    }

}
