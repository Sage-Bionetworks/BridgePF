package org.sagebionetworks.bridge.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;

/**
 * Because I cannot figure out how to do this directly in the Spring application context XML. 
 *
 */
public class DynamoDBMapperConfigFactory {

    public static DynamoDBMapperConfig getCreateMapper(Class<DynamoTable> clazz) {
        return new DynamoDBMapperConfig(
                DynamoDBMapperConfig.SaveBehavior.CLOBBER,
                DynamoDBMapperConfig.DEFAULT.getConsistentReads(),
                TableNameOverrideFactory.getTableNameOverride(clazz));
    }

    public static DynamoDBMapperConfig getUpdateMapper(Class<DynamoTable> clazz) {
        return new DynamoDBMapperConfig(
                DynamoDBMapperConfig.SaveBehavior.UPDATE,
                DynamoDBMapperConfig.DEFAULT.getConsistentReads(),
                TableNameOverrideFactory.getTableNameOverride(clazz));
    }
}
