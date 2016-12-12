package org.sagebionetworks.bridge.dynamodb;

import org.joda.time.LocalDateTime;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverter;

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

