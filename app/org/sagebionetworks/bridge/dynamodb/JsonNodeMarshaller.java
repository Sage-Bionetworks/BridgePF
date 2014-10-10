package org.sagebionetworks.bridge.dynamodb;

import java.io.IOException;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMarshaller;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

public class JsonNodeMarshaller implements DynamoDBMarshaller<JsonNode> {

    @Override
    public String marshall(JsonNode node) {
        try {
            return BridgeObjectMapper.get().writeValueAsString(node);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return "";
    }

    @Override
    public JsonNode unmarshall(Class<JsonNode> node, String data) {
        try {
            return BridgeObjectMapper.get().readTree(data);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

}
