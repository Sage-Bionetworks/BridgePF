package org.sagebionetworks.bridge.dynamodb;

import java.io.IOException;
import java.util.List;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMappingException;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;

/**
 * Generic list marshaller for DynamoDB. This converts the list into JSON for marshalling to DynamoDB. Because DynamoDB
 * annotations don't work with generics, you'll need to subclass this and fill in getTypeReference().
 */
public abstract class ListMarshaller<T> implements DynamoDBTypeConverter<String, List<T>> {
    /**
     * Returns the type reference for Jackson to deserialize the value from DynamoDB, because Java can't infer generic
     * types at runtime.
     */
    public abstract TypeReference<List<T>> getTypeReference();

    /** {@inheritDoc} */
    @Override
    public String convert(List<T> list) {
        try {
            return BridgeObjectMapper.get().writerWithDefaultPrettyPrinter().writeValueAsString(list);
        } catch (JsonProcessingException ex) {
            throw new DynamoDBMappingException(ex);
        }
    }

    /** {@inheritDoc} */
    @Override
    public List<T> unconvert(String json) {
        try {
            return BridgeObjectMapper.get().readValue(json, getTypeReference());
        } catch (IOException ex) {
            throw new DynamoDBMappingException(ex);
        }
    }
}
