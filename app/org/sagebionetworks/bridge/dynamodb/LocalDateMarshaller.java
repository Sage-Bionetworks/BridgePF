package org.sagebionetworks.bridge.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMarshaller;
import org.joda.time.LocalDate;
import org.joda.time.format.ISODateTimeFormat;

/**
 * DynamoDB marshaller for JodaTime LocalDate (because the built-in ones don't work very well). This simply marshalls
 * to and from a simple YYYY-MM-DD format.
 */
public class LocalDateMarshaller implements DynamoDBMarshaller<LocalDate> {
    /** {@inheritDoc} */
    @Override
    public String marshall(LocalDate date) {
        return date.toString(ISODateTimeFormat.date());
    }

    /** {@inheritDoc} */
    @Override
    public LocalDate unmarshall(Class<LocalDate> clazz, String data) {
        return LocalDate.parse(data, ISODateTimeFormat.date());
    }
}
