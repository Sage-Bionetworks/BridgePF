package org.sagebionetworks.bridge.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMarshaller;
import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;

/**
 * DynamoDB marshaller for Joda DateTime (because the built-in ones don't work very well). This simply marshalls
 * to and from the yyyy-MM-dd'T'HH:mm:ss.SSSZZ format.
 */
public class DateTimeMarshaller implements DynamoDBMarshaller<DateTime> {
    /** {@inheritDoc} */
    @Override
    public String marshall(DateTime dateTime) {
        return dateTime.toString(ISODateTimeFormat.dateTime());
    }

    /** {@inheritDoc} */
    @Override
    public DateTime unmarshall(Class<DateTime> clazz, String data) {
        return DateTime.parse(data, ISODateTimeFormat.dateTime());
    }
}
