package org.sagebionetworks.bridge.dynamodb;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.config.Environment;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.TableNameOverride;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;

final class TableNameOverrideFactory {

    static TableNameOverride getTableNameOverride(final Class<?> dynamoTable) {
        final DynamoDBTable table = dynamoTable.getAnnotation(DynamoDBTable.class);
        if (table == null) {
            throw new IllegalArgumentException("Missing DynamoDBTable annotation for " + dynamoTable.getName());
        }
        final BridgeConfig config = BridgeConfigFactory.getConfig();
        final Environment env = config.getEnvironment();
        return new TableNameOverride(env.name().toLowerCase() + "-" + config.getUser() + "-" + table.tableName());
    }

    static String getTableName(final Class<?> dynamoTable) {
        return getTableNameOverride(dynamoTable).getTableName();
    }
}
