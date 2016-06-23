package org.sagebionetworks.bridge.dynamodb;

import java.util.Collection;
import java.util.Map;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.DescribeTableResult;
import com.amazonaws.services.dynamodbv2.model.ResourceNotFoundException;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
import com.amazonaws.services.dynamodbv2.model.TableStatus;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.config.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DynamoInitializer {
    private static Logger LOG = LoggerFactory.getLogger(DynamoInitializer.class);

    private final BridgeConfig bridgeConfig;
    private final AmazonDynamoDB dynamoDBClient;
    private final DynamoUtils dynamoUtils;
    private final DynamoNamingHelper dynamoNamingHelper;

    @Autowired
    public DynamoInitializer(BridgeConfig bridgeConfig,
                             AmazonDynamoDBClient dynamoDBClient,
                             DynamoUtils dynamoUtils, DynamoNamingHelper dynamoNamingHelper) {
        this.bridgeConfig = bridgeConfig;
        this.dynamoDBClient = dynamoDBClient;
        this.dynamoUtils = dynamoUtils;
        this.dynamoNamingHelper = dynamoNamingHelper;
    }

    /**
     * Creates DynamoDB tables, if they do not exist yet. Throws an error if the table exists but the schema (hash key, range key,
     * and secondary indices) does not match.
     */
    public void init(Collection<TableDescription> tables) {
        initTables(tables);
    }

    void deleteTable(Class<?> table) {
        final String tableName = dynamoNamingHelper.getFullyQualifiedTableName(table);
        try {
            DescribeTableResult tableResult = dynamoDBClient.describeTable(tableName);
            TableDescription tableDscr = tableResult.getTable();
            String status = tableDscr.getTableStatus();
            if (TableStatus.DELETING.toString().equalsIgnoreCase(status)) {
                return;
            } else if (!TableStatus.ACTIVE.toString().equalsIgnoreCase(status)) {
                // Must be active to be deleted
                dynamoUtils.waitForActive(tableDscr.getTableName());
            }
            LOG.info("Deleting table " + tableName);
            dynamoDBClient.deleteTable(tableName);
            dynamoUtils.waitForDelete(tableDscr.getTableName());
            LOG.info("Table " + tableName + " deleted.");
        } catch (ResourceNotFoundException e) {
            LOG.warn("Table " + tableName + " does not exist.");
        }
    }

    private void initTables(final Collection<TableDescription> tables) {
        Map<String, TableDescription> existingTables = dynamoUtils.getExistingTables();
        Environment env = bridgeConfig.getEnvironment();
        if (Environment.UAT.equals(env) || Environment.PROD.equals(env)) {
            StringBuilder builder = new StringBuilder("[");
            for (Map.Entry<String, TableDescription> entry : existingTables.entrySet()) {
                builder.append("(");
                builder.append(entry.getKey());
                builder.append(", ");
                builder.append(entry.getValue().getTableName());
                builder.append(", ");
                builder.append(entry.getValue().getTableStatus());
                builder.append("), ");
            }
            builder.append("]");
            LOG.info("Existing tables: " + builder.toString());
        }
        for (TableDescription table : tables) {
            if (!existingTables.containsKey(table.getTableName())) {
                CreateTableRequest createTableRequest = dynamoUtils.getCreateTableRequest(table);
                LOG.info("Creating table " + table.getTableName());
                dynamoDBClient.createTable(createTableRequest);
            } else {
                final TableDescription existingTable = existingTables.get(table.getTableName());
                dynamoUtils.compareSchema(table, existingTable);
            }
            dynamoUtils.waitForActive(table.getTableName());
        }
        LOG.info("DynamoDB tables are ready.");
    }
}
