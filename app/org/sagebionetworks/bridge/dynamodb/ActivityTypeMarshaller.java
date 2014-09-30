package org.sagebionetworks.bridge.dynamodb;

import org.sagebionetworks.bridge.models.schedules.ActivityType;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMarshaller;

public class ActivityTypeMarshaller implements DynamoDBMarshaller<ActivityType> {

    @Override
    public String marshall(ActivityType type) {
        return type.name();
    }

    @Override
    public ActivityType unmarshall(Class<ActivityType> clazz, String value) {
        return ActivityType.valueOf(value);
    }

}
