package org.sagebionetworks.bridge.dynamodb;

import org.sagebionetworks.bridge.models.schedules.ScheduleType;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMarshaller;

public class ScheduleTypeMarshaller implements DynamoDBMarshaller<ScheduleType> {

    @Override
    public String marshall(ScheduleType type) {
        return type.name();
    }

    @Override
    public ScheduleType unmarshall(Class<ScheduleType> clazz, String value) {
        return ScheduleType.valueOf(value);
    }

}
