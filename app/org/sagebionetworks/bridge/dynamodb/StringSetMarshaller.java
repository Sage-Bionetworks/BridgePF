package org.sagebionetworks.bridge.dynamodb;

import java.util.Set;

import com.amazonaws.services.dynamodbv2.datamodeling.JsonMarshaller;

/**
 * Marshalls Set<String> values JSON when persisting using DynamoDB. Without this annotation, 
 * DynamoDB throws errors on empty string set fields.
 */
public class StringSetMarshaller extends JsonMarshaller<Set<String>> {

}
