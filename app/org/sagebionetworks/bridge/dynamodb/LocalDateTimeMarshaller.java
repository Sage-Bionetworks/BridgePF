package org.sagebionetworks.bridge.dynamodb;

import org.joda.time.LocalDateTime;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMarshaller;

public class LocalDateTimeMarshaller implements DynamoDBMarshaller<LocalDateTime> {
    @Override
    public String marshall(LocalDateTime localDateTime) {
        return localDateTime.toString();
    }

    @Override
    public LocalDateTime unmarshall(Class<LocalDateTime> cls, String localDateTimeString) {
        return LocalDateTime.parse(localDateTimeString);
    }
}

