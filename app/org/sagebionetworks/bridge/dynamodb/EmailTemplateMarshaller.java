package org.sagebionetworks.bridge.dynamodb;

import org.sagebionetworks.bridge.models.studies.EmailTemplate;

import com.amazonaws.services.dynamodbv2.datamodeling.JsonMarshaller;

/**
 * Marshalls the EmailTemplate class to JSON when persisting using DynamoDB.
 */
public class EmailTemplateMarshaller extends JsonMarshaller<EmailTemplate> {
}
