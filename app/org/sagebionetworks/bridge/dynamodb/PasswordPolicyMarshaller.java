package org.sagebionetworks.bridge.dynamodb;

import org.sagebionetworks.bridge.models.studies.PasswordPolicy;

import com.amazonaws.services.dynamodbv2.datamodeling.JsonMarshaller;

/**
 * Marshals the PasswordPolicy class to JSON when persisting using DynamoDB. DynamoMarshaller 
 * annotation doesn't allow generic types, so we need to make this class to JsonMarshaller.
 */
public class PasswordPolicyMarshaller extends JsonMarshaller<PasswordPolicy> {

}
