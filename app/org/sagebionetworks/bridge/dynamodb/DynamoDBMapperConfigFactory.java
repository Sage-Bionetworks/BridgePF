package org.sagebionetworks.bridge.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;

/**
 * Because I cannot figure out how to do this directly in the Spring application context XML. 
 *
 */
public class DynamoDBMapperConfigFactory {

    public static DynamoDBMapperConfig getCreateMapper() {
        return new DynamoDBMapperConfig(DynamoDBMapperConfig.SaveBehavior.CLOBBER);
    }

    public static DynamoDBMapperConfig getUpdateMapper() {
        return new DynamoDBMapperConfig(DynamoDBMapperConfig.SaveBehavior.UPDATE);
    }
}
