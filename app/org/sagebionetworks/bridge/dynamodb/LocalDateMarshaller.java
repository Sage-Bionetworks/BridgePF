package org.sagebionetworks.bridge.dynamodb;

import org.joda.time.LocalDate;
import org.sagebionetworks.bridge.time.DateUtils;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverter;

/**
 * DynamoDB marshaller for JodaTime LocalDate (because the built-in ones don't work very well). This simply marshalls
 * to and from a simple YYYY-MM-DD format.
 * 
 * These converters are supposed to be "null-safe", see:
 * http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/dynamodbv2/datamodeling/DynamoDBTypeConverted.html
 */
public class LocalDateMarshaller implements DynamoDBTypeConverter<String,LocalDate> {
    /** {@inheritDoc} */
    @Override
    public String convert(LocalDate date) {
        return DateUtils.getCalendarDateString(date);
    }

    /** {@inheritDoc} */
    @Override
    public LocalDate unconvert(String data) {
        return DateUtils.parseCalendarDate(data);
    }
}
