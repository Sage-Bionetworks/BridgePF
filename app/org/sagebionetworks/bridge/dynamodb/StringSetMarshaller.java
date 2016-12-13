package org.sagebionetworks.bridge.dynamodb;

import java.io.IOException;
import java.util.Set;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMappingException;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Marshalls Set<String> values JSON when persisting using DynamoDB. Without this annotation, 
 * DynamoDB throws errors on empty string set fields.
 * 
 * These converters are supposed to be "null-safe", see:
 * http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/dynamodbv2/datamodeling/DynamoDBTypeConverted.html
 */
public class StringSetMarshaller implements DynamoDBTypeConverter<String,Set<String>> {

    private static final TypeReference<Set<String>> SET_REF = new TypeReference<Set<String>>() {};
    
    @Override
    public String convert(Set<String> set) {
        try {
            return new ObjectMapper().writeValueAsString(set);
        } catch (JsonProcessingException e) {
            throw new DynamoDBMappingException(e);
        }
    }

    @Override
    public Set<String> unconvert(String string) {
        try {
            return new ObjectMapper().readValue(string, SET_REF);
        } catch (IOException e) {
            throw new DynamoDBMappingException(e);
        }
    }

}
