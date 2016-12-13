package org.sagebionetworks.bridge.dynamodb;

import org.joda.time.LocalDateTime;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverter;

/**
 * These converters are supposed to be "null-safe", see:
 * http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/dynamodbv2/datamodeling/DynamoDBTypeConverted.html
 */
public class LocalDateTimeMarshaller implements DynamoDBTypeConverter<String,LocalDateTime> {

    @Override
    public String convert(LocalDateTime localDateTime) {
        return localDateTime.toString();
    }

    @Override
    public LocalDateTime unconvert(String localDateTimeString) {
        return LocalDateTime.parse(localDateTimeString);
    }
}

