package org.sagebionetworks.bridge.dynamodb;

import java.io.IOException;
import java.util.Map;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMappingException;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;

public abstract class StringKeyMapMarshaller<T> implements DynamoDBTypeConverter<String, Map<String,T>> {

    public abstract TypeReference<Map<String,T>> getTypeReference();

    /** {@inheritDoc} */
    @Override
    public String convert(Map<String,T> map) {
        try {
            return BridgeObjectMapper.get().writeValueAsString(map);
        } catch (JsonProcessingException ex) {
            throw new DynamoDBMappingException(ex);
        }
    }

    /** {@inheritDoc} */
    @Override
    public Map<String,T> unconvert(String json) {
        try {
            return BridgeObjectMapper.get().readValue(json, getTypeReference());
        } catch (IOException ex) {
            throw new DynamoDBMappingException(ex);
        }
    }
}