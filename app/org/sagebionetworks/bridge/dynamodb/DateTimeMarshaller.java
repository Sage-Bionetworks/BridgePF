package org.sagebionetworks.bridge.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMarshaller;
import org.joda.time.DateTime;

import org.sagebionetworks.bridge.json.DateUtils;

/**
 * DynamoDB marshaller for Joda DateTime (because the built-in ones don't work very well). This simply marshalls
 * to and from the yyyy-MM-dd'T'HH:mm:ss.SSSZZ format.
 */
public class DateTimeMarshaller implements DynamoDBMarshaller<DateTime> {
    /** {@inheritDoc} */
    @Override
    public String marshall(DateTime dateTime) {
        return DateUtils.getISODateTime(dateTime);
    }

    /** {@inheritDoc} */
    @Override
    public DateTime unmarshall(Class<DateTime> clazz, String data) {
        return DateUtils.parseISODateTime(data);
    }
}
