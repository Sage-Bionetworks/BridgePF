package org.sagebionetworks.bridge.dynamodb;

import java.util.Set;

import com.amazonaws.services.dynamodbv2.datamodeling.JsonMarshaller;

/**
 * You wouldn't think you'd need this, but DynamoDB does not like empty string sets, 
 * while this will work when no such value is set.
 */
public class StringSetMarshaller extends JsonMarshaller<Set<String>> {

}
