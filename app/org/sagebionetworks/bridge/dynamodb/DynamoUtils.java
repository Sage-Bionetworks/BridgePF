package org.sagebionetworks.bridge.dynamodb;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.config.Environment;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.TableNameOverride;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Index;
import com.amazonaws.services.dynamodbv2.document.Table;

public final class DynamoUtils {

    static TableNameOverride getTableNameOverride(final Class<?> dynamoTable) {
        final DynamoDBTable table = dynamoTable.getAnnotation(DynamoDBTable.class);
        if (table == null) {
            throw new IllegalArgumentException("Missing DynamoDBTable annotation for " + dynamoTable.getName());
        }
        final BridgeConfig config = BridgeConfigFactory.getConfig();
        final Environment env = config.getEnvironment();
        return new TableNameOverride(env.name().toLowerCase() + "-" + config.getUser() + "-" + table.tableName());
    }

    public static String getTableName(final Class<?> dynamoTable) {
        return getTableNameOverride(dynamoTable).getTableName();
    }

    /**
     * Gets the mapper with UPDATE behavior for saves and CONSISTENT reads.
     */
    public static DynamoDBMapper getMapper(final Class<?> dynamoTable, final AmazonDynamoDB dynamoClient) {
        final DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig.Builder()
                .withSaveBehavior(DynamoDBMapperConfig.SaveBehavior.UPDATE)
                .withConsistentReads(DynamoDBMapperConfig.ConsistentReads.CONSISTENT)
                .withTableNameOverride(getTableNameOverride(dynamoTable)).build();
        return new DynamoDBMapper(dynamoClient, mapperConfig);
    }

    /**
     * Gets the mapper with UPDATE behavior for saves and EVENTUALLY consistent reads.
     */
    public static DynamoDBMapper getMapperEventually(final Class<?> dynamoTable, final AmazonDynamoDB dynamoClient) {
        final DynamoDBMapperConfig mapperConfig = new DynamoDBMapperConfig.Builder()
                .withSaveBehavior(DynamoDBMapperConfig.SaveBehavior.UPDATE)
                .withConsistentReads(DynamoDBMapperConfig.ConsistentReads.EVENTUAL)
                .withTableNameOverride(getTableNameOverride(dynamoTable)).build();
        return new DynamoDBMapper(dynamoClient, mapperConfig);
    }

    public static DynamoIndexHelper getDynamoIndexHelper(final Class<?> dynamoTable,
            final String indexName, final AmazonDynamoDB client) {
        final DynamoDB ddb = new DynamoDB(client);
        final Table ddbTable = ddb.getTable(getTableName(dynamoTable));
        final Index ddbIndex = ddbTable.getIndex(indexName);
        final DynamoIndexHelper indexHelper = new DynamoIndexHelper();
        indexHelper.setIndex(ddbIndex);
        indexHelper.setMapper(getMapper(dynamoTable, client));
        return indexHelper;
    }
}
