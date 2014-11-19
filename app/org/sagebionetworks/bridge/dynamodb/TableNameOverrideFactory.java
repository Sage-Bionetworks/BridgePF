package org.sagebionetworks.bridge.dynamodb;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.config.Environment;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.TableNameOverride;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;

public class TableNameOverrideFactory {

    public static TableNameOverride getTableNameOverride(Class<? extends DynamoTable> clazz) {
        BridgeConfig config = BridgeConfigFactory.getConfig();
        Environment env = config.getEnvironment();
        String tableName = clazz.getAnnotation(DynamoDBTable.class).tableName();
        if (tableName == null || tableName.isEmpty()) {
            throw new IllegalArgumentException("Missing DynamoDBTable table name for " + clazz.getName());
        }
        return new TableNameOverride(env.name().toLowerCase() + "-" + config.getUser() + "-" + tableName);
    }
}
