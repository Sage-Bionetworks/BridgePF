package org.sagebionetworks.bridge.dynamodb;

import java.io.IOException;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMarshaller;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonNodeMarshaller implements DynamoDBMarshaller<JsonNode> {

    @Override
    public String marshall(JsonNode node) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return "";
    }

    @Override
    public JsonNode unmarshall(Class<JsonNode> node, String data) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readTree(data);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

}
