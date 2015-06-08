package org.sagebionetworks.bridge.dynamodb;

import org.sagebionetworks.bridge.models.studies.PasswordPolicy;

import com.amazonaws.services.dynamodbv2.datamodeling.JsonMarshaller;

/**
 * Marshalls the PasswordPolicy class to JSON when persisting using DynamoDB.
 */
public class PasswordPolicyMarshaller extends JsonMarshaller<PasswordPolicy> {

}
