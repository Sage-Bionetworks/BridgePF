package org.sagebionetworks.bridge.dynamodb;

import org.joda.time.LocalDate;
import org.sagebionetworks.bridge.json.DateUtils;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMarshaller;

/**
 * DynamoDB marshaller for JodaTime LocalDate (because the built-in ones don't work very well). This simply marshalls
 * to and from a simple YYYY-MM-DD format.
 */
public class LocalDateMarshaller implements DynamoDBMarshaller<LocalDate> {
    /** {@inheritDoc} */
    @Override
    public String marshall(LocalDate date) {
        return DateUtils.getCalendarDateString(date);
    }

    /** {@inheritDoc} */
    @Override
    public LocalDate unmarshall(Class<LocalDate> clazz, String data) {
        return DateUtils.parseCalendarDate(data);
    }
}
