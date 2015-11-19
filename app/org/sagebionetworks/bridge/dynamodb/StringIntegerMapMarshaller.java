package org.sagebionetworks.bridge.dynamodb;

import java.util.Map;

import com.amazonaws.services.dynamodbv2.datamodeling.JsonMarshaller;

/**
 * Marshalls Map<String, Integer> values JSON when persisting using DynamoDB.
 */
public class StringIntegerMapMarshaller extends JsonMarshaller<Map<String,Integer>> {

}
