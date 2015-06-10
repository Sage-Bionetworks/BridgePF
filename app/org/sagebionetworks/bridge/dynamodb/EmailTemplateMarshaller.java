package org.sagebionetworks.bridge.dynamodb;

import org.sagebionetworks.bridge.models.studies.EmailTemplate;

import com.amazonaws.services.dynamodbv2.datamodeling.JsonMarshaller;

/**
 * Marshals the EmailTemplate class to JSON when persisting using DynamoDB. DynamoMarshaller 
 * annotation doesn't allow generic types, so we need to make this class to JsonMarshaller.
 */
public class EmailTemplateMarshaller extends JsonMarshaller<EmailTemplate> {
}
