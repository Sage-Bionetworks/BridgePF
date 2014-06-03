package org.sagebionetworks.bridge.dynamodb;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.config.Environment;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.TableNameOverride;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;

/**
 * Because I cannot figure out how to do this directly in the Spring application context XML. 
 *
 */
public class DynamoDBMapperConfigFactory {

    public static DynamoDBMapperConfig getCreateMapper(Class<DynamoDbRecord> clazz) {
        return new DynamoDBMapperConfig(
                DynamoDBMapperConfig.SaveBehavior.CLOBBER,
                DynamoDBMapperConfig.DEFAULT.getConsistentReads(),
                getTableNameOverride(clazz));
    }

    public static DynamoDBMapperConfig getUpdateMapper(Class<DynamoDbRecord> clazz) {
        return new DynamoDBMapperConfig(
                DynamoDBMapperConfig.SaveBehavior.UPDATE,
                DynamoDBMapperConfig.DEFAULT.getConsistentReads(),
                getTableNameOverride(clazz));
    }

    private static TableNameOverride getTableNameOverride(Class<DynamoDbRecord> clazz) {
        BridgeConfig config = BridgeConfigFactory.getConfig();
        Environment env = config.getEnvironment();
        String tableName = clazz.getAnnotation(DynamoDBTable.class).tableName();
        if (tableName == null || tableName.isEmpty()) {
            throw new IllegalArgumentException("Missing DynamoDBTable table name for " + clazz.getName());
        }
        return new TableNameOverride(env.getEnvName() + "-" + config.getUser() + "-" + tableName);
    }
}
