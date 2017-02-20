package org.sagebionetworks.bridge.dynamodb;

import java.io.IOException;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMappingException;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * These converters are supposed to be "null-safe", see:
 * http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/dynamodbv2/datamodeling/DynamoDBTypeConverted.html
 */
public class JsonNodeMarshaller implements DynamoDBTypeConverter<String,JsonNode> {

    @Override
    public String convert(JsonNode node) {
        try {
            return BridgeObjectMapper.get().writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new DynamoDBMappingException(e);
        }
    }

    @Override
    public JsonNode unconvert(String data) {
        try {
            return BridgeObjectMapper.get().readTree(data);
        } catch (IOException e) {
            throw new DynamoDBMappingException(e);
        }
    }

}
